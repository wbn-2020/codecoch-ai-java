# CodeCoachAI Release Engineering Runbook

## Scope

This runbook covers:

- backend and frontend CI quality gates;
- four deployable backend services: Gateway, Core, AI, and Search;
- non-root application images and loopback Actuator probes;
- test-environment startup failure detection and orchestrator recovery;
- immutable release directory generation and SHA-256 verification;
- host-key-pinned SFTP upload, release pointer activation, and rollback.

The release transport does not run Flyway, publish Nacos configuration, restart
containers, or execute acceptance tests. Those are separate approved deployment
steps. No remote shell command or SCP command is used by the transport tool.

## Required Toolchain

Use:

- JDK 17;
- Maven 3.9.x;
- Node.js 20 and `npm ci`;
- Python 3.12;
- Docker with Compose v2 for image/Compose validation;
- Paramiko from `scripts/release/requirements.txt` for remote transport.

None of the local verification commands in this runbook starts a backend or
frontend application service.

## CI Contract

`.github/workflows/ci.yml` runs for:

- `main`;
- `dev-v3`;
- `dev-260703`;
- `dev-fb`;
- `dev-fb-260803`;
- `dev-fb-260805`;
- pull requests targeting those branches;
- manual `workflow_dispatch`.

The backend job runs:

```text
mvn -B -ntp -Dstyle.color=never clean test
mvn -B -ntp -Dstyle.color=never -Pphase2-dependency-gates -DskipTests verify
```

The phase-two dependency gate is an explicit CI step. It must not be described as a
runtime acceptance result; RocketMQ, Nacos, health, resource, API and rollback behavior
still require the test-environment acceptance template.

The frontend job checks out the frontend repository and runs:

```text
npm ci --ignore-scripts
npm run type-check
npm run test:unit:run
npm run build
```

Repository variables:

| Variable | Default | Purpose |
|---|---|---|
| `CODECOACHAI_FRONTEND_REPOSITORY` | `wbn-2020/codecoch-ai-vue` | Frontend repository |
| `CODECOACHAI_FRONTEND_REF` | `dev-260703` | Frontend branch or immutable ref |

If the frontend repository is private, configure the
`CODECOACHAI_FRONTEND_READ_TOKEN` Actions secret with read-only repository
access. Prefer an immutable frontend commit for a formal release candidate.

CI uploads only the four tested deployable JARs and frontend `dist` separately,
builds one immutable release directory, verifies `SHA256SUMS`, and builds one
runtime image per deployable service. Historical `*.tar.gz` and `*.tgz` files
are not release inputs.

## Local Quality Gates

Run the release/container contract tests:

```text
python -m unittest discover -s scripts/release/tests -p "test_*.py" -v
```

Run shell syntax validation:

```text
sh -n scripts/docker/entrypoint.sh
bash -n scripts/rehearse-migrations.sh
```

When Docker is available, expand Compose without starting containers:

```text
docker compose --profile app-services --profile search-service config
```

Build one runtime image from a tested JAR:

```text
docker build \
  --target runtime-prebuilt \
  --build-arg JAR_FILE=codecoachai-core/target/codecoachai-core-1.0.0-SNAPSHOT.jar \
  --build-arg SERVICE_PORT=9200 \
  -t codecoachai/codecoachai-core:local \
  .
```

The runtime contract is:

- Java 17 JRE;
- UID/GID `10001:10001`;
- no `sh -c "java ..."` command construction;
- health probe implemented by the bundled `HealthProbe` Java class;
- health URL restricted to loopback HTTP;
- application PID monitored by the container entrypoint.

## Deployable Service Contract

Only these backend JARs and runtime images are deployable:

```text
codecoachai-gateway  port 8080
codecoachai-core     port 9200
codecoachai-ai       port 9206
codecoachai-search   port 8091
```

The legacy `auth`, `user`, `resume`, `interview`, `question`, `file`, `system`,
and `task` modules remain build-time libraries of Core. They must not be
released as standalone JARs or started as standalone containers.

