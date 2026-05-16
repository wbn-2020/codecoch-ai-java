# AGENTS.md - CodeCoachAI Java Backend

## Project Identity

This repository is the Java backend of CodeCoachAI, an AI interview training platform.

Related repositories:

- Frontend: <https://github.com/wbn-2020/codecoch-ai-vue.git>
- Backend: <https://github.com/wbn-2020/codecoch-ai-java.git>
- Docs: <https://github.com/wbn-2020/codecoch-ai-doc.git>

Current execution strategy:

1. Complete the V2 backend first.
2. Complete the V2 frontend after backend contracts are stable.
3. Run full integration and E2E testing at the end.

V1 has been completed and must remain stable. Do not treat previous V2 technical baseline work as the full official V2.

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

The following completed items are classified as V2 technical baseline and AI interview enhancement prerequisites. They are useful foundations, but they are not the full official V2:

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

| Stage | Priority | Goal | Status |
|---|---|---|---|
| A0 | P0 | PRD alignment and backend gap list | Completed |
| A1 | P0 | Resume file upload minimal loop | Completed |
| A2 | P0 | Resume parse status and retry state machine | Completed |
| A3 | P0 | Resume text extraction and AI structured parsing | Completed |
| A4 | P0 | Resume analysis result viewing and confirmation | Completed |
| A5 | P0 | AI resume optimization | Completed |
| A6 | P0 | Industry templates and scenario interviews | Completed |
| A7 | P0 | AI question generation and review pool | Completed |
| A8 | P1 | Initial question-bank deduplication | Completed |
| A9 | P1 | Learning plan backend loop | Completed |
| A10 | P1 | SSE streaming output | Current |
| A11 | P1 | Prompt template versioning and AI log enhancement | Pending |

Each backend development task must state the following before implementation:

1. Corresponding PRD scope.
2. Priority: P0 / P1 / P2.
3. Affected backend services.
4. Affected database tables.
5. Affected API paths.
6. Whether the task affects frontend contracts.
7. Whether the task adds a new module.
8. Whether SQL migration is required.
9. Compile and verification method.

## Current V2 Backend Progress

### A0: V2 PRD Alignment And Backend Gap List - Completed

### A1: Resume File Upload Minimal Loop - Completed

Key points:

- `POST /resumes/upload`.
- Save file information.
- Create `resume_analysis_record` with status `PENDING`.
- Do not perform AI parsing in A1.

### A2: Resume Parse Status, Retry, And State Machine - Completed

Key points:

- `GET /resumes/{id}/parse-status`.
- `POST /resumes/{id}/reparse`.
- `{id}` is `resume_analysis_record.id`.
- Statuses include `PENDING`, `PARSING`, `SUCCESS`, `FAILED`, and `WAIT_CONFIRM`.
- `reparse` only allows `FAILED -> PENDING`.

### A3: Resume Text Extraction And AI Structured Parsing - Completed

Key points:

- `file-service` internal file download.
- `resume-service` text extraction.
- `ai-service` resume structured parsing.
- State flow: `PENDING -> PARSING -> WAIT_CONFIRM`.
- Failure enters `FAILED`.
- Successful parsing terminal state is `WAIT_CONFIRM`, not `SUCCESS`.

### A4: Resume Analysis Result Viewing And Confirmation - Completed

Key points:

- `GET /resumes/{id}/analysis-result`.
- `POST /resumes/{id}/confirm-analysis`.
- `{id}` is still `resume_analysis_record.id`.
- Only `WAIT_CONFIRM` can be confirmed.
- `structured_json` is converted into formal `resume` / `resume_project` data.
- After confirmation, `parse_status = SUCCESS`.
- Backfill `resume_id`.

### A5: AI Resume Optimization - Completed

Key points:

- `POST /resumes/{id}/optimize`.
- `GET /resumes/{id}/optimize-records`.
- `GET /resumes/optimize-records/{recordId}`.
- `{id}` is formal `resume.id`.
- Adds `resume_optimize_record`.
- `ai-service` adds `/inner/ai/resume/optimize`.
- AI output must be a JSON object.
- Record `ai_call_log`.
- Do not fabricate experience, companies, education, years, responsibilities, or achievements.

### A6: Industry Templates And Scenario Interviews - Completed

Key points:

- Adds `industry_template`.
- `interview_session` adds `industry_template_id` / `industry_context`.
- Admin industry template CRUD.
- User-side industry template read-only APIs.
- Interview creation supports `industryTemplateId`.
- `generateQuestion`, `generateFollowUp`, `evaluate`, and `generateReport` prompts inject `industryContext`.
- Industry templates are scenario references only and must not fabricate candidate experience.

