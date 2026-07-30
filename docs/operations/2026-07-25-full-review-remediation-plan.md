# CodeCoachAI Full Review Remediation Plan

Date: 2026-07-25

## Objective

Remove confirmed release blockers and high-risk defects from the backend,
frontend, database, and delivery chain; produce a reproducible release
candidate; deploy it to the authorized test environment; and complete a
documented acceptance pass with a tested rollback path.

## Delivery Rules

- Preserve all pre-existing uncommitted work. Do not reset or clean either
  repository.
- Do not commit credentials, generated archives, remote configuration values,
  or database dumps.
- Do not deploy from a dirty or unidentified source tree.
- Do not mutate the test environment until a rollback point exists.
- Stop deployment immediately on migration failure, failed core health checks,
  authentication failure, or evidence of data corruption.
- Prefer static checks, focused tests, and one-shot build commands over local
  services.

## Phases

### 1. Discovery

Deliverables:

- Backend and frontend Git state snapshot.
- Test environment topology, current release ID, artifact hashes, schema state,
  disk capacity, and rollback inventory.
- Explicit list of protected user changes.

Exit criteria:

- Current runtime and release inputs are identifiable.
- A backup and rollback strategy can be stated without assumptions.

### 2. P0 Remediation

Scope:

- Reconcile `init.sql` and Flyway baseline ownership.
- Remove the fixed default administrator credential.
- Resolve the V9 configuration contract failure.
- Make CI cover the active development branch and require clean, reproducible
  build inputs.

Exit criteria:

- No known fresh-bootstrap schema conflict.
- No repository-controlled production-capable default credential.
- Backend test reactor passes.
- Frontend type check and production build pass.
- Release metadata identifies exact backend and frontend commits.

### 3. P1 Security And Correctness

Scope:

- Deployment command injection and host-key verification.
- AI provider URL validation and outbound request restrictions.
- Gateway user-context replay protection and internal endpoint authorization.
- Atomic password-reset token consumption and session revocation.
- Gateway rate limiting and effective OpenFeign timeout configuration.
- Frontend refresh-session isolation, HTTP authentication error handling, and
  dynamic route entity reload.
- MQ task ownership, dead-letter consistency, permission-cache invalidation,
  last-administrator protection, and file deletion ordering.

Exit criteria:

- Every changed behavior has a focused unit, contract, or integration-style
  test.
- No unresolved blocker or high finding remains in the changed scope.

### 4. Engineering Hardening

Scope:

- Docker health probes and non-root runtime.
- Reproducible frontend asset generation.
- Critical N+1 and repeated-count queries.
- SSE duplicate handling, blob error decoding, and sensitive raw-data
  normalization.
- Dependency, static-analysis, coverage, and browser-test gates where feasible.

Exit criteria:

- Build output is deterministic for a fixed version input.
- Critical query paths have bounded query counts.
- CI runs real backend and frontend release commands.

### 5. Verification

Required evidence:

- Backend focused tests and full Maven test reactor.
- Backend package build.
- Frontend type check, focused tests, full split test suite, and production
  build.
- Migration contract validation and, when an isolated MySQL runtime is
  available, fresh and upgrade migration rehearsal.
- Dependency audit and diff hygiene checks.

Release is blocked if any required command fails without an approved and
documented exception.

### 6. Release Candidate

Deliverables:

- Full backend and frontend commit identifiers.
- SHA-256 manifest for every artifact.
- Migration list and expected schema changes.
- Deployment command sequence.
- Rollback command sequence and retained backup paths.
- Known limitations and acceptance checklist.

### 7. Test Deployment

Order:

1. Verify free disk and runtime health.
2. Capture current artifact/configuration hashes.
3. Create and verify database and artifact backups.
4. Upload to an immutable release directory.
5. Validate and apply database migrations.
6. Atomically switch artifacts.
7. Restart only affected services.
8. Run health and log checks.

Rollback triggers:

- Migration error.
- Core service fails health checks.
- Login or authorization regression.
- Unexpected schema or row-count change.
- Repeated severe application errors after restart.

### 8. Acceptance

Coverage:

- Public page and static assets.
- User and administrator login/logout/refresh behavior.
- Current-user and permission checks.
- Resume, evidence, question, interview, task, file, search, and AI smoke
  workflows that do not create destructive production-like data.
- Export/download error handling and SSE completion.
- Admin model configuration validation and sensitive-data display boundaries.
- Rate-limit and unauthenticated/unauthorized response behavior.
- Restart persistence and post-deployment log review.

Exit criteria:

- All critical scenarios pass.
- Any skipped external-provider scenario has a reason and residual-risk owner.
- Final report includes changes, verification evidence, unresolved risks, and
  follow-up actions.

## Current Environment Status

Updated: 2026-07-26

- The authorized test server root filesystem is at 53% utilization with
  approximately 14 GiB free. The disk-capacity deployment blocker is resolved.
- The verified pre-deployment database backup is retained at
  `/opt/codecoachai/backups/full-review-20260726-120232-predeploy`.
- The isolated rehearsal database successfully migrated from baseline `4.085`
  through `4.097`, and Flyway validation passed.
- The production-like test database has not yet been migrated or switched to
  the new artifacts. Release gates and rollback capture remain mandatory before
  that mutation.

## HTTP Error Contract Decision

This release preserves HTTP 200 for legacy `BusinessException` codes that do
not explicitly declare an HTTP status. Existing Feign clients deserialize the
`Result` envelope only on successful HTTP transport responses, and the
frontend currently exposes different error object shapes for HTTP 200 business
failures and non-2xx failures.

Explicit 401, 403, 404, 409, 422, 429, and 503 mappings remain enabled. A
future global migration requires a Feign error decoder, a normalized frontend
error type, dual-protocol contract tests, and caller-first rollout.
