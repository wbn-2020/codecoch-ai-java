#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

usage() {
  cat <<'EOF'
Usage: scripts/rehearse-migrations.sh [--repo PATH] [--evidence-dir PATH] [--validate-only]

Runs the CodeCoachAI baseline plus Flyway migrations in isolated disposable
containers. No host ports or existing Compose resources are used.

--validate-only checks arguments, required commands, and repository inputs
without contacting the Docker daemon or creating files and Docker resources.
EOF
}

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
EVIDENCE_ROOT=""
VALIDATE_ONLY=0
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-17}"
MIN_FREE_KB="${MIGRATION_REHEARSAL_MIN_FREE_KB:-2097152}"
PRELOADED_IMAGES="${MIGRATION_REHEARSAL_PRELOADED_IMAGES:-0}"
OWNER_LABEL="com.codecoachai.migration-rehearsal.owner"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      [[ $# -ge 2 && -n "$2" ]] || {
        echo "--repo requires a non-empty path" >&2
        exit 2
      }
      REPO="$(cd "$2" && pwd -P)"
      shift 2
      ;;
    --evidence-dir)
      [[ $# -ge 2 && -n "$2" ]] || {
        echo "--evidence-dir requires a non-empty path" >&2
        exit 2
      }
      EVIDENCE_ROOT="$2"
      shift 2
      ;;
    --validate-only)
      VALIDATE_ONLY=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

for command in docker openssl awk chmod df grep mkdir mktemp mv rm seq sleep tee; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command is unavailable: $command" >&2
    exit 1
  }
done

for required in \
  "$REPO/pom.xml" \
  "$REPO/sql/init.sql" \
  "$REPO/sql/migration" \
  "$REPO/scripts/verify-migration-schema.sql"; do
  [[ -e "$required" ]] || {
    echo "Required migration input is missing: $required" >&2
    exit 1
  }
done

[[ "$MIN_FREE_KB" =~ ^[0-9]+$ ]] || {
  echo "MIGRATION_REHEARSAL_MIN_FREE_KB must be a non-negative integer" >&2
  exit 1
}
[[ "$PRELOADED_IMAGES" == "0" || "$PRELOADED_IMAGES" == "1" ]] || {
  echo "MIGRATION_REHEARSAL_PRELOADED_IMAGES must be 0 or 1" >&2
  exit 1
}

if [[ "$VALIDATE_ONLY" -eq 1 ]]; then
  printf 'Migration rehearsal validation passed for repository: %s\n' "$REPO"
  exit 0
fi

OWNER_TOKEN="$(openssl rand -hex 16)"
RUN_ID="ccai-migration-${OWNER_TOKEN:0:16}"
NETWORK="${RUN_ID}-net"
DB_CONTAINER="${RUN_ID}-mysql"
FLYWAY_CONTAINER="${RUN_ID}-flyway"
DB_VOLUME="${RUN_ID}-data"
M2_VOLUME="${RUN_ID}-m2"
EVIDENCE_PARENT="${EVIDENCE_ROOT:-${TMPDIR:-/tmp}}"
EVIDENCE="${EVIDENCE_PARENT%/}/${RUN_ID}"
SECRET_PARENT="${TMPDIR:-/tmp}"
SECRET_DIR=""
SECRET=""

resource_owned() {
  local resource_type="$1"
  local resource_name="$2"
  local actual_owner=""

  case "$resource_type" in
    container)
      actual_owner="$(docker inspect --format "{{ index .Config.Labels \"$OWNER_LABEL\" }}" "$resource_name" 2>/dev/null)" || return 1
      ;;
    network)
      actual_owner="$(docker network inspect --format "{{ index .Labels \"$OWNER_LABEL\" }}" "$resource_name" 2>/dev/null)" || return 1
      ;;
    volume)
      actual_owner="$(docker volume inspect --format "{{ index .Labels \"$OWNER_LABEL\" }}" "$resource_name" 2>/dev/null)" || return 1
      ;;
    *)
      return 1
      ;;
  esac

  [[ "$actual_owner" == "$OWNER_TOKEN" ]]
}

record_cleanup_warning() {
  printf '%s\n' "$1" >> "$EVIDENCE/cleanup-warnings.log"
}

remove_owned_container() {
  local name="$1"
  if ! docker inspect "$name" >/dev/null 2>&1; then
    return 0
  fi
  if ! resource_owned container "$name"; then
    record_cleanup_warning "Skipped container cleanup after ownership check failed: $name"
    return 0
  fi
  docker rm -f "$name" >/dev/null 2>&1 || \
    record_cleanup_warning "Failed to remove owned container: $name"
}

