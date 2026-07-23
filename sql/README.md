# Flyway Database Migrations

Use Flyway to manage the versioned scripts under `sql/migration/`.

`sql/init.sql` is the single baseline schema for a fresh database. It is treated as
Flyway version `2.999`; only `V3_001` and later migrations run after that baseline.

## Fresh database bootstrap

The supported Docker path is:

```cmd
set MYSQL_PASSWORD=your-local-password
docker compose up mysql flyway-migrate
```

The MySQL container imports `sql/init.sql` once for a new `mysql-data` volume. After
MySQL is healthy, the one-shot `flyway-migrate` container records baseline `2.999`
and applies all V3/V4 migrations. Do not mount `sql/migration/` directly into
`/docker-entrypoint-initdb.d`; doing so attempts to execute V2 ALTER scripts against
an empty schema before the baseline exists.

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

This means the first `flyway:migrate` on a schema created from `sql/init.sql` marks
the current schema as baseline 2.999, then applies `V3_001` and later scripts only.

Do not run the V2 migration directory after importing `sql/init.sql`; the baseline
already contains the V2 schema.

## Migration order risk

- `V3_010__ai_call_log_enhancement_and_search_index_record.sql` and `V3_011__add_login_log_operation_log_notification.sql` are both idempotent compatibility migrations.
- Do not rename, reorder, or edit an applied migration file.
- Add a new `V3_xxx__*.sql` file for any fix, even if it only adjusts an existing table.

## Spring Boot integration

Business services keep startup migration disabled. Test and deployment environments
must run one dedicated Flyway job before starting application services. Enabling
Flyway independently in multiple microservices can race and is not supported.

For a dedicated migration application, use:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 2.999
```

Keep the dedicated migration job pointed at the repository `sql/migration/`
directory. Do not maintain a second copied migration tree.

## Safety: Demo data script isolated

The file `V4_009__clean_demo_business_data_and_seed_chinese_dataset.sql` has been moved from
`sql/migration/` to `sql/sandbox/`. It resets business/demo data (soft-deletes 20+ tables
and re-seeds Chinese demo data) and **must not** run in production.

Flyway will skip it automatically. To manually reset demo data in a local/dev/demo/test
schema, run:

```sql
source sql/sandbox/V4_009__clean_demo_business_data_and_seed_chinese_dataset.sql;
```
