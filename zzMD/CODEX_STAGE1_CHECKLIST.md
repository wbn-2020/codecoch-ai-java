# CodeCoachAI Java Stage 1 Checklist

| No. | Check Item | Status | Notes |
| --- | --- | --- | --- |
| 1 | Maven multi-module structure is complete | PASS | Root, common modules, gateway, auth, user, and skeleton services are present. |
| 2 | common-core is complete | PASS | Result, PageResult, ErrorCode, BusinessException, BaseEntity, constants. |
| 3 | common-web is complete | PASS | GlobalExceptionHandler and basic Swagger config. |
| 4 | common-security is complete | PASS | LoginUser, context, header reader, context filter, PasswordEncoder. |
| 5 | common-mybatis is complete | PASS | Pagination interceptor, logic-delete config, auto-fill handler. |
| 6 | common-redis only has base config | PASS | RedisTemplate config, StringRedisTemplate helper, key constants only. |
| 7 | common-feign handles request header propagation | PASS | Propagates internal, service, trace, user, role, and Authorization headers. |
| 8 | gateway has base routes | PASS | Required external routes are configured in application.yml. |
| 9 | gateway does not expose `/inner/**` | PASS | No `/inner/**` route; filter blocks `/inner/` paths. |
| 10 | gateway does not configure `/ai/**` | PASS | Only `/admin/ai/**` is configured. |
| 11 | auth-service implements registration | PASS | `POST /auth/register`. |
| 12 | auth-service implements login | PASS | `POST /auth/login`. |
| 13 | auth-service implements logout | PASS | `POST /auth/logout`. |
| 14 | auth-service implements current-user | PASS | `GET /auth/current-user`. |
| 15 | auth-service calls user-service through OpenFeign | PASS | `UserFeignClient` uses Spring Cloud OpenFeign. |
| 16 | auth-service does not directly access user tables | PASS | No datasource, mapper, or user DB entity dependency in auth-service. |
| 17 | user-service implements SysUser | PASS | `SysUser` entity and mapper exist. |
| 18 | user-service implements SysRole | PASS | `SysRole` entity and mapper exist. |
| 19 | user-service implements SysUserRole | PASS | `SysUserRole` entity and mapper exist. |
| 20 | user-service provides `/inner/users/by-username` | PASS | `GET /inner/users/by-username`. |
| 21 | user-service provides `/inner/users` | PASS | `POST /inner/users`. |
| 22 | user-service provides `/inner/users/{id}/roles` | PASS | `GET /inner/users/{id}/roles`. |
| 23 | user-service provides `/admin/roles` | PASS | `GET /admin/roles`. |
| 24 | system-service does not maintain role relations | PASS | system-service is skeleton only. |
| 25 | question-service is skeleton only | PASS | Startup class and health endpoint only. |
| 26 | resume-service is skeleton only | PASS | Startup class and health endpoint only. |
| 27 | interview-service is skeleton only | PASS | Startup class and health endpoint only. |
| 28 | ai-service is skeleton only | PASS | Startup class and health endpoint only. |
| 29 | system-service is skeleton only | PASS | Startup class and health endpoint only. |
| 30 | No V2/V3 features implemented | PASS | No MQ, MinIO, ES, SSE/WebSocket, payment, enterprise, or complex RBAC. |
| 31 | Old interfaces were not generated | PASS | No `POST /questions/answer`, `GET /questions/wrongs`, or user-facing `/ai/interview/**`. |
| 32 | `mvn clean package -DskipTests` passes | PASS | Build completed successfully. |

## TODO Items

| Item | Status | Notes |
| --- | --- | --- |
| Shared Sa-Token Redis login-state | TODO | Current gateway token parsing is a Stage 1 placeholder. |
| Real `/users/overview` aggregation | TODO | Currently returns zero/default statistics. |
| Internal-call service-side verification | TODO | Gateway blocks frontend `/inner/**`; service-side trusted header checks can be hardened next. |
| Integration tests with MySQL/Nacos/Redis | TODO | Build passes; runtime integration tests should be added after infrastructure is running. |