remove_owned_network() {
  local name="$1"
  if ! docker network inspect "$name" >/dev/null 2>&1; then
    return 0
  fi
  if ! resource_owned network "$name"; then
    record_cleanup_warning "Skipped network cleanup after ownership check failed: $name"
    return 0
  fi
  docker network rm "$name" >/dev/null 2>&1 || \
    record_cleanup_warning "Failed to remove owned network: $name"
}

remove_owned_volume() {
  local name="$1"
  if ! docker volume inspect "$name" >/dev/null 2>&1; then
    return 0
  fi
  if ! resource_owned volume "$name"; then
    record_cleanup_warning "Skipped volume cleanup after ownership check failed: $name"
    return 0
  fi
  docker volume rm "$name" >/dev/null 2>&1 || \
    record_cleanup_warning "Failed to remove owned volume: $name"
}

capture_schema() {
  local schema_tmp="$EVIDENCE/final-schema.sql.tmp"
  if ! resource_owned container "$DB_CONTAINER"; then
    return 0
  fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null)" != "true" ]]; then
    return 0
  fi
  if docker exec "$DB_CONTAINER" sh -c \
    'export MYSQL_PWD="$(cat /run/secrets/mysql-root)"; exec mysqldump --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --no-data --routines --skip-comments codecoachai_v1' \
    > "$schema_tmp" 2>> "$EVIDENCE/schema-capture.log"; then
    mv "$schema_tmp" "$EVIDENCE/final-schema.sql"
  else
    rm -f "$schema_tmp"
    record_cleanup_warning "Could not capture schema before cleanup"
  fi
}

cleanup() {
  local exit_code="$1"
  trap - EXIT
  set +e

  if command -v docker >/dev/null 2>&1; then
    if resource_owned container "$FLYWAY_CONTAINER"; then
      docker logs "$FLYWAY_CONTAINER" > "$EVIDENCE/flyway-container.log" 2>&1
    fi
    if resource_owned container "$DB_CONTAINER"; then
      docker logs "$DB_CONTAINER" > "$EVIDENCE/mysql.log" 2>&1
      capture_schema
    fi

    remove_owned_container "$FLYWAY_CONTAINER"
    remove_owned_container "$DB_CONTAINER"
    remove_owned_network "$NETWORK"
    remove_owned_volume "$DB_VOLUME"
    remove_owned_volume "$M2_VOLUME"
  fi

  if [[ -n "$SECRET" ]]; then
    rm -f "$SECRET"
  fi
  if [[ -n "$SECRET_DIR" && "$SECRET_DIR" == "${SECRET_PARENT%/}/ccai-migration-secret."* ]]; then
    rm -rf -- "$SECRET_DIR"
  fi
  printf '%s\n' "$exit_code" > "$EVIDENCE/exit-code"
  if [[ "$exit_code" -ne 0 ]]; then
    printf 'Migration rehearsal failed. Evidence, including the last capturable schema, remains at: %s\n' "$EVIDENCE" >&2
  fi
  exit "$exit_code"
}
trap 'cleanup $?' EXIT

SECRET_DIR="$(mktemp -d "${SECRET_PARENT%/}/ccai-migration-secret.XXXXXXXX")"
SECRET="${SECRET_DIR}/mysql-root-password"
mkdir -p "$EVIDENCE"
chmod 700 "$EVIDENCE" "$SECRET_DIR"
openssl rand -hex 32 > "$SECRET"
chmod 600 "$SECRET"
: > "$EVIDENCE/preflight.log"
: > "$EVIDENCE/error.log"
exec > >(tee -a "$EVIDENCE/preflight.log") \
  2> >(tee -a "$EVIDENCE/error.log" >&2)
printf '[preflight] evidence capture started\n'

repo_digest_line_valid() {
  local digest="$1"
  [[ "$digest" =~ ^[^[:space:]]+@sha256:[0-9a-fA-F]{64}$ ]]
}

image_id_valid() {
  local image_id="$1"
  [[ "$image_id" =~ ^sha256:[0-9a-fA-F]{64}$ ]]
}

repo_digest_lines_valid() {
  local digest_lines="$1"
  local digest_line
  local digest_count=0

  [[ -n "$digest_lines" ]] || return 1
  while IFS= read -r digest_line; do
    [[ -n "$digest_line" ]] || return 1
    repo_digest_line_valid "$digest_line" || return 1
    ((digest_count += 1))
  done <<< "$digest_lines"
  (( digest_count > 0 ))
}

