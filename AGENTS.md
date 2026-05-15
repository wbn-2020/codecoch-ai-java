# AGENTS.md — CodeCoachAI Java Backend

## Project identity

This repository is the Java backend of CodeCoachAI, an AI interview training platform.

Current project phase:
- V1 code is considered functionally complete.
- V1 documentation still needs to be updated and aligned with the actual code.
- The next development phase is V2.
- Do not start V2 feature implementation until V1 docs, API contracts, and release notes are synchronized.

Related repositories:
- Frontend: https://github.com/wbn-2020/codecoch-ai-vue.git
- Backend: https://github.com/wbn-2020/codecoch-ai-java.git
- Docs: https://github.com/wbn-2020/codecoch-ai-doc.git

## Backend architecture expectations

Act as a senior Java architect when working in this repo.

Expected stack:
- Java 17
- Spring Boot 3
- Spring Cloud / Spring Cloud Alibaba
- Gateway
- Nacos
- OpenFeign
- MyBatis-Plus
- MySQL
- Redis
- Sa-Token
- Knife4j / OpenAPI

Backend should preserve the current microservice/module boundary. Do not collapse modules into a monolith unless explicitly requested.

## V1 scope that must remain stable

V1 includes:
- User registration and login
- User profile and password management
- Question browsing, answering, favorites, wrong records
- Admin question/category/tag/group management
- Resume and project experience manual input
- Interview creation
- Interview room flow
- AI scoring, follow-up questions, and summary generation
- Interview report generation and history
- Prompt template management
- AI call logs
- System configuration

V1 does not include:
- MinIO file upload
- Message queue based async processing
- Elasticsearch
- Voice interview
- Embedding / vector search
- Learning plan generation
- AI-generated question bank expansion

## Non-negotiable rules

1. Before modifying code, inspect the current implementation and summarize the affected modules.
2. Do not invent API paths. Use the actual controller mappings as source of truth.
3. If frontend integration is involved, verify request/response DTOs against the frontend API files.
4. Do not silently change database schema. If schema changes are required, update SQL migration/init files and explain compatibility impact.
5. Keep admin APIs under `/admin/**` and user APIs under their existing user-side paths.
6. Do not expose `/inner/**` APIs to frontend usage.
7. Preserve permission isolation:
   - Normal users cannot access admin APIs.
   - Normal users cannot read or modify another user's resumes, interviews, reports, favorites, or wrong records.
8. For AI-related changes, clearly distinguish mock mode and real model mode.
9. Any V1 fix must include verification steps:
   - compile command
   - affected endpoint list
   - regression cases
   - possible frontend impact

## Build and verification commands

Use the actual project scripts/commands if they exist. Otherwise prefer:

```bash
mvn clean compile
mvn clean package -DskipTests
```

For service-level checks:
- Confirm Nacos service registration.
- Confirm gateway routing.
- Confirm MySQL init data compatibility.
- Confirm Redis/token behavior where relevant.

## Required response format for Codex

When completing a task, report:

1. Task conclusion
2. Modified files
3. What changed in each file
4. API impact
5. Database impact
6. Frontend impact
7. Verification performed
8. Remaining risks
9. Next recommended step

## Documentation sync rule

If code changes affect V1 behavior, update or instruct the user to update the docs repository:
- `PROJECT_STATE.md`
- `V1_RELEASE_NOTES_DRAFT.md`
- API contract documents
- V2 roadmap if the change affects the V2 baseline