Compose application profiles select different runtime containers:

- `app-services`: Gateway, Core, AI, and their MySQL, Redis, Nacos, Flyway, and
  RocketMQ dependencies;
- `search-service`: Search and its RocketMQ and Elasticsearch dependencies.

The Nacos configuration gate is intentionally all-or-nothing for the current
four-service release and audits Common, Redis, Gateway, Core, AI, and Search
together. Do not use the runtime profile split to publish only a partial Nacos
topology.

Core listens on container and default host port `9200`. Elasticsearch retains
container port `9200` but uses default host port `9210`, preventing a host-port
collision. Override `CODECOACHAI_ELASTICSEARCH_PORT` only when the selected
host port is available.

## Test Environment Startup And Recovery

The workstation does not have enough resources for application startup or
end-to-end validation. Do not run Compose `up`, backend processes, Docker
dependencies, or acceptance workflows locally for this refactor. Perform
application startup and acceptance exclusively in the approved test
environment after the release artifact is published.

Before starting the four application containers in the test environment:

1. Confirm the test database backup, completed Flyway migration, and required
   secrets are available. Populate `MYSQL_HOST`, `MYSQL_PORT`,
   `MYSQL_DATABASE`, `MYSQL_USERNAME`, `REDIS_HOST`, `REDIS_PORT`,
   `ROCKETMQ_NAME_SERVER`, `ELASTICSEARCH_URIS`, `QDRANT_BASE_URL`, and
   `NACOS_SERVER_ADDR` with addresses reachable from the application
   containers. Release Compose deliberately rejects missing external endpoint
   values instead of silently falling back to local service names.
2. Confirm Nacos contains the reviewed consolidated Gateway/Core/AI/Search
   configuration for the selected `SPRING_PROFILES_ACTIVE` value, including the
   Core `9200` registration and routes targeting Core. The repository currently
   contains a reviewed six-file set only for the `dev` profile, so this release
   must use `SPRING_PROFILES_ACTIVE=dev`. A different profile is blocked until a
   complete reviewed `codecoachai-*-<profile>.yml` set is added. Create one dedicated,
   non-`public` namespace and set its exact ID as `NACOS_NAMESPACE` for the
   initializer and all four services. Empty values and the literal `public`
   namespace are deployment blockers. `nacos-config-init` authenticates through
   the Nacos login/token flow and compares exact tenant content. In a disposable
   local environment, `NACOS_CONFIG_BOOTSTRAP_ENABLED=true` may create
   configurations that are missing after a full preflight and blocks any
   already-visible drift. The Nacos HTTP API does not provide an atomic
   create-if-absent operation, so shared environments must keep this flag
   `false` and publish reviewed configuration through a separate serialized
   operation. With the flag set to `false`, every required Data ID must already
   match the checked-in reviewed file.
   Configure three directional Gateway HMAC
   secrets (`Gateway -> Core`, `Gateway -> AI`, `Gateway -> Search`) plus
   distinct Core, AI, and Search outbound secrets. Verify the Core/AI/Search
   caller key rings use the matching values with their reviewed `/inner/**`
   permissions. The shared test Nacos deployment must enable authentication and
   authorization, use non-default `NACOS_USERNAME`/`NACOS_PASSWORD` credentials,
   and be isolated from untrusted workloads. The Java services in this release
   assume Nacos is reachable on an approved private network. Setting
   `NACOS_CONFIG_SCHEME=https` secures only the initializer HTTP audit and does
   not by itself enable TLS for the Java Nacos clients; do not expose their Nacos
   transport outside the private network without a separately verified client
   TLS configuration.
   The same transport boundary applies to the exact-path multipart upload
   allowlist in Gateway. Those requests use
   `STREAMING-UNSIGNED-PAYLOAD`: HMAC authenticates routing and identity fields
   but not file bytes or multipart form fields. For this test release, verify
   Gateway, Core, and AI ports remain bound to host loopback and that their
   container network admits no untrusted workloads. A cross-host, shared-network,
   or production rollout is blocked until Gateway-to-Core/AI uses authenticated
   TLS; prefer mTLS once certificate automation is available.