pull_image_with_digest() {
  local image="$1"
  local repo_digests
  local digest_lines
  local image_id

  printf '[preflight] pulling image: %s\n' "$image"
  docker pull "$image"
  repo_digests="$(docker image inspect --format '{{json .RepoDigests}}' "$image")"
  case "$repo_digests" in
    ""|"null"|"[]")
      echo "RepoDigests is empty after pull/inspect for image: $image" >&2
      return 1
      ;;
  esac
  if [[ "$repo_digests" == *'""'* ]]; then
    echo "RepoDigests contains an empty digest entry for image: $image" >&2
    return 1
  fi

  digest_lines="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image")"
  repo_digest_lines_valid "$digest_lines" || {
    echo "RepoDigests contains an empty or non-canonical digest line for image: $image" >&2
    return 1
  }
  image_id="$(docker image inspect --format '{{.Id}}' "$image")"
  image_id_valid "$image_id" || {
    echo "Docker image ID is not canonical for image: $image" >&2
    return 1
  }
  VALIDATED_REPO_DIGESTS="$repo_digests"
  VALIDATED_IMAGE_ID="$image_id"
  VALIDATED_IMAGE_SOURCE="registry-pull"
  printf '[preflight] validated immutable RepoDigests for image: %s\n' "$image"
}

inspect_preloaded_image() {
  local image="$1"
  local repo_digests
  local digest_lines
  local image_id

  printf '[preflight] using preloaded image without registry pull: %s\n' "$image"
  docker image inspect "$image" >/dev/null
  image_id="$(docker image inspect --format '{{.Id}}' "$image")"
  image_id_valid "$image_id" || {
    echo "Docker image ID is not canonical for preloaded image: $image" >&2
    return 1
  }

  repo_digests="$(docker image inspect --format '{{json .RepoDigests}}' "$image")"
  case "$repo_digests" in
    ""|"null"|"[]")
      repo_digests="[]"
      ;;
    *)
      if [[ "$repo_digests" == *'""'* ]]; then
        echo "RepoDigests contains an empty digest entry for preloaded image: $image" >&2
        return 1
      fi
      digest_lines="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$image")"
      repo_digest_lines_valid "$digest_lines" || {
        echo "RepoDigests contains an empty or non-canonical digest line for preloaded image: $image" >&2
        return 1
      }
      ;;
  esac

  VALIDATED_REPO_DIGESTS="$repo_digests"
  VALIDATED_IMAGE_ID="$image_id"
  VALIDATED_IMAGE_SOURCE="preloaded"
  printf '[preflight] validated immutable image ID for preloaded image: %s\n' "$image"
}

resolve_image_identity() {
  local image="$1"
  VALIDATED_REPO_DIGESTS=""
  VALIDATED_IMAGE_ID=""
  VALIDATED_IMAGE_SOURCE=""
  if [[ "$PRELOADED_IMAGES" -eq 1 ]]; then
    inspect_preloaded_image "$image"
  else
    pull_image_with_digest "$image"
  fi
}

check_free_space() {
  local description="$1"
  local available_kb="$2"
  [[ "$available_kb" =~ ^[0-9]+$ ]] || {
    echo "Could not determine free space for $description" >&2
    return 1
  }
  if (( available_kb < MIN_FREE_KB )); then
    echo "Insufficient free space for $description: ${available_kb}KB available, ${MIN_FREE_KB}KB required" >&2
    return 1
  fi
}

echo "Evidence directory: $EVIDENCE"

printf '[preflight] checking Docker daemon\n'
docker info | tee "$EVIDENCE/docker-info.txt"
host_free_kb="$(df -Pk "$EVIDENCE_PARENT" | awk 'END {print $4}')"
check_free_space "evidence filesystem" "$host_free_kb"

resolve_image_identity "$MYSQL_IMAGE"
MYSQL_REPO_DIGESTS="$VALIDATED_REPO_DIGESTS"
MYSQL_IMAGE_ID="$VALIDATED_IMAGE_ID"
MYSQL_IMAGE_SOURCE="$VALIDATED_IMAGE_SOURCE"
resolve_image_identity "$MAVEN_IMAGE"
MAVEN_REPO_DIGESTS="$VALIDATED_REPO_DIGESTS"
MAVEN_IMAGE_ID="$VALIDATED_IMAGE_ID"
MAVEN_IMAGE_SOURCE="$VALIDATED_IMAGE_SOURCE"

