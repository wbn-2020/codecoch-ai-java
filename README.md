# CodeCoachAI Java Backend

This repository is the Java backend for CodeCoachAI.

Current branch focus: V2 backend completion, B0 backend freeze testing, and API contract stabilization.

Authoritative V2 scope source:

```text
C:\my-claude\CodeCoachAI-doc\PRD\CodeCoachAI_PRD_V2_AI能力增强版.md
```

Frontend implementation is outside this repository.

## Stack

- Java 17
- Spring Boot 3
- Spring Cloud Alibaba
- Spring Cloud Gateway
- Nacos
- OpenFeign
- MyBatis-Plus
- MySQL 8
- Redis
- Sa-Token
- Knife4j / OpenAPI

## Local Services

Start infrastructure first:

```text
MySQL -> Redis -> Nacos
```

Then import Nacos configs:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\nacos\import-nacos-config.ps1
```

Start backend modules as needed:

```powershell
mvn -pl codecoachai-gateway spring-boot:run
mvn -pl codecoachai-auth spring-boot:run
mvn -pl codecoachai-user spring-boot:run
mvn -pl codecoachai-question spring-boot:run
mvn -pl codecoachai-resume spring-boot:run
mvn -pl codecoachai-interview spring-boot:run
mvn -pl codecoachai-ai spring-boot:run
mvn -pl codecoachai-file spring-boot:run
```

## Database

Keep the database name consistent with Nacos datasource config.

`sql/init.sql` currently creates and uses `codecoachai_v1` for local dev compatibility. The name is historical; after the V2 migrations below are applied, it is the local V2 verification schema. For an isolated V2 verification database, create a temporary copy with the database name replaced, then import that temporary file.

Migration order:

```text
sql/migration/V2_001__file_resume_upload.sql
sql/migration/V2_002__resume_optimize_record.sql
sql/migration/V2_003__industry_template_scene_interview.sql
sql/migration/V2_004__ai_question_review.sql
sql/migration/V2_005__question_duplicate_review_relation.sql
sql/migration/V2_006__study_plan_task.sql
sql/migration/V2_007__prompt_template_version_ai_log_enhancement.sql
sql/migration/V2_008__practice_answer_review.sql
```

Use Windows-safe MySQL commands:

```powershell
mysql --host=127.0.0.1 --user=root --password=wbn123.. --default-character-set=utf8mb4 --database=codecoachai_v1 -e "source sql/migration/V2_008__practice_answer_review.sql"
```

## AI Mock Mode

Local V2 backend smoke tests should use mock AI unless explicitly validating a real model provider. Check the `codecoachai-ai-dev.yml` Nacos config and keep `mockEnabled=true` for deterministic local verification.

## B0 Backend Freeze Contract

SSE public endpoints:

```text
GET  /ai/sse/interview-question?sessionId={id}
GET  /ai/sse/interview-comment?sessionId={id}&answerContent={shortAnswer}
POST /ai/sse/interview-comment?sessionId={id}
GET  /ai/sse/report?sessionId={id}
GET  /ai/sse/resume-optimize?resumeId={id}
GET  /ai/sse/study-plan?reportId={id}
GET  /ai/sse/admin/questions/generate?count={count}
```

`POST /ai/sse/interview-comment` accepts:

```json
{
  "answerContent": "candidate answer"
}
```

`GET /ai/sse/interview-comment` is retained only for compatibility and short smoke tests. Frontend code should prefer the POST form to avoid URL length limits, log exposure, and escaping issues.

SSE event contract:

```text
start    stream accepted
progress stage progress event
chunk    primary content chunk event
delta    legacy alias for chunk, kept for existing clients
result   structured business result
metadata structured business metadata
done     stream completed
error    sanitized failure event
```

Common payload fields:

```text
requestId, sessionId, content, index, messageId, aiCallLogId, fullContent, code, message, metadata
```

The current backend uses synchronous AI results split into SSE chunks where the AI client does not provide native token streaming.

## Verification

After backend changes, run:

```powershell
mvn clean compile
git diff --check
```

For SQL changes, also verify a clean database import or an equivalent isolated SQL replay.

Recommended backend smoke scope before V2 API freeze:

- resume upload and parse-status
- interview list/report and SSE
- question review list
- duplicate review list
- practice answer AI review
- study plan generate and study task complete/skip
- prompt version test and AI log fields
- admin file list/detail
- sampled user isolation and admin permission checks

B0-1 smoke endpoints:

```text
/ai/sse/interview-question
/ai/sse/interview-comment
/ai/sse/report
/ai/sse/resume-optimize
/ai/sse/study-plan
/admin/ai/prompt-template-versions/{versionId}/test
/questions/{questionId}/practice
```

## Boundaries

- Do not modify the frontend repository from this backend workflow.
- Do not introduce MQ, ES, MinIO, Seata, Redis distributed locks, vector databases, or embedding unless the official V2 PRD requires them.
- Keep `/inner/**` protected by existing internal-call authentication.
- Keep admin APIs under `/admin/**` and guarded by admin permissions.
- Preserve existing synchronous APIs when adding SSE-compatible endpoints.