3. Confirm Core receives non-empty `OSS_BUCKET`, `OSS_AK`, and `OSS_SK`, and
   that Core and AI receive the same reviewed `QDRANT_BASE_URL` and
   `QDRANT_API_KEY`. For the checked-in Compose topology the Qdrant URL is
   `http://qdrant:6333`; never use container loopback for this dependency.
4. Scale legacy public-facing services and the legacy Search instance to zero
   before routing traffic to the consolidated containers. Keep only the legacy
   Task consumer temporarily while Core starts with
   `CODECOACHAI_TASK_CONSUMERS_ENABLED=false`.
5. Start AI and confirm it is healthy. Start Core with consumers disabled, then
   start Gateway and verify synchronous traffic. Stop legacy Task, set
   `CODECOACHAI_TASK_CONSUMERS_ENABLED=true`, recreate Core, and verify its
   asynchronous consumers before starting Search. Keep every legacy standalone
   application service scaled to zero after the handover.

The image entrypoint checks Actuator independently of Docker's displayed
container state:

| Environment variable | Default | Meaning |
|---|---:|---|
| `HEALTHCHECK_URL` | service build port | Loopback Actuator health URL |
| `HEALTHCHECK_TIMEOUT_MILLIS` | `3000` | One probe timeout |
| `HEALTH_STARTUP_TIMEOUT_SECONDS` | `180` | Maximum time to reach `UP` |
| `HEALTH_MONITOR_INTERVAL_SECONDS` | `10` | Probe interval |
| `HEALTH_MONITOR_FAILURE_THRESHOLD` | `6` | Failures after first readiness |

Core, AI, and Search default to a 240-second startup timeout. Gateway defaults
to 180 seconds. If Spring startup fails but a RocketMQ non-daemon thread keeps
the JVM alive, the entrypoint terminates the JVM and exits non-zero.
`restart: on-failure` then retries. This prevents a container from remaining
merely `Up` while its Actuator endpoint refuses connections.

Run the parameterized operations probe from the test environment after startup
or deployment:

```text
python scripts/release/check_health.py \
  --service gateway=http://127.0.0.1:8080/actuator/health \
  --container gateway=codecoachai-gateway \
  --service core=http://127.0.0.1:9200/actuator/health \
  --container core=codecoachai-core \
  --service ai=http://127.0.0.1:9206/actuator/health \
  --container ai=codecoachai-ai \
  --service search=http://127.0.0.1:8091/actuator/health \
  --container search=codecoachai-search \
  --attempts 5 \
  --interval-seconds 3
```

Multiple `--service NAME=URL` and `--container NAME=CONTAINER` arguments are
supported. The equivalent environment variables are comma-separated
`CODECOACHAI_HEALTH_SERVICES` and `CODECOACHAI_HEALTH_CONTAINERS`. The script
returns non-zero unless every Actuator response is JSON with `status=UP`. A
Docker container in `running` state with a failing application endpoint is
reported as `classification=false-up`.

The upstream Qdrant image does not guarantee an HTTP client in the image, so the
Compose file does not declare a `curl`/`wget` based Qdrant probe. Check Qdrant
from an application or a dedicated operations probe on the Compose network.

## Build A Release Directory

The CI `release` job is the authoritative candidate builder. It waits for
backend, frontend, per-service Docker contract, and runtime-image jobs. The
runtime-image job builds one `runtime-base` image tagged with the release ID and
exports it with `docker save`. The release builder then combines:

- the four tested backend JARs;
- frontend `dist`;
- the Docker-save runtime image;
- the Compose, Nacos, Flyway, health-check, migration, and operations control
  bundle copied from the same backend commit.

Every file is covered by `SHA256SUMS`. The Docker-save manifest must contain
exactly `codecoachai/runtime-base:<release-id>`; a generic tar or a differently
tagged image is rejected.

