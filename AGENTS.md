# AGENTS.md — CodeCoachAI Java Backend

## Project Identity

This repository is the Java backend of CodeCoachAI, an AI interview training platform.

Related repositories:

- Frontend: <https://github.com/wbn-2020/codecoch-ai-vue.git>
- Backend: <https://github.com/wbn-2020/codecoch-ai-java.git>
- Docs: <https://github.com/wbn-2020/codecoch-ai-doc.git>

Current project phase:

- V1 has been completed.
- The current final goal is to implement the full official V2 PRD.
- The current execution route is:
  1. Complete V2 backend first.
  2. Complete V2 frontend after backend contracts are stable.
  3. Run full integration and E2E testing at the end.

Do not treat the previous V2 technical baseline work as the full V2.

## Official V2 Scope Source

The only authoritative source for the official V2 scope is:

`codecoch-ai-doc/PRD/CodeCoachAI_PRD_V2_AI能力增强版.md`

If any of the following conflict with the official V2 PRD, the official V2 PRD wins:

- Old chat history.
- Codex historical output.
- `V2_ROADMAP_DRAFT.md`.
- `PROJECT_STATE.md`.
- Old backend `AGENTS.md` content.
- Previous temporary V2 roadmaps.
- Any local memory or summary that has not been reconciled with the official V2 PRD.

Before planning or implementing a V2 backend task, read the official V2 PRD and align the task scope with it.

## Completed Baseline Work

The following completed items are classified as:

> V2 technical baseline and AI interview enhancement prerequisites.

They are useful foundations, but they are not the full official V2:

- Service-side `ADMIN` secondary checks for `/admin/**`.
- HMAC signature protection for `/inner/**` internal calls.
- AI call stability enhancements.
- Resume project deep-dive capability enhancements.
- Early learning feedback in interview reports.

Do not confuse these prerequisite capabilities with completion of the official V2 PRD.

## Backend Architecture Expectations

Act as a senior Java architect when working in this repository.

Expected stack:

- Java 17.
- Spring Boot 3.
- Spring Cloud / Spring Cloud Alibaba.
- Gateway.
- Nacos.
- OpenFeign.
- MyBatis-Plus.
- MySQL.
- Redis.
- Sa-Token.
- Knife4j / OpenAPI.

Preserve the current microservice and module boundaries. Do not collapse services into a monolith unless the user explicitly requests it.

## Stable V1 Capabilities

V1 completed the core closed loop and must remain stable while V2 is implemented.

User-side V1 capabilities:

- User registration, login, and logout.
- User profile and password management.
- Question browsing and answering.
- Favorites and wrong records.
- Manual resume and project experience management.
- Interview creation.
- Interview room flow.
- AI scoring, follow-up questions, and summary generation.
- Interview report generation and history.

Admin-side V1 capabilities:

- Dashboard.
- User and role management.
- Question, category, tag, and group management.
- Prompt template management.
- AI call log management.
- System configuration.

Do not break existing V1 login, question, resume, interview, report, admin, or AI-log flows while implementing V2.

## V2 Backend Route

The backend V2 route is:

| Stage | Priority | Goal |
|---|---|---|
| A0 | P0 | PRD alignment and backend gap list |
| A1 | P0 | `codecoach-file` / resume file upload |
| A2 | P0 | Resume parse records and parse status flow |
| A3 | P0 | AI structured resume parsing |
| A4 | P0 | AI resume optimization |
| A5 | P0 | Industry templates and industry scenario interviews |
| A6 | P0 | AI question generation and review |
| A7 | P1 | Initial question-bank deduplication |
| A8 | P1 | Formal learning plan module |
| A9 | P1 | Prompt template version management |
| A10 | P1 | SSE streaming output |
| A11 | P0 | Final backend static closure |

Each backend development task must state the following before implementation:

1. Corresponding PRD section.
2. Priority: P0 / P1 / P2.
3. Affected backend services.
4. Affected database tables.
5. Affected API paths.
6. Whether the task affects frontend contracts.
7. Whether the task adds a new module.
8. Whether SQL migration is required.
9. Compile and verification method.

## V2 Scope Guardrails

Follow these boundaries unless the user explicitly changes the scope:

- Do not invent API paths. Use actual controller mappings and the official PRD as the source of truth.
- Do not casually change existing Controller paths.
- Do not delete existing APIs.
- Do not expose `/inner/**` APIs to frontend usage.
- Keep admin APIs under `/admin/**` and user APIs under their existing user-side paths.
- Do not break service-side `/admin/**` permission checks.
- Do not break `/inner/**` HMAC internal-call protection.
- Do not mix frontend implementation into backend tasks.
- Do not run full runtime testing unless the user explicitly asks for it.
- Do not silently change database schema.
- If schema changes are required, add compatible SQL migration or patch SQL and explain the compatibility impact.
- If frontend integration is involved, verify request and response DTOs against frontend API files.
- For AI-related changes, clearly distinguish mock mode and real model mode.

## Security Rules

Preserve permission isolation:

- Normal users cannot access admin APIs.
- Normal users cannot read or modify another user's resumes, interviews, reports, favorites, wrong records, files, parse records, optimization records, or learning plans.
- `/admin/**` must continue to require the `ADMIN` role at the Gateway and service level.
- `/inner/**` must continue to require internal-call protection and must not be used by frontend code.
- Internal security changes must not degrade the existing HMAC signature, timestamp, nonce, and replay-protection behavior.

## Database and Migration Rules

Do not silently modify schema.

When a backend task requires schema changes:

- Prefer compatible additions over destructive changes.
- Preserve old data.
- Add or update migration / patch SQL.
- Keep `init.sql` and development data strategy aligned when relevant.
- Explain affected tables and rollback considerations.

## Build and Verification Rules

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

For documentation-only changes, `git diff --check` is sufficient unless the user asks for compilation.

For service-level checks, only run runtime verification when requested. When runtime verification is requested, confirm:

- Nacos service registration.
- Gateway routing.
- MySQL init data compatibility.
- Redis/token behavior.
- `/admin/**` authorization.
- `/inner/**` HMAC behavior.

## Documentation and Contract Rules

When code changes affect backend behavior or API contracts:

- Update the backend repository documentation when the document lives in this repo.
- If the durable project-level document lives in `codecoch-ai-doc`, tell the user which docs should be updated there.
- Keep user APIs, admin APIs, and `/inner/**` internal APIs clearly separated.
- Do not document unsupported public frontend access to `/inner/**`.

## Required Response Format

When completing a backend task, report:

1. Task conclusion.
2. Modified files.
3. What changed in each file.
4. API impact.
5. Database impact.
6. Frontend impact.
7. Verification performed.
8. Commit ID, if a commit was created.
9. Remaining risks.
10. Next recommended step.