### A7: AI Question Generation And Review - Completed

Key points:

- Admin starts AI question generation.
- `ai-service` adds `/inner/ai/questions/generate`.
- AI generated questions enter the `question_review` review pool first.
- Only approved questions are written into formal `question`.
- Rejected questions are not written into formal `question`.
- `approve` supports edit-before-approve.
- `approve` uses `id + review_status=PENDING` concurrency protection.
- Question-bank deduplication is not part of A7; it belongs to A8.

### A8: Initial Question-Bank Deduplication - Completed

Key points:

- Adds `question_duplicate_review`.
- Adds `question_relation`.
- Supports rule-based duplicate candidate detection.
- Supports candidate list, detail, `merge`, and `ignore`.
- Supports question relation query, creation, and logical delete.
- A7 `approve` may trigger duplicate detection non-blockingly.
- `createQuestion` may trigger duplicate detection non-blockingly.
- Does not call `ai-service`.
- Does not use ES, vector databases, or embedding.
- `merge` is not physical question merging.
- Does not delete formal `question` records.
- Does not automatically modify `question` content.

### A9: Learning Plan Backend Loop - Completed

Key points:

- Adds `study_plan`.
- Adds `study_task`.
- Users can generate a study plan from an interview report.
- Supports study plan list, detail, and task list.
- Supports regenerate after `FAILED`.
- Supports study task status updates.
- `ai-service` adds `POST /inner/ai/learning-plans/generate`.
- `mockEnabled=true` returns stable mock JSON.
- `mockEnabled=false` reuses `AiClient.chat(prompt)`.
- Records `ai_call_log`.
- Does not implement frontend.
- Does not implement SSE.
- Does not implement a complex recommendation system.

### Current Stage: A10 SSE Streaming Output - Ready To Plan / Implement

A10 boundaries:

- Only implement backend SSE streaming output.
- Do not implement frontend.
- Do not use WebSocket.
- Do not replace existing synchronous APIs.
- Do not break A1-A9 completed flows.
- Do not implement A11 Prompt template version management.
- Do not introduce MQ, ES, MinIO, Seata, Redis distributed locks, vector databases, or embedding.
- Prefer adding stream-version APIs instead of changing old APIs.
- Must evaluate Gateway, authentication filters, global response wrapping, and whether `AiClient` supports real streaming.

### A11: Prompt Template Version Management And AI Log Enhancement - Pending

## A10 SSE Streaming Guardrails

1. A10 only implements SSE. Do not use WebSocket.
2. Do not replace existing synchronous APIs. Prefer adding stream-version APIs.
3. Confirm whether `AiClient` supports real streaming before implementation.
4. If `AiClient` does not support real streaming, only implement a synchronous-result chunked SSE fallback and clearly mark it as not strict real streaming.
5. Check whether Gateway supports `text/event-stream`.
6. Check whether global response wrapping breaks SSE.
7. Check whether authentication filters and token propagation support long-lived connections.
8. Recommended SSE event names:
   - `start`
   - `delta`
   - `metadata`
   - `done`
   - `error`
   - `heartbeat` optional
9. A10 minimal loop should prioritize one interview-module AI output interface.
10. Do not convert report and study-plan flows to SSE all at once.
11. Do not implement A11 Prompt template version management.
12. Do not implement frontend.
13. Do not run full runtime integration or E2E testing unless explicitly requested.

## V2 Scope Guardrails

Follow these boundaries unless the user explicitly changes the scope:

- Do not invent API paths. Use actual controller mappings and the official PRD as the source of truth.
- Do not casually change existing Controller paths.
- Do not delete existing APIs.
- Do not expose `/inner/**` APIs to frontend usage.
- Keep admin APIs under `/admin/**` and user APIs under their existing user-side paths.
- Do not break service-side `/admin/**` permission checks.
- Do not break `/inner/**` HMAC internal-call protection.
- Do not mix frontend implementation into backend tasks unless the user explicitly enters the frontend phase.
- Do not run full runtime testing unless the user explicitly asks for it.
- Do not silently change database schema.
- If schema changes are required, add compatible SQL migration or patch SQL and explain the compatibility impact.
- If frontend integration is involved, verify request and response DTOs against frontend API files.
- For AI-related changes, clearly distinguish mock mode and real model mode.
- Do not casually introduce MQ, ES, MinIO, Seata, Redis distributed locks, vector databases, embedding, or other new infrastructure unless the official V2 PRD explicitly requires it.

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