For a manual rehearsal, build backend and frontend first and use a clean source
tree. The builder rejects dirty Git repositories unless `--allow-dirty` is
explicitly supplied; never use that override for a deployable candidate.

Example:

```text
release_id=20260804-001

docker build \
  --target runtime-base \
  --tag "codecoachai/runtime-base:${release_id}" \
  .

docker save \
  --output "../deploy-artifacts/codecoachai-runtime-base.tar" \
  "codecoachai/runtime-base:${release_id}"

python scripts/release/build_release.py \
  --backend-artifacts . \
  --backend-control-source . \
  --frontend-dist ../codecoch-ai-vue/dist \
  --runtime-image ../deploy-artifacts/codecoachai-runtime-base.tar \
  --runtime-image-tag "$release_id" \
  --output-root ../deploy-artifacts/releases \
  --release-id "$release_id" \
  --backend-repo . \
  --frontend-repo ../codecoch-ai-vue

python scripts/release/verify_release.py \
  "../deploy-artifacts/releases/${release_id}"
```

Release layout:

```text
<release-id>/
  backend/
    codecoachai-*.jar
  frontend/
    index.html
    assets/
  runtime/
    codecoachai-runtime-base.tar
  control/
    docker-compose.yml
    docker-compose.release.yml
    docs/nacos/codecoachai-*-dev.yml
    docs/operations/release-engineering-runbook.md
    scripts/docker/
    scripts/nacos/
    scripts/release/
    sql/migration/
  release.json
  SHA256SUMS
```

The builder requires exactly one deployable JAR for Gateway, Core, AI, and
Search, validates each JAR as ZIP, copies only frontend `dist`, excludes
historical tar archives, validates the runtime image repository and tag, records
the backend SHA for the control bundle, writes source SHAs to `release.json`,
and atomically renames the completed local staging directory.

## Pin The SSH Host Key

Never use `StrictHostKeyChecking=accept-new`, `AutoAddPolicy`, or an empty
`known_hosts` file for a deployment.

Create a pending file:

```text
ssh-keyscan -p "$CODECOACHAI_DEPLOY_PORT" "$CODECOACHAI_DEPLOY_HOST" \
  > known_hosts.pending
ssh-keygen -lf known_hosts.pending -E sha256
```

`ssh-keyscan` is not authentication. Compare the displayed fingerprint with a
fingerprint received through a separate trusted channel. Only after an exact
match should the file become the path referenced by
`CODECOACHAI_KNOWN_HOSTS`.

Set the expected value independently in
`CODECOACHAI_DEPLOY_HOST_FINGERPRINT`. The transport requires both OpenSSH
`known_hosts` validation and an exact SHA-256 fingerprint comparison.

## Configure Transport

Use `scripts/release/release.env.example` as the variable checklist. Do not
store a populated copy in the repository.

Required non-secret variables:

```text
CODECOACHAI_DEPLOY_HOST
CODECOACHAI_DEPLOY_PORT
CODECOACHAI_DEPLOY_USER
CODECOACHAI_REMOTE_ROOT
CODECOACHAI_KNOWN_HOSTS
CODECOACHAI_DEPLOY_HOST_FINGERPRINT
```

Inject exactly one authentication source:

```text
CODECOACHAI_DEPLOY_PASSWORD
```

or:

```text
CODECOACHAI_DEPLOY_IDENTITY_FILE
CODECOACHAI_DEPLOY_KEY_PASSPHRASE
```

The identity file should be mounted by a secret manager with restrictive
permissions. The tool disables SSH Agent use and implicit local key discovery.
It never prints passwords, passphrases, or key contents.

Install the transport dependency:

```text
python -m pip install -r scripts/release/requirements.txt
```

## Upload, Preflight, Activate, And Roll Back

Every `release_transport.py` command is dry-run unless `--execute` is present.
Docker and Compose commands are real operations and must run only in the
approved target-host session.

