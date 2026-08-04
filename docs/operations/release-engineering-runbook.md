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
- pull requests targeting those branches;
- manual `workflow_dispatch`.

The backend job runs:

```text
mvn -B -ntp -Dstyle.color=never clean test
mvn -B -ntp -Dstyle.color=never -DskipTests package
```

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

Compose profiles are intentionally separate:

- `app-services`: Gateway, Core, AI, and their MySQL, Redis, Nacos, Flyway, and
  RocketMQ dependencies;
- `search-service`: Search and its RocketMQ and Elasticsearch dependencies.

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
   secrets are available.
2. Confirm Nacos contains the reviewed consolidated Gateway/Core/AI/Search
   configuration, including the Core `9200` registration and routes targeting
   Core. Configure four distinct outbound HMAC secrets for Gateway, Core, AI,
   and Search, and verify the Core/AI/Search caller key rings use the matching
   values with their reviewed `/inner/**` permissions.
3. Stop all legacy standalone application containers before enabling Core.
   In particular, stop the old Task consumer before Core's RocketMQ consumers
   join the production-equivalent consumer groups.
4. Start AI and confirm it is healthy, then enable Core and its MQ consumers.
   Start Gateway and Search through the approved orchestrator after Core is
   healthy. Keep the old application services scaled to zero.

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

Build backend and frontend first. Use a clean source tree for release candidates.
The release builder rejects dirty Git repositories unless `--allow-dirty` is
explicitly supplied; do not use that override for a deployable candidate.

Example:

```text
python scripts/release/build_release.py \
  --backend-artifacts . \
  --frontend-dist ../codecoch-ai-vue/dist \
  --output-root ../deploy-artifacts/releases \
  --release-id 20260726-001 \
  --backend-repo . \
  --frontend-repo ../codecoch-ai-vue

python scripts/release/verify_release.py \
  ../deploy-artifacts/releases/20260726-001
```

Release layout:

```text
<release-id>/
  backend/
    codecoachai-*.jar
  frontend/
    index.html
    assets/
  release.json
  SHA256SUMS
```

The builder requires exactly one deployable JAR for Gateway, Core, AI, and
Search, validates each JAR as ZIP, copies only frontend `dist`, excludes
historical tar archives, writes source SHAs to `release.json`, and atomically
renames the completed local staging directory.

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

## Upload, Activate, And Roll Back

Every command is dry-run unless `--execute` is present.

Review an upload plan:

```text
python scripts/release/release_transport.py upload \
  --release-dir ../deploy-artifacts/releases/20260726-001
```

Upload only after the plan and release ID are approved:

```text
python scripts/release/release_transport.py upload \
  --release-dir ../deploy-artifacts/releases/20260726-001 \
  --execute \
  --confirm-release-id 20260726-001
```

The upload path is:

```text
<remote-root>/.incoming/<release-id>.<random>
<remote-root>/releases/<release-id>
```

Files are uploaded with SFTP, hashed again through SFTP, checked against
`SHA256SUMS`, and renamed into `releases` only after all checks pass.

Review and perform pointer activation:

```text
python scripts/release/release_transport.py activate \
  --release-id 20260726-001

python scripts/release/release_transport.py activate \
  --release-id 20260726-001 \
  --execute \
  --confirm-release-id 20260726-001
```

Activation updates:

```text
<remote-root>/current  -> releases/20260726-001
<remote-root>/previous -> releases/<former-current>
```

The server must support the SFTP POSIX rename extension; the tool refuses a
non-atomic fallback. Existing service definitions must read JARs and frontend
assets through `<remote-root>/current`. After pointer activation, use the
separately approved orchestrator command to restart/reload services, then run
health and acceptance checks.

Activation and rollback acquire `<remote-root>/.release-pointer.lock` with an
atomic SFTP directory create. A leftover lock means an earlier operation was
interrupted; inspect `current`, `previous`, and the target manifests before
removing that lock manually.

Rollback:

```text
python scripts/release/release_transport.py rollback

python scripts/release/release_transport.py rollback \
  --execute \
  --confirm ROLLBACK
```

Rollback swaps `current` and `previous` after verifying the previous release.
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