{
  printf 'image\trepo_digests\timage_id\tsource\n'
  printf '%s\t%s\t%s\t%s\n' \
    "$MYSQL_IMAGE" \
    "$MYSQL_REPO_DIGESTS" \
    "$MYSQL_IMAGE_ID" \
    "$MYSQL_IMAGE_SOURCE"
  printf '%s\t%s\t%s\t%s\n' \
    "$MAVEN_IMAGE" \
    "$MAVEN_REPO_DIGESTS" \
    "$MAVEN_IMAGE_ID" \
    "$MAVEN_IMAGE_SOURCE"
} > "$EVIDENCE/image-digests.tsv"

docker_free_kb="$(
  docker run --rm \
    --pull=never \
    --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
    --entrypoint sh \
    "$MYSQL_IMAGE_ID" \
    -c "df -Pk /var/lib/mysql | awk 'END {print \$4}'"
)"
check_free_space "Docker storage" "$docker_free_kb"

docker volume create \
  --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
  "$M2_VOLUME" >/dev/null

{
  printf '[preflight] checking Docker and Maven/Flyway dependencies\n'
  docker version
  docker run --rm \
    --pull=never \
    --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
    "$MAVEN_IMAGE_ID" mvn -version
  docker run --rm \
    --pull=never \
    --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
    --mount "type=bind,src=$REPO,dst=/workspace,readonly" \
    --mount "type=volume,src=$M2_VOLUME,dst=/root/.m2" \
    -w /workspace \
    "$MAVEN_IMAGE_ID" sh -lc "
      mvn -B -N -DskipTests \
        -Dflyway.locations=filesystem:/workspace/sql/migration \
        help:describe \
        -Dplugin=org.flywaydb:flyway-maven-plugin \
        -Ddetail=false
    "
}

docker network create \
  --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
  "$NETWORK" >/dev/null
docker volume create \
  --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
  "$DB_VOLUME" >/dev/null

docker run -d \
  --pull=never \
  --name "$DB_CONTAINER" \
  --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
  --network "$NETWORK" \
  --mount "type=volume,src=$DB_VOLUME,dst=/var/lib/mysql" \
  --mount "type=bind,src=$REPO/sql/init.sql,dst=/docker-entrypoint-initdb.d/001-init.sql,readonly" \
  --mount "type=bind,src=$SECRET,dst=/run/secrets/mysql-root,readonly" \
  -e MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root \
  -e MYSQL_DATABASE=codecoachai_v1 \
  "$MYSQL_IMAGE_ID" \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --default-time-zone=+08:00 >/dev/null

baseline_ready() {
  docker exec "$DB_CONTAINER" sh -c '
    export MYSQL_PWD="$(cat /run/secrets/mysql-root)"
    mysqladmin --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --silent ping >/dev/null 2>&1 || exit 1
    sentinel_table_count="$(mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --skip-column-names --batch codecoachai_v1 --execute "$1")" || exit 1
    sentinel_row_count="$(mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --skip-column-names --batch codecoachai_v1 --execute "$2")" || exit 1
    sentinel_v2_table_count="$(mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --skip-column-names --batch codecoachai_v1 --execute "$3")" || exit 1
    sentinel_v2_final_count="$(mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --skip-column-names --batch codecoachai_v1 --execute "$4")" || exit 1
    [ "$sentinel_table_count" = "1" ] &&
      [ "$sentinel_row_count" = "1" ] &&
      [ "$sentinel_v2_table_count" = "1" ] &&
      [ "$sentinel_v2_final_count" = "1" ]
  ' sh \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'codecoachai_v1' AND table_name = 'system_config';" \
    "SELECT COUNT(*) FROM system_config WHERE config_key = 'ai.timeout.seconds';" \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'codecoachai_v1' AND table_name = 'file_info';" \
    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'codecoachai_v1' AND table_name = 'study_task' AND column_name = 'planned_date';"
}

ready=0
for _ in $(seq 1 120); do
  if baseline_ready; then
    ready=1
    break
  fi
  sleep 2
done
[[ "$ready" -eq 1 ]] || {
  echo "MySQL did not become TCP-ready with the baseline sentinel table" >&2
  exit 1
}