Review an upload plan:

```text
python scripts/release/release_transport.py upload \
  --release-dir ../deploy-artifacts/releases/20260804-001
```

Upload only after the plan and release ID are approved:

```text
python scripts/release/release_transport.py upload \
  --release-dir ../deploy-artifacts/releases/20260804-001 \
  --execute \
  --confirm-release-id 20260804-001
```

The upload path is:

```text
<remote-root>/.incoming/<release-id>.<random>
<remote-root>/releases/<release-id>
```

Files are uploaded with SFTP, hashed again through SFTP, checked against
`SHA256SUMS`, and renamed into `releases` only after all checks pass.

### Candidate Target-Host Setup

Do not use a mutable repository checkout for deployment. On the target host,
select the uploaded candidate and run every preflight command from its immutable
control directory:

```text
export CANDIDATE_RELEASE_ID=20260804-001
export CANDIDATE_RELEASE_ROOT="${CODECOACHAI_REMOTE_ROOT}/releases/${CANDIDATE_RELEASE_ID}"
export CANDIDATE_CONTROL_ROOT="${CANDIDATE_RELEASE_ROOT}/control"
export CODECOACHAI_RUNTIME_IMAGE_TAG="${CANDIDATE_RELEASE_ID}"
export COMPOSE_PROJECT_NAME=codecoachai

cd "$CANDIDATE_CONTROL_ROOT"

docker load \
  --input "${CANDIDATE_RELEASE_ROOT}/runtime/codecoachai-runtime-base.tar"

docker image inspect \
  "codecoachai/runtime-base:${CANDIDATE_RELEASE_ID}"

printf '%s' "${CODECOACHAI_NACOS_GUARD_IMAGE}" \
  | grep -Eq '^.+@sha256:[0-9a-f]{64}$' \
  || { echo "CODECOACHAI_NACOS_GUARD_IMAGE must be pinned by digest" >&2; exit 1; }
printf '%s' "${CODECOACHAI_FLYWAY_TOOL_IMAGE}" \
  | grep -Eq '^.+@sha256:[0-9a-f]{64}$' \
  || { echo "CODECOACHAI_FLYWAY_TOOL_IMAGE must be pinned by digest" >&2; exit 1; }

docker image inspect "${CODECOACHAI_NACOS_GUARD_IMAGE}"
docker image inspect "${CODECOACHAI_FLYWAY_TOOL_IMAGE}"
```

`CODECOACHAI_RUNTIME_IMAGE_TAG` must equal the candidate release ID. Release
Compose sets `pull_policy: never`; application startup must fail rather than
pull or build an unreviewed replacement image. The Nacos guard and Flyway tool
images are deployment prerequisites rather than release outputs. Pre-provision
their reviewed digest-pinned references on the target host; the release workflow
must not pull mutable public tags during a gate.

### PDF CJK Font Gate

The runtime image installs `font-noto-cjk`, refreshes the font cache, and
exposes the selected regular face at the stable path
`/opt/codecoachai/fonts/NotoSansCJK-Regular.ttc`. Release Compose passes that
exact path to Core as `RESUME_EXPORT_PDF_FONT_PATH`; the PDF renderer fails
closed instead of replacing unencodable Chinese text.

Before activating a candidate that includes PDF export changes, verify the
loaded immutable image without starting an application service:

```text
docker run --rm --entrypoint sh \
  "codecoachai/runtime-base:${CANDIDATE_RELEASE_ID}" \
  -c 'test -r /opt/codecoachai/fonts/NotoSansCJK-Regular.ttc && fc-scan --format "%{family}\n" /opt/codecoachai/fonts/NotoSansCJK-Regular.ttc | grep -qi "Noto"'
```

Record the command result with the candidate release evidence. A missing font,
an unreadable stable path, or a non-Noto scan result blocks activation and must
not be worked around by substituting a host-mounted font.

### Candidate Nacos Gate

