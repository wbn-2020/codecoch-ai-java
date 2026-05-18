# AGENTS.md - CodeCoachAI Java Backend

## Project Identity

This repository is the Java backend of CodeCoachAI, an AI interview and job-search training platform.

Related repositories:

- Frontend: <https://github.com/wbn-2020/codecoch-ai-vue.git>
- Backend: <https://github.com/wbn-2020/codecoch-ai-java.git>
- Docs: <https://github.com/wbn-2020/codecoch-ai-doc.git>

Backend repository local path:

```text
C:\my-claude\CodeCoachAI-java
```

Act as a senior Java architect when working in this repository. Preserve the current microservice and module boundaries. Do not collapse services into a monolith unless the user explicitly requests it.

## Branch And Stage Rules

Current backend V3 development branch:

```text
dev-v3
```

All subsequent V3 backend development tasks should run on `dev-v3`. After a backend task passes verification, commit and push to:

```text
origin/dev-v3
```

V2 sealed backend baseline:

```text
dev / 29dc14d fix: stabilize AI question generation flow
```

The documentation repository continues to use:

```text
main
```

Current project stage:

```text
V3
```

V3-A planning freeze and consistency correction is complete in the documentation repository. The next backend implementation stage is:

```text
V3-B: Target Job / JD backend foundation
```

Do not treat earlier V2 technical baseline work as the full V3 scope. V1 and V2 completed capabilities must remain stable while V3 is implemented.

## V3 Documentation Baseline

Before planning or implementing a V3 backend task, read the V3 baseline documents from the documentation repository `main` branch.

Primary V3 PRD:

```text
C:\my-claude\CodeCoachAI-doc\V3\CodeCoachAI_PRD_V3_Enhanced.md
```

V3-A supporting planning documents:

```text
C:\my-claude\CodeCoachAI-doc\MD\V3\V3_开发路线图.md
C:\my-claude\CodeCoachAI-doc\MD\V3\V3_数据库设计草案.md
C:\my-claude\CodeCoachAI-doc\MD\V3\V3_API契约草案.md
C:\my-claude\CodeCoachAI-doc\MD\V3\V3_AI_Prompt类型清单.md
C:\my-claude\CodeCoachAI-doc\MD\V3\V3_验收清单.md
```

The frontend page list is also part of the V3-A baseline and should be used when a backend contract affects frontend integration:

```text
C:\my-claude\CodeCoachAI-doc\MD\V3\V3_前端页面清单.md
```

Latest known V3 documentation baseline commit:

```text
916f386c8185e3036db24aa1607e288787c85dcd
```

If local memory, old chat history, previous temporary roadmaps, backend comments, or older `AGENTS.md` content conflict with the V3 documentation baseline, the V3 documentation baseline wins. If the V3 documentation draft conflicts with the real backend module, package, controller, DTO, VO, mapper, SQL migration, or gateway route structure, report the conflict first and implement against the real backend structure unless the user explicitly asks to change the architecture.

## V3 Development Mainline

V3 mainline:

```text
岗位目标 / JD -> 简历-JD 匹配 -> 能力差距画像 -> 学习计划 -> 题目训练 -> 模拟面试 -> 报告回流 -> 求职驾驶舱
```

All new V3 capabilities should serve this job-target-driven closed loop. Do not add technology-only features or empty services that are not tied to a real V3 business workflow.

V3 route:

| Stage | Goal | Backend Rule |
|---|---|---|
| V3-A | Planning freeze and contract split | Completed in docs, no backend code |
| V3-B | Target Job / JD backend foundation | Current next backend stage |
| V3-C | Target Job / JD frontend pages | Frontend stage, backend only fixes contract defects if needed |
| V3-D | Resume-JD match backend | Implement after V3-B is accepted |
| V3-E | Resume-JD match frontend | Frontend stage |
| V3-F | Skill profile and study-plan linkage | Backend/frontend integration stage |
| V3-G | Task center, notification, MQ, MinIO, ES, search center | Engineering enhancement tied to real workflows |
| V3-H | Dashboard, Docker Compose, full-chain testing, seal | Final integration and acceptance |

## V3-B Backend Direction

V3-B should prioritize:

1. `target_job` table.
2. `job_description_analysis` table.
3. `V3_001__xxx.sql` as the first V3 migration.
4. Target job CRUD.
5. Setting the current main target job.
6. JD parsing API.
7. Prompt type `JOB_DESCRIPTION_PARSE`.
8. AI call log integration.
9. Implementation based on real `dev-v3` backend modules, packages, Controller style, DTO/VO style, mapper style, and gateway routes.

Do not add empty shell services, unused controllers, fake APIs, or unregistered modules for V3-B. If the docs suggest a new subdomain but the current backend has a suitable existing service, prefer the existing service unless the user explicitly approves a new module.

