# CodeCoachAI Release Acceptance Remediation Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the July 11 release acceptance defects, deploy the complete V4 release to the test server, and re-run browser and API acceptance.

**Architecture:** Preserve the current Spring Cloud and Docker Compose topology. Fix contracts at their owning boundaries, add a forward-only migration for database uniqueness changes, keep `docs/nacos` authoritative, and deploy timestamped artifacts with backup and rollback evidence.

**Tech Stack:** Java 17, Spring Boot, Spring Cloud Gateway, Nacos, MyBatis-Plus, MySQL 8, Redis, Vue 3, TypeScript, Vitest, Maven, Docker Compose, Nginx.

---

### Task 1: Gateway, CORS, and Route Contract

**Files:**
- Modify: `docs/nacos/codecoachai-gateway-dev.yml`
- Modify: `config/nacos/codecoachai-gateway-dev.yml`
- Create or modify: `codecoachai-gateway/src/test/java/com/codecoachai/gateway/config/GatewayRouteContractTest.java`

- [ ] **Step 1: Write a failing contract test**

The test must load both Gateway YAML files and assert:

```text
http://nqx.githubpage.com:30080
/resume-suggestions/**
/resume-ats-templates/**
/resume-exports/**
/resume-artifacts/**
/job-experiments-v2/**
/career-calendar/**
/career-imports/**
/interview-tts/**
/interview-streaming-asr/**
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn -pl codecoachai-gateway -am "-Dtest=GatewayRouteContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: failure because the authoritative `docs/nacos` file lacks the new origin/routes.

- [ ] **Step 3: Synchronize authoritative and reference configuration**

Add the required origin and routes to both files without exposing `/inner/**`.

- [ ] **Step 4: Verify GREEN**

Run the same Maven command and expect zero failures.

### Task 2: Revoked Token Error Semantics

**Files:**
- Inspect and modify the auth/security exception handling under `codecoachai-auth` and `codecoachai-common/common-security`
- Add focused controller/filter tests in the owning module

- [ ] **Step 1: Write a failing test**

Reproduce a revoked or unknown token request to current-user and assert the established unauthenticated/token-invalid response instead of HTTP 500.

- [ ] **Step 2: Verify RED**

Run the focused auth/security test and confirm the current exception escapes as server error.

- [ ] **Step 3: Implement minimal exception mapping**

Map revoked, unknown, expired, and malformed tokens consistently without logging token values.

- [ ] **Step 4: Verify GREEN**

Run focused tests and the affected auth/security module tests.

### Task 3: Requirement, Evidence, Readiness, and Experiment Frontend Contracts

**Files:**
- Modify: `codecoch-ai-vue/src/features/job-requirement-matrix.ts`
- Modify: `codecoch-ai-vue/src/types/jobRequirement.ts`
- Modify: `codecoch-ai-vue/src/views/job-experiment/components/CareerExperimentPanel.vue`
- Modify related frontend unit tests

- [ ] **Step 1: Write failing normalization tests**

Cover:

```text
RESUME_MATCH, INTERVIEW_REPORT, APPLICATION_RESULT evidence retains generic IDs and labels
nextActions[actionCode,title,path] survives normalization
confidenceLevel maps to dimension confidence
fallback maps to conservative/sample-insufficient UI state
attribution metric enum matches backend
```

- [ ] **Step 2: Verify RED**

Run only the affected Vitest files and confirm the expected contract failures.

- [ ] **Step 3: Implement backend-owned wire normalization**

Use legacy aliases only as fallback and remove the unsupported `FEEDBACK` metric unless the backend is changed to support it.

- [ ] **Step 4: Verify GREEN**

Run affected tests and `npm run type-check`.

### Task 4: CSV, ICS, Experiment Concurrency, and Active-Row Uniqueness

**Files:**
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/careerimport/CareerImportServiceImpl.java`
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/experimentv2/ExperimentV2ServiceImpl.java`
- Modify related resume tests
- Create: next available migration after `V4_068`

- [ ] **Step 1: Write failing tests**

Cover:

```text
SKIP same normalized company/job inside seven-day window shares one fingerprint bucket
CREATE leaves fingerprint null
non-fingerprint DuplicateKeyException returns APPLICATION_INSERT_FAILED
concurrent experiment assignment returns existing assignment
concurrent ICS UID returns skipped duplicate
two import/delete/reimport/delete cycles do not violate active-row uniqueness
```

- [ ] **Step 2: Verify RED**

Run focused resume tests and confirm failures correspond to the uncovered behavior.

- [ ] **Step 3: Implement service fixes and forward migration**

Do not edit V4_068 if it may already be deployed. Add a forward migration that replaces the soft-delete uniqueness design with active-row-safe semantics.

- [ ] **Step 4: Verify GREEN**

Run all career import, experiment v2, calendar, and migration tests.

### Task 5: Resume Undo and Artifact Integrity

**Files:**
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/service/impl/ResumeSuggestionServiceImpl.java`
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/service/impl/ResumeExportArtifactServiceImpl.java`
- Modify: `codecoch-ai-vue/src/features/resume-delivery.ts`
- Modify related backend and frontend tests

- [ ] **Step 1: Preserve and extend undo regression tests**

Assert two different-length suggestions can be batch accepted and then undone one by one without reintroducing previously undone content.

- [ ] **Step 2: Write failing integrity tests**

Cover:

```text
template definition JSON mismatch rejects export
PDF/DOCX download verifies actual bytes
rebuilt ZIP returns freshly calculated hash
ZIP manifest size/hash matches entries
foreign user cannot read or download artifact
```

- [ ] **Step 3: Verify RED**

Run the focused backend/frontend tests.

- [ ] **Step 4: Implement minimal integrity checks**

Use streaming-safe hashing where practical and return stable artifact/export errors.

- [ ] **Step 5: Verify GREEN**

Run focused resume export, suggestion, and frontend tests.

### Task 6: Interview Ownership and Scenario/Rubric Validation

**Files:**
- Modify: `codecoachai-interview/src/main/java/com/codecoachai/interview/service/impl/InterviewComparisonServiceImpl.java`
- Modify scenario/rubric service files
- Modify related interview tests

- [ ] **Step 1: Write failing ownership tests**

Missing and foreign report IDs must produce the same unavailable response and no existence oracle.

- [ ] **Step 2: Write failing publication tests**

Reject duplicate stage codes, invalid budgets/follow-up values, duplicate rubric dimensions, and weights not totaling 100.

- [ ] **Step 3: Verify RED**

Run focused comparison and scenario tests.

- [ ] **Step 4: Implement ownership predicates and validation**

Keep published versions immutable.

- [ ] **Step 5: Verify GREEN**

Run the complete interview comparison/scenario test groups.

### Task 7: Voice Provider, Media Encoding, and Resource Lifecycle

**Files:**
- Modify frontend interview voice API/component/feature files
- Modify backend TTS, Streaming ASR, audio retention, and voice delivery services
- Modify related frontend and backend tests

- [ ] **Step 1: Write failing tests**

Cover:

```text
provider comes from deploy configuration rather than fixed MOCK
MediaRecorder MIME maps to ASR encoding
unsupported MIME uses text fallback
expired ASR sessions are cleaned without a later open request
missing session after restart returns recoverable status
teardown releases local resources exactly once
remote file confirmed absent reconciles retention status to DELETED
```

- [ ] **Step 2: Verify RED**

Run focused voice tests.

- [ ] **Step 3: Implement provider/config and cleanup changes**

Keep mock available only through explicit configuration. Add scheduled cleanup and idempotent reconciliation.

- [ ] **Step 4: Verify GREEN**

Run all interview voice tests and affected frontend unit tests.

### Task 8: Full Local Release Gates

**Files:**
- No product changes unless a gate reveals a defect

- [ ] **Step 1: Backend verification**

```powershell
mvn -DskipTests compile
mvn test
mvn -DskipTests package
git diff --check
```

- [ ] **Step 2: Frontend verification**

```powershell
npm run type-check
npm run test:unit:run
npm run build
```

- [ ] **Step 3: Migration and configuration verification**

Check migration numbering, duplicate versions, route coverage, Nacos source synchronization, and Compose expansion.

### Task 9: Build Deployment Bundle and Server Backup

**Files:**
- Create local timestamped deployment manifest under the acceptance evidence directory
- Do not commit secrets or server dumps

- [ ] **Step 1: Build artifact manifest**

Record each JAR/frontend bundle SHA-256 and the source Git state.

- [ ] **Step 2: Create remote backup**

Under `/opt/codecoachai/backups/<timestamp>` back up:

```text
jars/
web/
docker-compose.yml
Nacos exported configuration
MySQL dump
container inventory and recent logs
```

- [ ] **Step 3: Verify backup completeness**

Check files, sizes, hashes, and MySQL dump completion before activation.

### Task 10: Database, Nacos, Backend, and Frontend Deployment

**Files:**
- Remote deployment artifacts only

- [ ] **Step 1: Stage and hash-verify artifacts**

Upload to `/opt/codecoachai/releases/<timestamp>`.

- [ ] **Step 2: Validate and apply migrations**

Inspect Flyway history, validate, then apply only pending migrations.

- [ ] **Step 3: Publish Nacos configuration**

Back up previous values and publish authoritative files.

- [ ] **Step 4: Replace affected services**

Restart affected application containers in dependency order and verify Nacos registration/logs after each group.

- [ ] **Step 5: Replace frontend**

Use a staged directory and atomic switch/recreate of `codecoachai-frontend`.

- [ ] **Step 6: Roll back on any release gate failure**

Restore artifacts/config and preserve failure evidence.

### Task 11: Online Acceptance and Report Update

**Files:**
- Modify: `C:\vibe-coding\codecoachai\文档相关\审查报告\7-10\2026-07-11-CodeCoachAI测试环境全链路验收报告.md`
- Create evidence files under a timestamped acceptance evidence directory

- [ ] **Step 1: Browser acceptance**

Verify resource loading, administrator/normal login and logout, console, Network, permissions, and responsive layout.

- [ ] **Step 2: Four-priority acceptance**

Execute requirement/readiness, interview/voice, resume delivery, and experiment/CRM flows.

- [ ] **Step 3: Security and resource regression**

Verify IDOR, revoked token semantics, repeated voice page entry/exit, duplicate submissions, and resource cleanup.

- [ ] **Step 4: Update release decision**

Record executed items, remaining blockers, P0-P3 status, and explicit continue-testing/gray/production decisions.