The release override forces `nacos-config-init` into audit-only mode. Run it
from the candidate control directory against the existing dedicated namespace:

```text
docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  --profile app-services \
  --profile search-service \
  run --rm --no-deps nacos-config-init
```

`NACOS_SERVER_ADDR` is the single Nacos host and port used by the initializer
and all four applications. `NACOS_CONFIG_SCHEME` and
`NACOS_CONFIG_CONTEXT_PATH` only form the initializer's HTTP API URL; do not
configure a second Nacos address.

Create missing shared-environment Data IDs through a separate reviewed and
serialized operation. Do not enable automatic create-missing publication during
release: the Nacos HTTP API cannot atomically guarantee create-if-absent when
another publisher races between the guard read and publish request.

### Candidate Flyway Gate

Only after the Nacos gate succeeds, run Flyway from the same candidate control
directory. Its dedicated POM and `sql/migration` directory are part of the
candidate manifest. Never run migrations from a mutable checkout or rely on an
application recreation to start Flyway:

```text
docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  run --rm --no-deps flyway-migrate
```

### Activate After Both Gates

Do not activate until both commands above have exited successfully and their
audit/migration evidence has been retained. Then review and perform pointer
activation from the trusted transport workstation, using the same
`CANDIDATE_RELEASE_ID`:

```text
export CANDIDATE_RELEASE_ID=20260804-001

python scripts/release/release_transport.py activate \
  --release-id "$CANDIDATE_RELEASE_ID"

python scripts/release/release_transport.py activate \
  --release-id "$CANDIDATE_RELEASE_ID" \
  --execute \
  --confirm-release-id "$CANDIDATE_RELEASE_ID"
```

Activation updates:

```text
<remote-root>/current  -> releases/<candidate-release-id>
<remote-root>/previous -> releases/<former-current>
```

The server must support the SFTP POSIX rename extension; the tool refuses a
non-atomic fallback. The release Compose override reads each backend JAR through
`CODECOACHAI_REMOTE_ROOT/current/backend`. The loaded runtime image and the
candidate control directory continue to use the immutable release ID.

### Phased Application Handover

Recreate only application containers. `--no-deps` prevents application
deployment from starting infrastructure, and `--no-build` prevents Compose from
building from the control bundle. Stop immediately when any health command
returns non-zero.

#### Phase 1: AI

Export the disabled consumer mode so Compose passes it into the recreated Core
container:

```text
export CODECOACHAI_TASK_CONSUMERS_ENABLED=false

docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  --profile app-services \
  --profile search-service \
  up -d --no-deps --no-build --force-recreate \
  codecoachai-ai

python scripts/release/check_health.py \
  --service ai=http://127.0.0.1:9206/actuator/health \
  --container ai=codecoachai-ai
```

#### Phase 2: Core With Consumers Disabled

Keep `CODECOACHAI_TASK_CONSUMERS_ENABLED=false` exported:

```text
docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  --profile app-services \
  up -d --no-deps --no-build --force-recreate \
  codecoachai-core

python scripts/release/check_health.py \
  --service core=http://127.0.0.1:9200/actuator/health \
  --container core=codecoachai-core
```

#### Phase 3: Stop Legacy Task

Use the legacy platform's approved scale-to-zero/stop operation. Verify that no
legacy Task process or container remains and that its RocketMQ consumer group no
longer has a legacy member. This is a hard gate; do not enable Core consumers
until the evidence is recorded.

#### Phase 4: Core With Consumers Enabled

Export the enabled consumer mode, recreate only Core, and run the Core health
check again:

```text
export CODECOACHAI_TASK_CONSUMERS_ENABLED=true

docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  --profile app-services \
  up -d --no-deps --no-build --force-recreate \
  codecoachai-core

python scripts/release/check_health.py \
  --service core=http://127.0.0.1:9200/actuator/health \
  --container core=codecoachai-core
```

Complete an asynchronous acceptance case and verify exactly one consumer handles
the message before continuing.

