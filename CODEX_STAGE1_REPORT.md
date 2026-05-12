# CodeCoachAI Java Stage 1 Report

## 1. Task Goal

Stage 1 builds the V1 backend foundation only:

- Maven multi-module project skeleton.
- Common modules for core response, web exception handling, security context, MyBatis-Plus, Redis, Spring Cloud OpenFeign, logging, and AI placeholder.
- Gateway basic external routing and request filtering.
- Auth service registration, login, logout, current-user, and optional refresh-token placeholder.
- User service user, role, and user-role foundation.
- Question, resume, interview, ai, and system services remain skeleton services only.

The intended chain is:

Frontend -> Gateway -> auth-service -> Spring Cloud OpenFeign -> user-service -> sys_user / sys_role / sys_user_role.

## 2. Modules Created Or Modified

| Module | Status | Notes |
| --- | --- | --- |
| root pom | DONE | Defines Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.1, Spring Cloud Alibaba, MyBatis-Plus, Sa-Token, Knife4j versions. |
| codecoachai-common | DONE | Aggregates common-core, common-web, common-security, common-mybatis, common-redis, common-feign, common-log, common-ai. |
| common-core | DONE | Unified Result, PageResult, ErrorCode, BusinessException, BaseEntity, constants. |
| common-web | DONE | GlobalExceptionHandler and base Swagger/Knife4j config. |
| common-security | DONE | LoginUser, LoginUserContext, header reader, servlet context filter, PasswordEncoder. |
| common-mybatis | DONE | MyBatis-Plus pagination interceptor and auto fill handler. |
| common-redis | DONE | Redis key constants, StringRedisTemplate helper, RedisTemplate base config. |
| common-feign | DONE | Spring Cloud OpenFeign request interceptor and Result unwrap utility. |
| common-log | DONE | TraceId utility placeholder. |
| common-ai | SKELETON | AI common placeholder only; no AI integration logic. |
| codecoachai-gateway | DONE | Spring Cloud Gateway app, Nacos discovery, CORS config, route config, basic auth/internal blocking filter. |
| codecoachai-auth | DONE | Register, login, logout, current-user, refresh-token placeholder; calls user-service through Spring Cloud OpenFeign. |
| codecoachai-user | DONE | User/role/user-role entities, mappers, services, VO/DTO separation, external and internal controllers. |
| codecoachai-question | SKELETON | App and health endpoint only. |
| codecoachai-resume | SKELETON | App and health endpoint only. |
| codecoachai-interview | SKELETON | App and health endpoint only. |
| codecoachai-ai | SKELETON | App and health endpoint only; no /ai/** user route and no /inner/ai/** implementation in Stage 1. |
| codecoachai-system | SKELETON | App and health endpoint only; does not maintain roles or user-role relations. |
| sql/init.sql | DONE | Minimal sys_user, sys_role, sys_user_role schema and default USER/ADMIN/admin seed data. |

## 3. Key Files

### Root

- `pom.xml`
- `sql/init.sql`
- `CODEX_STAGE1_REPORT.md`
- `CODEX_STAGE1_CHECKLIST.md`

### common-core

- `Result.java`
- `PageResult.java`
- `ErrorCode.java`
- `BusinessException.java`
- `BaseEntity.java`
- `CommonConstants.java`
- `SecurityConstants.java`
- `HeaderConstants.java`

### common-web

- `GlobalExceptionHandler.java`
- `SwaggerConfig.java`

### common-security

- `LoginUser.java`
- `LoginUserContext.java`
- `HeaderUserContextReader.java`
- `LoginUserContextFilter.java`
- `CommonSecurityAutoConfiguration.java`

### common-mybatis

- `MybatisPlusConfig.java`
- `MybatisPlusMetaObjectHandler.java`

### common-redis

- `RedisTemplateConfig.java`
- `RedisCacheHelper.java`
- `RedisKeyConstants.java`

### common-feign

- `OpenFeignConfig.java`
- `FeignResultUtils.java`

### gateway

- `GatewayApplication.java`
- `application.yml`
- `CorsConfig.java`
- `AuthGatewayFilter.java`

### auth-service

- `AuthApplication.java`
- `AuthController.java`
- `AuthService.java`
- `AuthServiceImpl.java`
- `UserFeignClient.java`
- `RegisterDTO.java`
- `LoginDTO.java`
- `LoginVO.java`
- `CurrentUserVO.java`

### user-service

- `UserApplication.java`
- `UserController.java`
- `AdminUserController.java`
- `InnerUserController.java`
- `UserService.java`
- `UserServiceImpl.java`
- `RoleService.java`
- `RoleServiceImpl.java`
- `SysUser.java`
- `SysRole.java`
- `SysUserRole.java`
- `SysUserMapper.java`
- `SysRoleMapper.java`
- `SysUserRoleMapper.java`
- DTO/VO/convert classes under `domain` and `convert`.

## 4. Implemented Interfaces

### auth-service

| Method | Path | Status | Notes |
| --- | --- | --- | --- |
| POST | `/auth/register` | DONE | Encodes password and calls user-service `POST /inner/users`. |
| POST | `/auth/login` | DONE | Calls user-service, checks password/status/roles, returns token and user info. |
| POST | `/auth/logout` | DONE | Clears Sa-Token login state if present. |
| GET | `/auth/current-user` | DONE | Resolves current user from gateway headers or Sa-Token login id, then calls user-service. |
| POST | `/auth/refresh-token` | PLACEHOLDER | Returns BusinessException; V1 optional and not part of core first integration. |

### user-service external

| Method | Path | Status | Notes |
| --- | --- | --- | --- |
| GET | `/users/profile` | DONE | Returns current user profile VO. |
| PUT | `/users/profile` | DONE | Updates nickname/avatar/email. |
| PUT | `/users/password` | DONE | Validates old password and updates encoded password. |
| GET | `/users/overview` | SIMPLIFIED | Returns default zero statistics; later can aggregate question/resume/interview via internal APIs. |
| GET | `/admin/users` | DONE | Admin-only simplified page query. |
| PUT | `/admin/users/{id}/status` | DONE | Admin-only enable/disable user with self-disable guard. |
| GET | `/admin/roles` | DONE | Returns USER and ADMIN role records from user-service. |

### user-service internal

| Method | Path | Status | Notes |
| --- | --- | --- | --- |
| GET | `/inner/users/by-username` | DONE | Used by auth-service login. |
| POST | `/inner/users` | DONE | Used by auth-service register; binds USER role by default. |
| GET | `/inner/users/{id}/roles` | DONE | Used by auth-service role lookup. |
| GET | `/inner/users/{id}` | DONE | Internal basic user query without password hash. |

### skeleton services

| Service | Path | Status |
| --- | --- | --- |
| question-service | `GET /health` | SKELETON ONLY |
| resume-service | `GET /health` | SKELETON ONLY |
| interview-service | `GET /health` | SKELETON ONLY |
| ai-service | `GET /health` | SKELETON ONLY |
| system-service | `GET /health` | SKELETON ONLY |

## 5. Reserved Or Simplified Interfaces

- `POST /auth/refresh-token`: placeholder only.
- `GET /users/overview`: simplified zero-value statistics.
- Gateway token verification: simplified token/header propagation until shared Sa-Token Redis login-state is wired.
- Redis login-state: config and helper are present, but full login-state sharing is a later task.
- Question/resume/interview/ai/system business APIs: intentionally not implemented in Stage 1.

## 6. Gateway Routes

| Path | Target Service |
| --- | --- |
| `/auth/**` | `lb://codecoachai-auth` |
| `/users/**` | `lb://codecoachai-user` |
| `/admin/users/**` | `lb://codecoachai-user` |
| `/admin/roles` | `lb://codecoachai-user` |
| `/questions/**` | `lb://codecoachai-question` |
| `/admin/questions/**` | `lb://codecoachai-question` |
| `/admin/question-categories/**` | `lb://codecoachai-question` |
| `/admin/question-tags/**` | `lb://codecoachai-question` |
| `/admin/question-groups/**` | `lb://codecoachai-question` |
| `/resumes/**` | `lb://codecoachai-resume` |
| `/interviews/**` | `lb://codecoachai-interview` |
| `/admin/ai/**` | `lb://codecoachai-ai` |
| `/admin/system/**` | `lb://codecoachai-system` |
| `/admin/configs/**` | `lb://codecoachai-system` |

## 7. Boundary Confirmations

| Check | Result |
| --- | --- |
| Gateway exposes `/inner/**` | NO. `AuthGatewayFilter` blocks `/inner/` and no route is configured for `/inner/**`. |
| User-facing `/ai/**` route exists | NO. Only `/admin/ai/**` exists in Gateway. |
| Old question/AI interfaces generated | NO. No `POST /questions/answer`, `GET /questions/wrongs`, or user-facing `/ai/interview/**` endpoints were generated. |
| auth-service directly accesses user tables | NO. auth-service has no datasource, mapper, or user table entity dependency; it calls user-service through Spring Cloud OpenFeign. |
| user-service owns sys_user/sys_role/sys_user_role | YES. Entities, mappers, services, and SQL are under user-service. |
| system-service maintains role relations | NO. system-service is skeleton only. |

## 8. Build Result

Command:

```bash
mvn clean package -DskipTests
```

Result:

```text
BUILD SUCCESS
```

Reactor modules from root through `codecoachai-system` all built successfully.

## 9. Current TODO

- Replace temporary gateway token parsing with shared Sa-Token verification backed by Redis.
- Wire Sa-Token Redis login-state if the deployment chooses distributed login sessions.
- Add real integration tests for auth registration/login and user internal APIs after local MySQL/Nacos/Redis are available.
- Implement real aggregation for `GET /users/overview` in a later phase through internal service APIs.
- Add inner-call hardening at service layer, for example checking `X-Internal-Call` and trusted service name.

## 10. Next Stage Suggestions

1. Start Nacos, MySQL, and Redis locally and run the auth-user chain manually through Gateway.
2. Import `sql/init.sql`, then verify admin login and normal user registration.
3. Add focused integration tests for auth-service and user-service.
4. Proceed to V1 question/resume/interview core APIs only after the auth-user base chain is stable.
