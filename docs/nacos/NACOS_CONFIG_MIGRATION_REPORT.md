# Nacos Config Migration Report

## 1. Migration Goal

Move CodeCoachAI V1 local development runtime configuration from each service `application.yml` into Nacos Config Center while preserving service names, API paths, Gateway route boundaries, and V1 feature scope.

Nacos target:

- Server: `http://127.0.0.1:8848/nacos`
- Namespace: `public`
- Group: `DEFAULT_GROUP`
- Profile: `dev`

## 2. Created Data IDs

| Data ID | Main Contents |
|---|---|
| `codecoachai-common-dev.yml` | Jackson timezone, logging level, MyBatis-Plus logical delete/camel-case config, Knife4j/Springdoc switches, OpenFeign default timeout, actuator health/info exposure. |
| `codecoachai-redis-dev.yml` | Local Redis host, port, password, database, timeout. |
| `codecoachai-gateway-dev.yml` | Gateway port and all V1 external routes, plus Gateway auth white paths and token-info timeout placeholder. |
| `codecoachai-auth-dev.yml` | Auth port, Sa-Token settings, auth token expiry placeholder. |
| `codecoachai-user-dev.yml` | User port, local MySQL datasource, Sa-Token read settings. |
| `codecoachai-question-dev.yml` | Question port, local MySQL datasource, question inner select placeholder. |
| `codecoachai-resume-dev.yml` | Resume port and local MySQL datasource. No upload or MinIO config. |
| `codecoachai-ai-dev.yml` | AI port, local MySQL datasource, V1 mock AI provider/model/base-url/api-key/timeout placeholders. |
| `codecoachai-interview-dev.yml` | Interview port, local MySQL datasource, OpenFeign longer timeout, V1 interview flow parameters. |
| `codecoachai-system-dev.yml` | System port, local MySQL datasource, simple overview enable flag. |

## 3. Local `application.yml` Retained Contents

Each runnable service keeps only minimal bootstrapping configuration:

- `spring.application.name`
- `spring.profiles.active=dev`
- `spring.config.import` entries for its Nacos Data IDs
- `spring.cloud.nacos.discovery` server/group
- `spring.cloud.nacos.config` server/group/file-extension

Service names were not changed:

- `codecoachai-gateway`
- `codecoachai-auth`
- `codecoachai-user`
- `codecoachai-question`
- `codecoachai-resume`
- `codecoachai-ai`
- `codecoachai-interview`
- `codecoachai-system`

## 4. Explicitly Not Migrated To Nacos

The following remain in MySQL or repository files and were not moved to Nacos:

- User, role, and user-role data
- Question, category, tag, group, answer, favorite, wrong-record data
- Resume and project data
- Interview sessions, stages, messages, and reports
- Prompt template records
- AI call logs
- System config table data
- Initialization SQL

## 5. Import Result

Imported with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\nacos\import-nacos-config.ps1
```

Result: all 10 Data IDs imported successfully.

## 6. Build Result

Command:

```powershell
mvn clean package -DskipTests
```

Result: SUCCESS.

## 7. Service Verification

All 8 backend services restarted after migration and logged successful Nacos Config loading. All 8 services registered to Nacos with one healthy instance each.

Verified registered services:

- `codecoachai-gateway`
- `codecoachai-auth`
- `codecoachai-user`
- `codecoachai-question`
- `codecoachai-resume`
- `codecoachai-ai`
- `codecoachai-interview`
- `codecoachai-system`

## 8. Gateway Route Boundary

Gateway routes are loaded from `codecoachai-gateway-dev.yml`.

Confirmed:

- No Gateway `/inner/**` route.
- No user-facing `/ai/**` route.
- AI admin route remains `/admin/ai/**`.
- Internal AI remains service-side `/inner/ai/**`, not exposed through Gateway.

## 9. Minimal API Verification

Through Gateway `http://127.0.0.1:8080`:

| Check | Result |
|---|---|
| `GET /auth/current-user` without token | `41000` unauthenticated, not 500 |
| `POST /auth/login` with `admin/admin123` | success |
| `GET /auth/current-user` with admin token | success |
| `GET /questions` with admin token | success |
| `GET /resumes` with admin token | success |
| `GET /interviews` with admin token | success |
| `GET /admin/roles` with admin token | success |
| `GET /admin/roles` with USER token | `41003` forbidden |
| Direct Gateway `/inner/auth/token-info` | 404 |

## 10. Manual Confirmation Still Needed

- Production values for MySQL, Redis, and AI provider settings are intentionally not defined.
- AI API key is blank in dev config: `api-key: ""`.
- `codecoachai.gateway.auth.*` and `codecoachai.interview.*` are currently documented placeholders unless later code binds them with `@ConfigurationProperties`.

## 11. Production Security Notes

This is a local dev/demo configuration for a portfolio project. Production should not store database passwords, Redis passwords, or AI API keys in plaintext Nacos config. Recommended production options:

- Environment variables
- Nacos encrypted configuration
- Secret manager integration
- Separate namespaces/groups per environment
- Restricted Nacos access control