V3-B draft API direction from the docs includes:

```text
GET  /job-targets
POST /job-targets
GET  /job-targets/{id}
PUT  /job-targets/{id}
POST /job-targets/{id}/set-current
POST /job-targets/{id}/parse
GET  /job-targets/{id}/analysis
GET  /job-targets/current
```

These paths are draft contracts. Before implementing, scan the real Gateway routes, existing user-side controller mappings, auth conventions, response wrappers, and service ownership.

## Backend Architecture Expectations

Expected stack:

- Java 17.
- Spring Boot 3.
- Spring Cloud / Spring Cloud Alibaba.
- Gateway.
- Nacos.
- OpenFeign.
- MyBatis-Plus.
- MySQL 8.
- Redis.
- Sa-Token.
- Knife4j / OpenAPI.

Existing Maven modules:

```text
codecoachai-common
codecoachai-gateway
codecoachai-auth
codecoachai-user
codecoachai-question
codecoachai-resume
codecoachai-file
codecoachai-interview
codecoachai-ai
codecoachai-system
```

Do not introduce MQ, MinIO, Elasticsearch, Docker Compose, a new service module, or other new infrastructure merely to satisfy a future V3 stage. Add new infrastructure only when the current V3 stage and user task explicitly require it and the implementation has a real business landing point.

## Stable V1 And V2 Capabilities

V1 and V2 completed capabilities must remain stable.

User-side stable capabilities include:

- User registration, login, logout, profile, and password management.
- Question browsing, answering, favorites, wrong records, and practice review.
- Manual resume and project experience management.
- Resume upload, parsing, confirmation, optimization, optimization records, and apply-as-draft flow.
- Industry templates and scenario interviews.
- Interview creation, room flow, AI scoring, follow-up questions, SSE review, report generation, and report history.
- Learning plan generation, daily view, planned date, task status updates, and study-plan SSE.
- User dashboard/status data.

Admin-side stable capabilities include:

- Dashboard.
- User and role management.
- Question, category, tag, and group management.
- AI question generation, review pool, batch review, duplicate review, and question relations.
- Prompt template management, prompt versioning, prompt testing, rollback alias, and prompt call logs.
- AI call log management.
- System configuration.
- Admin file governance.

Do not break existing V1 or V2 login, question, resume, interview, report, study-plan, dashboard, admin, prompt, file, or AI-log flows while implementing V3.

## Required Pre-Implementation Statement

Each backend development task must state the following before implementation:

1. Corresponding V3 PRD and V3-A planning scope.
2. Priority: P0 / P1 / P2.
3. Current V3 stage, such as V3-B.
4. Affected backend services/modules.
5. Affected database tables.
6. Affected API paths.
7. Whether frontend contracts are affected.
8. Whether a new module is required; default answer should be no unless justified by the real codebase and approved scope.
9. Whether SQL migration is required.
10. Compile, SQL, API, and verification method.

## Implementation Rules

Before each backend implementation, statically scan the real code first:

- Controller mappings and response wrapper style.
- Service interfaces and implementation style.
- Mapper and XML or MyBatis-Plus conventions.
- DTO, VO, entity, enum, and validation conventions.
- Feign client and `/inner/**` usage.
- Gateway route and auth filter behavior when paths change.
- Existing SQL migration helper procedures and idempotency style.

Use existing patterns before introducing abstractions. Controller methods should stay thin. Business rules belong in services. DTO and VO boundaries should remain explicit.

Do not:

- Skip tests or verification and claim the task is complete.
- Use mock data or mock responses to pretend a real backend interface is complete.
- Add empty services, empty controllers, empty interfaces, or unregistered modules for V3.
- Silently change existing Controller paths.
- Delete existing APIs.
- Expose `/inner/**` APIs to frontend usage.
- Mix frontend implementation into backend tasks unless the user explicitly enters a frontend phase.
- Generate result documents or extra documentation files unless explicitly requested.
- Run full runtime integration or E2E testing unless explicitly requested.

## API And Security Rules

Preserve permission isolation:

- Normal users cannot access admin APIs.
- Normal users cannot read or modify another user's resumes, interviews, reports, favorites, wrong records, files, parse records, optimization records, learning plans, target jobs, JD analyses, match reports, skill profiles, tasks, or notifications.
- `/admin/**` must continue to require the `ADMIN` role at the Gateway and service level.
- `/inner/**` must continue to require internal-call protection and must not be used by frontend code.
- Internal security changes must not degrade existing HMAC signature, timestamp, nonce, and replay-protection behavior.

Keep API ownership clear:

- User APIs should stay on user-facing business routes.
- Admin APIs must stay under `/admin/**`.
- Internal service APIs must stay under `/inner/**`.
- AI SSE APIs, when explicitly required, should follow existing `/ai/sse/**` conventions and preserve synchronous fallback paths unless the user asks for a breaking change.

## SQL And Migration Rules

Do not silently modify schema.

V3 SQL rules:

1. V3 migrations start at:

```text
sql/migration/V3_001__xxx.sql
```

2. All SQL migrations must be idempotent.
3. Use `information_schema` or the project's existing helper procedures to check whether tables, columns, indexes, and seed rows already exist.
4. Prefer compatible additions over destructive changes.
5. Preserve old data.
6. Do not require developers to manually drop tables or columns.
7. Keep `sql/init.sql` and development data strategy aligned when relevant.
8. Explain affected tables and rollback considerations.
9. Tasks involving SQL must execute database verification.
10. Tasks involving SQL must execute migration repeat-run verification.
11. Database account and password may only be temporarily provided by the user in the current conversation window.
12. Do not hardcode database passwords in code, SQL, docs, logs, scripts, or memory.

For SQL tasks, verify at minimum:

- Migration applies successfully.
- Re-running the migration succeeds without duplicate table, column, index, or data errors.
- Expected tables, columns, indexes, and seed data exist after execution.
- Existing V1/V2 tables and critical flows are not destructively changed.

## Sensitive Configuration And Runtime Files

Never commit real secrets:

- Real database passwords.
- AI API keys.
- MinIO access keys or secret keys.
- MQ passwords.
- Tokens, session keys, or private credentials.

Use example configuration files or private local/Nacos configuration for secrets. If a command must print sensitive values, redact them before reporting.

Runtime upload/storage directories must not be committed. In this repository, treat the following as runtime data:

```text
codecoachai-file/data/
```

If `.gitignore` does not cover a runtime directory, remind the user. Do not modify `.gitignore` in a scoped task unless the user explicitly asks for it.

## Build And Verification Rules

Use actual project scripts or commands if they exist. Otherwise prefer:

```bash
mvn clean compile
mvn clean package -DskipTests
```

After every backend code change, run at least:

```bash
mvn clean compile
git diff --check
```

For documentation-only backend repository changes, `git diff --check` is sufficient unless the user asks for compilation. If the user explicitly requests compilation for a documentation-only change, run it.

For SQL changes, also run database verification and migration repeat-run verification.

Before committing:

1. Run `git status --short`.
2. Confirm no runtime files are staged.
3. Confirm no sensitive configuration is staged.
4. Run `git diff --check`.
5. Run `mvn clean compile` for backend tasks or when requested.
6. If SQL changed, run database verification and repeat migration verification.
7. Stage only intended files.
8. Commit with a clear message.
9. Push to `origin/dev-v3`.

For service-level runtime checks, only run runtime verification when requested. When runtime verification is requested, confirm:

- Nacos service registration.
- Gateway routing.
- MySQL init data compatibility.
- Redis/token behavior.
- `/admin/**` authorization.
- `/inner/**` HMAC behavior.

## Documentation And Contract Rules

When code changes affect backend behavior or API contracts:

- Update backend repository documentation only when the document lives in this repo and the task scope allows it.
- If the durable project-level document lives in `codecoch-ai-doc`, tell the user which docs should be updated there.
- Keep user APIs, admin APIs, and `/inner/**` internal APIs clearly separated.
- Do not document unsupported public frontend access to `/inner/**`.
- Do not generate new result documents unless explicitly requested.

If API contracts, table structure, module ownership, or implementation feasibility differ from the V3-A draft documents, stop and report the exact conflict before coding beyond the safe portion.

## Codex Behavior Rules

Codex must:

1. Respect the user's scope fences such as backend-only, docs-only, SQL-only, or no-business-code.
2. Stop and report if the worktree has unrelated user changes that could be overwritten.
3. Avoid reverting user changes unless explicitly requested.
4. Read the official V3 docs before V3 planning or implementation.
5. Read the real backend code before proposing paths, services, tables, or DTO names.
6. Prefer real API data, explicit empty states, or clear unsupported status over fake data.
7. Clearly distinguish mock mode from real model mode for AI-related changes.
8. Clearly report anything not verified.
9. Never claim browser, runtime, DB, or integration verification happened unless it actually happened.

## Required Response Format

When completing a backend task, report:

1. Task conclusion.
2. Current directory.
3. Current branch.
4. Modified files.
5. What changed in each file.
6. API impact.
7. Database impact.
8. Frontend impact.
9. Verification performed.
10. Commit ID, if a commit was created.
11. Push result, if pushed.
12. Remaining risks.
13. Next recommended step.