mysql_query() {
  local query="$1"
  printf '%s\n' "$query" | docker exec -i "$DB_CONTAINER" sh -c \
    'export MYSQL_PWD="$(cat /run/secrets/mysql-root)"; exec mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --batch --raw --default-character-set=utf8mb4 codecoachai_v1'
}

mysql_query "
SELECT 'baseline_sentinel_table' AS metric, COUNT(*) AS value
FROM information_schema.tables
WHERE table_schema = 'codecoachai_v1'
  AND table_name = 'system_config';

SELECT 'baseline_sentinel_row' AS metric, COUNT(*) AS value
FROM system_config
WHERE config_key = 'ai.timeout.seconds';

SELECT 'baseline_v2_file_info_table' AS metric, COUNT(*) AS value
FROM information_schema.tables
WHERE table_schema = 'codecoachai_v1'
  AND table_name = 'file_info';

SELECT 'baseline_v2_final_column' AS metric, COUNT(*) AS value
FROM information_schema.columns
WHERE table_schema = 'codecoachai_v1'
  AND table_name = 'study_task'
  AND column_name = 'planned_date';
" > "$EVIDENCE/baseline-sentinel.tsv"

mysql_query "SELECT VERSION() AS mysql_version;" > "$EVIDENCE/mysql-version.txt"

docker run \
  --pull=never \
  --name "$FLYWAY_CONTAINER" \
  --label "${OWNER_LABEL}=${OWNER_TOKEN}" \
  --network "$NETWORK" \
  --mount "type=bind,src=$REPO,dst=/workspace,readonly" \
  --mount "type=bind,src=$SECRET,dst=/run/secrets/mysql-root,readonly" \
  --mount "type=volume,src=$M2_VOLUME,dst=/root/.m2" \
  -w /workspace \
  "$MAVEN_IMAGE_ID" sh -lc "
    export FLYWAY_PASSWORD=\"\$(cat /run/secrets/mysql-root)\"
    mvn -B -N -DskipTests \
      -Dflyway.locations=filesystem:/workspace/sql/migration \
      -Dflyway.url='jdbc:mysql://${DB_CONTAINER}:3306/codecoachai_v1?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' \
      -Dflyway.user=root \
      flyway:info flyway:migrate flyway:validate flyway:info
  " 2>&1 | tee "$EVIDENCE/flyway.log"

mysql_query "
SELECT version, description, type, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
" > "$EVIDENCE/flyway-history.tsv"

mysql_query "
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = 'codecoachai_v1'
ORDER BY table_name;
" > "$EVIDENCE/tables.tsv"

mysql_query "
SELECT table_name, column_name, column_type, is_nullable,
       character_set_name, collation_name, generation_expression
FROM information_schema.columns
WHERE table_schema = 'codecoachai_v1'
ORDER BY table_name, ordinal_position;
" > "$EVIDENCE/columns.tsv"

mysql_query "
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = 'codecoachai_v1'
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;
" > "$EVIDENCE/indexes.tsv"

docker exec -i "$DB_CONTAINER" sh -c \
  'export MYSQL_PWD="$(cat /run/secrets/mysql-root)"; exec mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --batch --raw --default-character-set=utf8mb4 codecoachai_v1' \
  < "$REPO/scripts/verify-migration-schema.sql" \
  > "$EVIDENCE/verification.tsv"

assert_metric() {
  local metric="$1"
  local expected="$2"
  local actual
  actual="$(
    awk -F '\t' -v metric="$metric" '
      $1 == metric { value = $2; matches++ }
      END {
        if (matches == 1) {
          print value
        } else {
          exit 1
        }
      }
    ' "$EVIDENCE/verification.tsv"
  )" || {
    echo "Verification metric is missing or duplicated: $metric" >&2
    return 1
  }
  [[ "$actual" == "$expected" ]] || {
    echo "Verification metric $metric expected $expected but got $actual" >&2
    return 1
  }
}

assert_metric migration_4_058_4_071_success_count 14
assert_metric migration_4_058_4_071_missing_count 0
assert_metric target_table_count 28
assert_metric v4_067_evidence_columns_exact_count 9
assert_metric v4_067_readiness_dimension_exact 1
assert_metric v4_067_evidence_project_index_exact 1
assert_metric v4_069_active_unique_index_count 3
assert_metric career_import_row_mediumtext_count 2
assert_metric v4_071_rubric_seed_exact 1
assert_metric v4_071_scenario_seed_exact_count 8
assert_metric ats_active_template_exact_count 3

capture_schema
printf 'Migration rehearsal passed. Evidence: %s\n' "$EVIDENCE"
