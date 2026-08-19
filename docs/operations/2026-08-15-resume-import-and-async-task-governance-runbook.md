# Resume Import Repair and Async Task Governance Runbook

> Date: 2026-08-15
> Scope: `CCA-P1-001`, `CCA-P1-003`, `CCA-P2-012`
> Safety rule: this runbook does not authorize direct database updates, unconditional batch retries, record deletion, or execution without the designated environment owner.

## 1. Preconditions

Before any non-read-only action:

1. Confirm the exact target environment, maintenance window, approver, operator, database backup, and rollback owner.
2. Deploy and verify migrations `V4_110` through `V4_114`.
3. Confirm API authorization and audit logging work without exposing sensitive source data.
4. Confirm `RESUME_IMPORT_REPAIR_AUDIT_KEY` is configured through the environment secret mechanism. Do not place its value in requests, scripts, logs, source code, or reports.
5. Prepare a unique `repairBatchId` matching `[A-Za-z0-9._:-]` with length 8-64.
6. Start with an explicit and small scope. Never use an unbounded all-record request.

## 2. Resume Import Repair

### 2.1 Endpoints

- Create preview or repair: `POST /admin/resume-import-repairs`
- Roll back audited repair records: `POST /admin/resume-import-repairs/{repairBatchId}/rollback`

The implementation is in:

- `codecoachai-core/src/main/java/com/codecoachai/resume/controller/AdminResumeImportRepairController.java`
- `codecoachai-core/src/main/java/com/codecoachai/resume/service/impl/ResumeImportHistoricalRepairServiceImpl.java`

### 2.2 Required scope and limits

Each request must include at least one explicit selector:

- `analysisRecordIds`, or
- `resumeIds`, or
- `userIds`.

`maxRecords` defaults to `20` and accepts only `1..100`. Use the smallest set that can validate the desired data pattern. Do not combine unrelated data cohorts in the same repair batch.

### 2.3 Dry-run procedure

1. Generate a batch ID and collect only the target identifiers.
2. Call `POST /admin/resume-import-repairs` without changing `dryRun`, or explicitly set `dryRun=true`.
3. Record the response summary: selected, eligible, changed, skipped and failed counts; validation findings; reason categories; and affected IDs.
4. Verify that the scope matches the original read-only inventory and that no sensitive values are present in logs or exported evidence.
5. Review any records proposed for normalization. Stop if the output would replace real contact information with a placeholder, discard non-duplicate projects, or exceed the approved count.

Dry-run must be repeated after changing scope, normalizer version, migrations, or candidate deployment.

### 2.4 Controlled execution

Only after dry-run approval, submit `dryRun=false`. Execution requires all of the following:

- An explicit confirmation flag.
- An operator reason.
- An idempotency key.
- Valid authorization.
- The approved `repairBatchId`.
- Explicit scope and `maxRecords`.
- `RESUME_IMPORT_REPAIR_AUDIT_KEY` available to encrypt before/after snapshots.

The service refuses execution if the audit key is absent. It does not delete historical records. It normalizes structure and stores encrypted audit snapshots so the exact repaired records can be inspected and recovered.

After execution:

1. Compare actual counts with dry-run counts.
2. Read each repaired record through the normal API path.
3. Verify schema validity, contact-field policy, project de-duplication, preview consistency and context eligibility.
4. Archive the batch request, response summary, audit record IDs, operator, reviewer and timestamps.
5. Stop immediately if validation fails or changed counts exceed the approved threshold.

### 2.5 Rollback

Rollback is not a broad data reset. It selects exact audit records from a prior `repairBatchId`.

1. Identify the affected audit record IDs and the original batch ID.
2. Run the rollback endpoint as dry-run first.
3. Review the exact restoration set and its validation result.
4. Obtain explicit approval for execution.
5. Execute rollback for the selected audit records only.
6. Read through normal APIs and preserve the rollback audit evidence.

Do not use direct SQL updates, delete audit data, or roll back a batch whose audit scope cannot be proven.

## 3. Readiness Snapshot Recovery

`ReadinessDimensionCodec` validates readiness dimensions on write and read. Bad JSON, invalid structures, unsupported schema versions, missing required fields, duplicate dimensions, and invalid score ranges are distinct validation outcomes.

### 3.1 Endpoint and scope

- Preview or controlled regeneration: `POST /admin/readiness-repairs`
- Required permission: `admin:system:overview`

Every request must include one or more exact selectors:

- `snapshotIds`, or
- `targetJobIds`, or
- `userIds`.

`repairBatchId` is mandatory and must match `[A-Za-z0-9._:-]{8,64}`. `maxRecords` defaults to `20` and permits only `1..100`. Do not combine unrelated cohorts in a batch.

