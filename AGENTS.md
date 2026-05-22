# AGENTS.md - CodeCoachAI Java Backend

## Project Identity

This repository is the Java backend of CodeCoachAI, an AI interview and job-search training platform.

Local repositories:

- Backend: `C:\my-claude\CodeCoachAI-java`
- Frontend: `C:\my-claude\CodeCoachAI-vue`
- Documentation: `C:\my-claude\CodeCoachAI-doc`

Act as a senior Java architect when working in this repository. Preserve the current microservice and module boundaries. Do not collapse services into a monolith unless the user explicitly requests it.

## Current Branch And Stage

Current backend branch:

```text
dev-v4
```

Current product stage:

```text
V4
```

V4 positioning:

```text
Personal JobCoachAgent + long-term growth profile + BI analytics + AI engineering observability
```

Primary current implementation stage:

```text
V4-A: JobCoachAgent MVP
```

V4-A scope is frozen as:

```text
Manual daily plan generation + agent_run/agent_task persistence + complete/skip tasks + Agent run detail
```

V4-A does not implement automatic daily scheduled plans, daily review, `agent_review`, long-term memory, RAG, resume delivery automation, complex BI, or multi-agent orchestration. Those are later V4 stages.

## V4 Documentation Baseline

Before planning or implementing V4 work, read the V4 documents from:

```text
C:\my-claude\CodeCoachAI-doc\MD\V4
```

Key documents:

```text
C:\my-claude\CodeCoachAI-doc\MD\V4\CodeCoachAI_PRD_V4_个人求职智能体与数据分析增强版.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4_开发路线图.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4-A\V4-A_技术设计.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4-A\V4-A_数据库设计.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4-A\V4-A_API契约.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4-A\V4-A_Prompt设计.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4-A\V4-A_开发任务拆解.md
C:\my-claude\CodeCoachAI-doc\MD\V4\V4-C\V4-C-1_基础BI看板_预研.md
```

If the PRD labels daily review, `agent_review`, or automatic daily plan as P0, the V4 roadmap wins for V4-A: these are reserved for V4-B, not coded in V4-A.

If documentation conflicts with the real backend structure, report the conflict and implement against the real backend structure unless the user explicitly asks to change architecture.

## V4 Roadmap

Recommended V4 delivery order:

| Stage | Goal | Rule |
|---|---|---|
| V4-A | JobCoachAgent MVP | Current priority. Build a controlled, explainable Agent workflow. |
| V4-C-1 | Basic BI dashboard | May follow V4-A using `agent_run`, `agent_task`, and `ai_call_log`. |
| V4-B | Daily review and growth profile | Add automatic plan, daily review, growth snapshots. |
| V4-C-2 | Full BI + AI Ops | Complete metric dictionary, snapshots, prompt quality analytics. |
| V4-D | Resume versions and job-search progress | Resume versioning, diff, rollback, application progress. |
| V4-E | Long-term memory and personal knowledge base | Memory, preferences, notes, RAG. |

Do not mix later-stage work into V4-A unless the user explicitly changes scope.

## Backend Architecture Expectations

Expected stack:

- Java 17
- Spring Boot 3
- Spring Cloud / Spring Cloud Alibaba
- Gateway
- Nacos
- OpenFeign
- MyBatis-Plus
- MySQL 8
- Redis
- Sa-Token via gateway/header context
- Knife4j / OpenAPI

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
codecoachai-task
codecoachai-search
```

For V4-A, prefer implementing the Agent orchestration inside the existing `codecoachai-ai` module under an `agent` package unless a real architectural need justifies a new service. Do not add an empty `codecoachai-agent` module.

## Stable Existing Capabilities

V1, V2, and V3 capabilities must remain stable:

- Login, logout, token persistence, user profile, password management.
- Question browsing, answering, favorites, wrong records, practice review, question recommendations.
- Resume management, upload, parsing, confirmation, optimization, optimization records, and apply-as-draft flow.
- Target job, JD analysis, resume-JD match, skill profile, study-plan linkage.
- Industry templates, scenario interviews, interview room, AI scoring, follow-up questions, report generation, report history.
- Learning plans, daily study tasks, planned dates, task status updates, check-ins.
- User dashboards and V3 job-search cockpit views.
- Admin user/role/menu, question management, prompt management, AI logs, model config, file governance, system config, operation/login logs, async task governance.

Do not break existing routes, response fields, auth behavior, migrations, or frontend contracts while implementing V4.

## V4-A Backend Direction

V4-A should prioritize:

1. `agent_run` table.
2. `agent_task` table.
3. `V4_001__create_agent_run_and_agent_task.sql`.
4. JobCoachAgent controlled workflow.
5. `AgentContextBuilder` with summarized context only.
6. `CandidateTaskBuilder` to constrain model output.
7. `JOB_COACH_DAILY_PLAN` prompt.
8. AI call through `AiCallLogService.callAndLog`, not raw model calls.
9. JSON parsing and validation before persistence.
10. User APIs for daily plan, tasks, complete/skip, run detail.
11. Admin APIs for run/task diagnostics.

Draft V4-A user API paths:

```text
POST /agent/job-coach/daily-plan/generate
GET  /agent/job-coach/daily-plan/latest
GET  /agent/tasks/today
GET  /agent/tasks
POST /agent/tasks/{id}/complete
POST /agent/tasks/{id}/skip
GET  /agent/runs/{id}
```

Draft V4-A admin API paths:

```text
GET /admin/agent/runs
GET /admin/agent/tasks
```

Optional V4-A APIs such as task start/restore should be implemented only if the current task explicitly includes them.

## Required Pre-Implementation Statement

Before each backend implementation, state:

1. Corresponding V4 document and stage.
2. Priority: P0 / P1 / P2.
3. Current V4 stage, such as V4-A.
4. Affected backend modules.
5. Affected database tables.
6. Affected API paths.
7. Whether frontend contracts are affected.
8. Whether a new module is required; default should be no.
9. Whether SQL migration is required.
10. Compile, SQL, API, and verification method.

## Implementation Rules

Before coding, statically scan the real code:

- Controller mappings and `Result` / `PageResult` style.
- Service interface and implementation style.
- Mapper and MyBatis-Plus conventions.
- DTO, VO, entity, enum, validation conventions.
- Existing AI call, prompt render, AI call log, and model routing patterns.
- Feign client and `/inner/**` usage.
- Gateway route and auth behavior for new paths.
- Existing SQL migration idempotency style.

Use existing patterns before introducing abstractions. Controller methods should stay thin. Business rules belong in services. DTO and VO boundaries must remain explicit.

Do not:

- Use mock data or mock responses to pretend a real backend interface is complete.
- Return fake AI plans when the model call fails.
- Persist invalid AI output as tasks.
- Add empty services, empty controllers, or unregistered modules.
- Silently change existing Controller paths.
- Delete existing APIs.
- Expose `/inner/**` APIs to frontend usage.
- Hardcode database passwords, AI keys, tokens, or any other secrets.
- Generate result documents unless explicitly requested.

## API And Security Rules

Preserve permission isolation:

- Normal users cannot access admin APIs.
- Normal users cannot read or modify another user's `agent_run` or `agent_task`.
- `/admin/**` must require admin role at gateway and service level.
- `/inner/**` must remain internal-only.

V4-A user APIs should use `SecurityAssert.requireLoginUserId()`.

V4-A admin APIs should use `SecurityAssert.requireAdmin()`.

Do not expose complete resume text, complete JD text, API keys, tokens, phone numbers, emails, or other sensitive raw content through Agent run detail.

## AI Rules

V4-A Agent is a controlled workflow Agent, not a free chat bot.

Rules:

- LLM may analyze, rank, explain, and produce structured daily plans.
- Backend owns context aggregation, candidate task generation, parsing, validation, and persistence.
- Prompt input must use summarized context, not complete resume/JD/private raw text.
- AI calls must use the existing logging/model routing path, preferably `AiCallLogService.callAndLog`.
- AI output must be JSON parsed and validated.
- AI failure returns a clear failure state and retry opportunity, not a mock plan.
- Store enough `agent_run` metadata for diagnostics: status, prompt type, model, trace id, AI log id, token counts, duration, error code/message.

## SQL And Migration Rules

Do not silently modify schema.

V4 SQL migrations should start at:

```text
sql/migration/V4_001__xxx.sql
```

Rules:

1. All SQL migrations must be idempotent.
2. Use `CREATE TABLE IF NOT EXISTS`, `information_schema`, or existing helper procedures where needed.
3. Prefer compatible additions over destructive changes.
4. Preserve old data.
5. Do not require developers to manually drop tables or columns.
6. Keep `sql/init.sql` and development data aligned only when relevant.
7. Explain affected tables and rollback considerations.
8. Run database verification when SQL changes.
9. Run migration repeat-run verification when SQL changes.
10. Database credentials may only be used locally in the current conversation and must never be written into code, SQL, docs, logs, scripts, or committed files.

Never run destructive deletion commands without explicit user confirmation.

## Build And Verification Rules

After backend code changes, run at least:

```text
mvn clean compile
git diff --check
```

For documentation-only or AGENTS-only changes, `git diff --check` is sufficient unless the user asks for compilation.

For SQL changes, also verify:

- Migration applies successfully.
- Re-running the migration succeeds.
- Expected tables, columns, indexes, and seed rows exist.
- Existing critical tables are not destructively changed.

Do not claim browser, runtime, database, or integration verification happened unless it actually happened.

## Git Rules

You may be in a dirty worktree. Never revert unrelated user changes.

Before committing:

1. Run `git status --short`.
2. Confirm no runtime files are staged.
3. Confirm no secrets are staged.
4. Run `git diff --check`.
5. Run required compile/build checks for the task.
6. If SQL changed, run DB and repeat-run verification.
7. Stage only intended files.
8. Commit with a clear message only when the user asks or the current task explicitly requires it.
9. Push only when requested or when the workflow explicitly says to do so.

## Required Completion Report

When completing a backend task, report:

1. Task conclusion.
2. Current directory.
3. Current branch.
4. Modified files.
5. What changed.
6. API impact.
7. Database impact.
8. Frontend impact.
9. Verification performed.
10. Commit ID, if committed.
11. Push result, if pushed.
12. Remaining risks.
13. Next recommended step.
