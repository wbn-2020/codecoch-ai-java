# Migration Idempotency And Soft Delete Unique Key Playbook

## Scope

This playbook covers the first review round items:

- V2 migration idempotency strategy for:
  - `sql/migration/V2_003__industry_template_scene_interview.sql`
  - `sql/migration/V2_010__resume_optimize_apply.sql`
  - `sql/migration/V2_011__practice_answer_review_enhancement.sql`
  - `sql/migration/V2_012__fix_v2_migration_idempotency.sql`
- Soft-delete unique key risk for relation rebuild flows:
  - `sys_user_role.uk_sys_user_role_user_role (user_id, role_id)`
  - `question_tag_relation.uk_question_tag_relation (question_id, tag_id)`

## Decision

Do not rewrite applied historical migrations.

`V2_003`, `V2_010`, and `V2_011` contain non-idempotent DDL. Editing them after an environment has recorded Flyway checksums can cause `flyway:validate` failures and can hide which schema state was actually deployed.

`V2_012__fix_v2_migration_idempotency.sql` is the compatibility repair layer for prior V2 rerun risk. If an environment has already applied these V2 scripts through Flyway, keep the files immutable and add only a new migration or operational repair note for future fixes.

## Current Code Repair

The current relation rebuild code avoids delete-then-insert against unique keys that do not include `deleted`:

- User role assignment soft-deletes only roles no longer assigned, then upserts target `(user_id, role_id)` rows with `deleted = 0`.
- Question tag replacement soft-deletes only tags no longer present, then upserts target `(question_id, tag_id)` rows with `deleted = 0`.

This means a previously soft-deleted relation is reactivated instead of inserted as a duplicate key.

## Manual Database Acceptance

Run these checks in a non-production copy first. Do not run repair commands until the checksum state is known.

### Flyway State

```sql
SELECT installed_rank, version, description, type, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('2.003', '2.010', '2.011', '2.012')
ORDER BY installed_rank;
```

Acceptance:

- Existing applied scripts remain `success = 1`.
- No edited historical file is required to make `flyway:validate` pass.
- If validation already fails because a historical file was changed before this playbook, capture the old/new checksum and use `flyway:repair` only after confirming the database objects match the intended deployed schema.

### V2 Schema Drift

```sql
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'interview_session' AND column_name IN ('industry_template_id', 'industry_context'))
    OR (table_name = 'resume' AND column_name IN ('source_resume_id', 'source_optimize_record_id', 'applied_at'))
    OR (table_name = 'practice_record' AND column_name IN (
      'answer_duration_seconds', 'source', 'level', 'strengths', 'weaknesses',
      'improvement_suggestions', 'reference_comparison', 'knowledge_gaps',
      'suggested_follow_ups', 'reference_answer_snapshot', 'question_snapshot_json', 'review_json'
    ))
  )
ORDER BY table_name, column_name;
```

Acceptance:

- All expected columns exist before application code depending on them is released.
- Missing objects should be repaired with a new forward migration or controlled manual DDL, not by editing `V2_003`, `V2_010`, or `V2_011`.

### Soft Delete Relation Conflicts

```sql
SELECT user_id, role_id, COUNT(*) AS row_count,
       SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS active_count
FROM sys_user_role
GROUP BY user_id, role_id
HAVING row_count > 1 OR active_count > 1;

SELECT question_id, tag_id, COUNT(*) AS row_count,
       SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS active_count
FROM question_tag_relation
GROUP BY question_id, tag_id
HAVING row_count > 1 OR active_count > 1;
```

Acceptance:

- No duplicate physical rows exist for the same logical relation.
- No relation has more than one active row.
- Reassigning an existing user role and re-saving an existing question tag set should reactivate/update the existing row rather than fail with duplicate key.

## Future DB Migration Option

If product requirements need multiple historical tombstones for the same relation, do not simply change the unique keys to `(biz_key..., deleted)` without a data review. With a binary `deleted` flag this still allows only one deleted row per relation. Prefer a dedicated tombstone field such as `deleted_at` or a generated active-key strategy after validating existing data and online DDL impact.
