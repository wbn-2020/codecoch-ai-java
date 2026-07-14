# Migration Rehearsal Deployment Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove clean-database migration compatibility, then deploy the dashboard and P2 fixes to the test server and complete real environment acceptance.

**Architecture:** Package the exact tested workspace snapshot. Run migrations against a disposable isolated MySQL 8 container before touching the running environment. Deploy atomically with backups, restart only affected services and frontend, then verify browser, APIs, logs and container health.

**Tech Stack:** Bash, Docker, MySQL 8, Maven Flyway plugin, PowerShell packaging, SSH, Nginx, Spring Boot.

---

### Task 1: Add reusable isolated migration rehearsal script

**Files:**
- Create: `scripts/rehearse-migrations.sh`
- Create: `scripts/verify-migration-schema.sql`
- Create: `docs/operations/isolated-migration-rehearsal.md`

- [ ] **Step 1: Implement strict shell setup, random secret file, network/container/volume names**
- [ ] **Step 2: Mount `sql/init.sql` into a disposable MySQL 8 container**
- [ ] **Step 3: Run `flyway:info`, `migrate`, `validate`, `info` from a disposable Maven container**
- [ ] **Step 4: Export history, schema, columns, indexes and seed counts**
- [ ] **Step 5: Add cleanup trap that never targets existing containers or volumes**
- [ ] **Step 6: Add a dry validation mode for required local files**

### Task 2: Run local/static script verification

- [ ] **Step 1: Parse shell syntax with `bash -n`**
- [ ] **Step 2: Verify the cleanup target prefix and no host port mapping**
- [ ] **Step 3: Verify no password literal is logged**

### Task 3: Run the rehearsal on the test server

- [ ] **Step 1: Upload the tested source snapshot and scripts to a timestamped release directory**
- [ ] **Step 2: Run the isolated migration script**
- [ ] **Step 3: Verify V4_058-V4_071 history, 28 tables, final indexes and seed counts**
- [ ] **Step 4: Download evidence to `deploy-artifacts/<release>/migration-evidence/`**
- [ ] **Step 5: Confirm disposable containers, network and volumes were removed**

### Task 4: Build release artifacts

- [ ] **Step 1: Run backend tests and package affected services**
- [ ] **Step 2: Run frontend tests, type-check and production build**
- [ ] **Step 3: Record hashes and create timestamped deployment bundle**

### Task 5: Deploy to the test environment

- [ ] **Step 1: Create hard-link/predeploy backups**
- [ ] **Step 2: Upload backend JARs, common libraries, Gateway config and frontend dist**
- [ ] **Step 3: Atomically replace artifacts**
- [ ] **Step 4: Recreate only affected application containers and frontend**
- [ ] **Step 5: Verify restart count, Nacos registration and HTTP health**

### Task 6: Real environment acceptance

- [ ] **Step 1: Verify admin/user login, logout and permission boundary**
- [ ] **Step 2: Capture dashboard screenshots at desktop and mobile widths**
- [ ] **Step 3: Verify no unexpected Network 400/401/403/404/409/500 or console errors**
- [ ] **Step 4: Verify absent/foreign resume and readiness snapshot return HTTP 404**
- [ ] **Step 5: Verify comparison selection rejects incomplete historical reports with explicit reason**
- [ ] **Step 6: Verify successful response traceId metadata and readiness snapshot detail**
- [ ] **Step 7: Exercise bounded concurrent export without unrestricted load**
- [ ] **Step 8: Update the full-chain acceptance report with release ID and evidence**
