# Flyway Database Migrations

Use Flyway to manage the versioned scripts under `sql/migration/`.

## One-time migrate

```cmd
set MYSQL_PASSWORD=your-local-password
cd <CODECOACHAI_JAVA_HOME>
mvn flyway:migrate -DskipTests
```

## Common commands

| Action | Command |
|---|---|
| Show current version | `mvn flyway:info` |
| Apply pending scripts | `mvn flyway:migrate` |
| Validate applied scripts | `mvn flyway:validate` |
| Repair metadata table | `mvn flyway:repair` |

## Dangerous cleanup

`flyway:clean` drops schema objects and is not a common development command. Do not run it
against shared, demo, staging, production, or any database that has not been backed up.

Only use it for a disposable local schema after confirming the exact database name and
connection URL, for example:

```text
DISPOSABLE LOCAL DATABASE ONLY:
1. Confirm the target JDBC URL points to a throwaway local schema.
2. Back up anything you may need.
3. Run clean only with an explicit URL and local-only credentials.
```

## Baseline

The project currently uses:

```text
baselineOnMigrate = true
baselineVersion = 2.999
```

This means the first `flyway:migrate` on an existing schema marks the current schema as baseline 2.999, then applies `V3_001` and later scripts only.

If your local database has not executed the V2 series yet, run the V2 scripts manually first.

## Migration order risk

- `V3_010__ai_call_log_enhancement_and_search_index_record.sql` and `V3_011__add_login_log_operation_log_notification.sql` are both idempotent compatibility migrations.
- Do not rename, reorder, or edit an applied migration file.
- Add a new `V3_xxx__*.sql` file for any fix, even if it only adjusts an existing table.

## Spring Boot integration

If you want service startup to run migrations automatically, enable Flyway in the target service config:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 2.999
```

Keep `sql/migration/*.sql` aligned with `src/main/resources/db/migration/` if you copy scripts there.

## Safety: Demo data script isolated

The file `V4_009__clean_demo_business_data_and_seed_chinese_dataset.sql` has been moved from
`sql/migration/` to `sql/sandbox/`. It resets business/demo data (soft-deletes 20+ tables
and re-seeds Chinese demo data) and **must not** run in production.

Flyway will skip it automatically. To manually reset demo data in a local/dev/demo/test
schema, run:

```sql
source sql/sandbox/V4_009__clean_demo_business_data_and_seed_chinese_dataset.sql;
```
