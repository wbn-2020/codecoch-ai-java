# P2 Contract Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct user-facing HTTP semantics, align interview comparison availability, preserve successful trace metadata, add readiness snapshot detail access, and bound multipart upload memory concurrency.

**Architecture:** Keep existing business codes and default frontend unwrapping behavior. Apply resource semantics only at owner-scoped user detail boundaries, share one comparison policy between precheck and compare, add opt-in response metadata, and isolate multipart admission control in a testable helper.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, MyBatis Plus, OpenFeign, Vue 3, Axios, JUnit 5, Mockito, MockMvc, Vitest.

---

### Task 1: Owner-scoped resource HTTP semantics

**Files:**
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/service/impl/ResumeServiceImpl.java`
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/service/impl/JobReadinessServiceImpl.java`
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/service/impl/ResumeExportArtifactServiceImpl.java`
- Modify: `codecoachai-interview/src/main/java/com/codecoachai/interview/service/impl/InterviewComparisonServiceImpl.java`
- Test: corresponding `*Test.java`
- Test: `codecoachai-common/common-web/src/test/java/com/codecoachai/common/web/handler/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write failing tests for absent and foreign resources**

```java
BusinessException ex = assertThrows(BusinessException.class, () -> service.getResume(999L));
assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getCode());
```

- [ ] **Step 2: Run tests and verify RED**

- [ ] **Step 3: Replace only owner-scoped query failures with `RESOURCE_NOT_FOUND`**

```java
throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在或已不可用");
```

- [ ] **Step 4: Keep operation permission failures as `FORBIDDEN` and state conflicts unchanged**

- [ ] **Step 5: Run targeted tests and MockMvc HTTP assertions**

Expected: owner-scoped absent/foreign detail returns HTTP 404 without data leakage.

### Task 2: Shared interview report comparability policy

**Files:**
- Create: `codecoachai-interview/src/main/java/com/codecoachai/interview/support/InterviewReportComparabilityPolicy.java`
- Modify: `codecoachai-interview/src/main/java/com/codecoachai/interview/service/impl/InterviewComparisonServiceImpl.java`
- Modify: `codecoachai-interview/src/main/java/com/codecoachai/interview/service/impl/InterviewServiceImpl.java`
- Modify: comparison/remediation VO if reason codes need exposure
- Test: `InterviewComparisonServiceImplTest.java`
- Test: `InterviewServiceImplTest.java`

- [ ] **Step 1: Write failing tests for missing total score, empty rubric JSON, malformed rubric JSON and rubric mismatch**

- [ ] **Step 2: Implement a policy result with `comparable`, `reasonCode`, `message` and normalized dimensions**

```java
public record Result(boolean comparable, String reasonCode, String message,
                     Map<String, BigDecimal> dimensions) {}
```

- [ ] **Step 3: Use the policy in report selection/precheck**

- [ ] **Step 4: Use the same policy during comparison creation**

- [ ] **Step 5: Keep existing comparison detail readable and return explicit unavailable reasons**

- [ ] **Step 6: Run interview tests**

### Task 3: Successful trace metadata

**Files:**
- Create: `codecoachai-common/common-web/src/main/java/com/codecoachai/common/web/advice/ResultTraceResponseBodyAdvice.java`
- Create: `codecoachai-common/common-web/src/test/java/com/codecoachai/common/web/advice/ResultTraceResponseBodyAdviceTest.java`
- Modify: `codecoachai-gateway/src/main/java/com/codecoachai/gateway/config/CorsConfig.java`
- Modify: `codecoch-ai-vue/src/utils/request.ts`
- Modify: `codecoch-ai-vue/src/types/api.ts`
- Create: `codecoch-ai-vue/tests/unit/utils/request-response-meta.test.ts`

- [ ] **Step 1: Write failing backend tests that a successful `Result` receives traceId**

- [ ] **Step 2: Implement `ResponseBodyAdvice<Result<?>>` using the current trace context**

- [ ] **Step 3: Expose `X-Trace-Id` in Gateway CORS**

- [ ] **Step 4: Add frontend opt-in request metadata**

```ts
export interface ApiEnvelope<T> extends ApiResult<T> {
  traceId?: string
}
```

- [ ] **Step 5: Preserve current default `result.data` return behavior**

- [ ] **Step 6: Add tests for default unwrap and metadata opt-in**

### Task 4: Readiness snapshot detail API

**Files:**
- Modify: `codecoch-ai-vue/src/api/jobRequirement.ts`
- Modify: `codecoch-ai-vue/tests/unit/features/job-requirement-matrix.test.ts` or create API test

- [ ] **Step 1: Write failing URL contract test**

```ts
await getJobReadinessSnapshotApi(15, 42)
expect(request.get).toHaveBeenCalledWith('/job-targets/15/readiness-snapshots/42')
```

- [ ] **Step 2: Add positive integer validation and API function**

- [ ] **Step 3: Run targeted frontend tests**

### Task 5: Multipart admission control

**Files:**
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/config/ResumeExportProperties.java`
- Create: `codecoachai-resume/src/main/java/com/codecoachai/resume/export/ResumeUploadAdmissionGuard.java`
- Modify: `codecoachai-resume/src/main/java/com/codecoachai/resume/service/impl/ResumeExportArtifactServiceImpl.java`
- Create: `codecoachai-resume/src/test/java/com/codecoachai/resume/export/ResumeUploadAdmissionGuardTest.java`
- Modify: `ResumeExportArtifactServiceImplTest.java`

- [ ] **Step 1: Write failing tests for over-size rejection, saturated concurrency and permit release**

- [ ] **Step 2: Implement a fair semaphore with configured max concurrent uploads**

- [ ] **Step 3: Check `Files.size(path)` before creating `PathMultipartFile`**

- [ ] **Step 4: Acquire before Feign upload and release in `finally`**

- [ ] **Step 5: Return controlled overload business error and log metadata only**

- [ ] **Step 6: Run resume export tests**

### Task 6: Integrated verification

- [ ] **Step 1: Run common-web, resume and interview targeted suites**
- [ ] **Step 2: Run frontend request/readiness suites**
- [ ] **Step 3: Run full backend `mvn test`**
- [ ] **Step 4: Run frontend full tests, type-check and build**
