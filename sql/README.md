# Flyway Database Migrations

Use Flyway to manage the versioned scripts under `sql/migration/`.

## One-time migrate

```cmd
set MYSQL_PASSWORD=your-local-password
cd C:\my-claude\CodeCoachAI-java
mvn flyway:migrate -DskipTests
```

## Common commands

| Action | Command |
|---|---|
| Show current version | `mvn flyway:info` |
| Apply pending scripts | `mvn flyway:migrate` |
| Validate applied scripts | `mvn flyway:validate` |
| Repair metadata table | `mvn flyway:repair` |
| Dangerous dev-only clean | `mvn flyway:clean -Dflyway.cleanDisabled=false` |

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