### 3.2 Dry-run procedure

1. Use the normal readiness read path or read-only inventory to define a small, approved scope.
2. Call `POST /admin/readiness-repairs` without setting `dryRun=false`, or explicitly set `dryRun=true`.
3. Record `matchedRecords`, `processedRecords`, status counts and each record's validation classification. Dry-run can return `ALREADY_VALID` or `WOULD_REGENERATE`; it must not write a snapshot.
4. Stop if scope, count, user/job ownership, validation reason or generated plan differs from the approved inventory.
5. Do not use returned hashes or identifiers to infer or export sensitive resume/JD content.

### 3.3 Controlled execution and verification

Only after dry-run approval, submit `dryRun=false`. Execution requires:

- `confirm=true`;
- a non-empty operator reason;
- an idempotency key;
- the required permission;
- the approved `repairBatchId`, exact scope and `maxRecords`.

The service regenerates only invalid snapshots through the current readiness evidence path and sets the supplied repair batch ID on the new snapshot. It never overwrites or deletes the historical snapshot row.

After execution:

1. Compare `matchedRecords`, `changedRecords` and status counts against dry-run.
2. Verify every `REGENERATED` result has a new snapshot ID and a valid validation status.
3. Read the target job through the normal readiness API and confirm a legal dimension array is returned.
4. Stop and mark the batch `MANUAL_ACTION_REQUIRED` when regeneration fails, produces an invalid result or exceeds the approved count.

### 3.4 Recovery boundary

There is no destructive rollback operation for readiness repair because the original snapshot is retained. To recover from an unexpected replacement result, stop further execution, keep the original and regenerated snapshots for audit, and use the normal read/history path while the data owner investigates the exact batch. Do not delete snapshots or issue broad SQL updates.

## 4. Async Task Governance

### 4.1 Read-only inventory

Use `GET /admin/tasks/governance-inventory` before any retry decision. The inventory is bounded and read-only. Classify by:

- `bizType`
- current task state
- age
- retry count
- business result presence and validity
- terminal reason
- idempotency eligibility
- governance status.

The available governance statuses are:

- `UNASSESSED`
- `RETRY_APPROVED`
- `RETRYING`
- `RESOLVED`
- `WONT_RETRY`
- `MANUAL_ACTION_REQUIRED`

Every category needs an owner, retry condition, maximum batch size, stop condition, and evidence of whether a valid business result already exists.

### 4.2 Retry preview and execution

The retry preview and confirmed retry routes are implemented in `AdminTaskController`. Treat preview as the default action:

1. Request the preview for an explicitly selected, homogeneous set of tasks.
2. Confirm the task has no valid business result or that its retry contract supports idempotent recovery.
3. Verify the new execution will carry a parent execution reference and a new idempotency-safe execution identity.
4. Obtain owner approval and state the stop threshold before confirmed retry.
5. Retry the smallest approved batch.
6. Monitor the user result, business record, AI log and `async_task` until a single terminal state is reached.

Do not retry tasks that may duplicate a resume, daily plan, interview report, notification, or external side effect until their idempotency evidence is documented. Never mark a failed task as successful merely to reduce inventory counts.

### 4.3 Stop and escalation conditions

Stop the batch and escalate when any of the following occurs:

- A duplicate business result appears.
- A terminal state regresses or conflicts across records.
- The retry effect exceeds the previewed count.
- An external provider failure rate crosses the agreed threshold.
- The task lacks a clear owner, idempotency key, or valid source data.
- The request would require an unbounded query, all-table update, or deletion.

Record the failed batch as `MANUAL_ACTION_REQUIRED` or `WONT_RETRY` as appropriate. Preserve task history, audit evidence, and failure reasons.

## 5. Evidence Template

For each repair or retry batch, archive:

| Field | Required value |
|---|---|
| Environment | Exact target environment |
| Issue IDs | Applicable CCA IDs |
| Batch ID | `repairBatchId` or governance batch identifier |
| Scope | Explicit record IDs/user IDs/task IDs |
| Mode | Read-only, dry-run, execution, or rollback |
| Limits | `maxRecords` and stop threshold |
| Approval | Approver, operator, reviewer and timestamp |
| Before/after | Counts, validation summaries and audit IDs |
| Correlation | `traceId`, `executionId`, `bizId`, `runId`, task ID |
| Result | Succeeded, skipped, failed and reason categories |
| Follow-up | Browser/API verification and residual risk |

This runbook was written and reviewed offline. No repair, rollback, migration, real task retry, local service startup, or remote environment operation was performed while producing it.