#### Phase 5: Gateway

```text
docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  --profile app-services \
  up -d --no-deps --no-build --force-recreate \
  codecoachai-gateway

python scripts/release/check_health.py \
  --service gateway=http://127.0.0.1:8080/actuator/health \
  --container gateway=codecoachai-gateway
```

Complete synchronous Gateway-to-Core and Gateway-to-AI smoke tests before
starting Search. Include one reviewed multipart upload for each deployed upload
owner and confirm the application ports are not reachable from outside the
approved proxy/host boundary.

#### Phase 6: Search

```text
docker compose \
  -f docker-compose.yml \
  -f docker-compose.release.yml \
  --profile search-service \
  up -d --no-deps --no-build --force-recreate \
  codecoachai-search

python scripts/release/check_health.py \
  --service search=http://127.0.0.1:8091/actuator/health \
  --container search=codecoachai-search
```

The base image contains the JRE, health probe, and entrypoint only; the bind
mounted JAR comes from the active immutable release directory. Because Docker
resolves a bind mount when the container is created, recreate the four
application containers after every activation or rollback, then run health and
acceptance checks. Infrastructure lifecycle is independent. The local base
Compose persists Nacos data and RocketMQ store/logs, but a shared test or
production environment should normally use externally managed infrastructure
with its own backup and recovery policy.

Activation and rollback acquire `<remote-root>/.release-pointer.lock` with an
atomic SFTP directory create. A leftover lock means an earlier operation was
interrupted; inspect `current`, `previous`, and the target manifests before
removing that lock manually.

### Rollback

Before changing pointers, load and inspect the image stored by the current
`previous` release. This prevents rollback from reusing or building an unrelated
local image:

```text
export PREVIOUS_RELEASE_ID="$(basename -- "$(readlink -- "${CODECOACHAI_REMOTE_ROOT}/previous")")"
export PREVIOUS_RELEASE_ROOT="${CODECOACHAI_REMOTE_ROOT}/releases/${PREVIOUS_RELEASE_ID}"
export PREVIOUS_CONTROL_ROOT="${PREVIOUS_RELEASE_ROOT}/control"
export CODECOACHAI_RUNTIME_IMAGE_TAG="${PREVIOUS_RELEASE_ID}"
export COMPOSE_PROJECT_NAME=codecoachai

docker load \
  --input "${CODECOACHAI_REMOTE_ROOT}/previous/runtime/codecoachai-runtime-base.tar"

docker image inspect \
  "codecoachai/runtime-base:${PREVIOUS_RELEASE_ID}"
```

Only after the previous image is loaded successfully, swap the pointers from
the trusted transport workstation:

```text
python scripts/release/release_transport.py rollback

python scripts/release/release_transport.py rollback \
  --execute \
  --confirm ROLLBACK
```

Rollback swaps `current` and `previous` after verifying the previous release.
Change to `$PREVIOUS_CONTROL_ROOT` and repeat the application recreation and
health sequence with `--no-deps --no-build --force-recreate`; use the previously
recorded consumer mode and skip the legacy Task stop only when it is already
verified at zero.

It does not reverse database migrations or Nacos changes. Database and
configuration rollback compatibility must be decided before deployment.

## Deployment Gates

Do not activate a release until all are true:

1. Backend tests and package passed.
2. Frontend type-check, tests, and production build passed.
3. Release manifest verification passed.
4. Required Docker images built as UID/GID `10001:10001`.
5. Database migration rehearsal and backup passed.
6. Nacos backup and intended configuration diff were reviewed.
7. Legacy standalone services are stopped before Core workers are enabled,
   preventing duplicate RocketMQ consumption.
8. The current release ID and rollback target were recorded.
9. Gateway, Core, AI, and Search Actuator endpoints report `UP`, not only
   Docker `Up`.

After activation, verify every expected Actuator endpoint, application login,
critical API workflows, asynchronous task consumption, frontend static assets,
container restart counts, and recent error logs before accepting the release.
