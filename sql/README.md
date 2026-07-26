# Flyway Database Migrations

Use Flyway to manage the versioned scripts under `sql/migration/`.

`sql/init.sql` is the single bootstrap baseline for a fresh database. It is treated
as Flyway version `2.999`; only `V3_001` and later migrations run after that
baseline.

Baseline ownership rules:

- `sql/init.sql` is imported exactly once, before Flyway records baseline `2.999`.
- New V3/V4 schema and data changes belong only in `sql/migration/`.
- Do not copy a migration-owned column or index back into `sql/init.sql`.
- Compatibility objects already present in the historical baseline are tolerated
  only when their later migration is explicitly replay-safe.
- Offline contract tests reject an unguarded `ALTER TABLE ... ADD` when the same
  column or index already exists in `sql/init.sql`.
- The baseline creates roles but never creates a login-capable administrator.

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

## Administrator bootstrap

Neither `sql/init.sql` nor a Flyway migration creates a usable administrator with a
fixed password. After the migration job succeeds, an operator may bootstrap the
first administrator with `sql/bootstrap/bootstrap_admin.sql`.

The bootstrap script:

- accepts only an operator-chosen 4-32 character username and a 60-character
  BCrypt hash through MySQL session variables;
- never accepts or stores plaintext;
- requires BCrypt cost 12-31 and validates the complete hash alphabet;
- refuses to run while an enabled administrator already exists;
- refuses to reuse an existing username;
- serializes concurrent attempts with a schema-scoped MySQL advisory lock;
- creates the user and ADMIN role relation in one transaction.

Use one interactive MySQL session so the variables and `SOURCE` command share the
same connection:

```sql
SET @bootstrap_admin_username = '<operator-chosen-username>';
SET @bootstrap_admin_password_hash = '<bcrypt-hash-generated-offline>';
SET @bootstrap_admin_nickname = '<display-name>';
SET @bootstrap_admin_email = NULL;
SOURCE sql/bootstrap/bootstrap_admin.sql;
```

Generate a strong random password and its BCrypt hash with an approved offline
tool. Do not place the plaintext password, hash, or connection credential in Git,
shell history, process arguments, deployment manifests, or chat logs. Clear the
interactive terminal history according to the environment's operating procedure.

`V4_096__disable_legacy_default_admin.sql` disables only the historical seed whose
username matches `admin` and whose BCrypt hash byte-for-byte matches the known
compromised repository seed. The byte comparison is required because the password
column inherits a case-insensitive collation. The migration replaces the credential
with a valid cost-12 BCrypt hash generated from a discarded random secret, then
disables the account. Accounts whose password was already rotated are not changed.

## P0 recovery migration preflight

`V4_096` and `V4_097` require MySQL 8.0 and must be applied by the dedicated Flyway
job while application writes are stopped. Before migrating a real environment:

1. Take and verify a restorable database backup or point-in-time recovery marker.
2. Confirm the target schema and server version:

   ```sql
   SELECT DATABASE() AS target_schema, VERSION() AS mysql_version;
   ```

3. Run `mvn flyway:validate` and `mvn flyway:info`; do not continue if applied
   migration checksums differ or the history is failed/out of order.
4. Record the exact legacy-admin candidates. A row is changed only when this query
   returns it:

   ```sql
   SELECT id, username, status, deleted
   FROM sys_user
   WHERE username = 'admin'
     AND BINARY password =
         BINARY '$2a$10$OuTN8naVk6kfkcyMNiSf.eO3rCVpGr2j7RL.iQvHkM6H/AJoFVtHG'
     AND deleted = 0;
   ```

5. Confirm there are no duplicate active role-menu relations:

   ```sql
   SELECT role_id, menu_id, COUNT(*) AS duplicate_count
   FROM sys_role_menu
   WHERE deleted = 0
   GROUP BY role_id, menu_id
   HAVING COUNT(*) > 1;
   ```

`V4_097` adds a stored generated column and rebuilds `uk_role_menu`; MySQL may lock
or rebuild `sys_role_menu` during the DDL. Schedule a write outage even though this
relation table is expected to be small.

After Flyway reaches `V4_097`, verify:

```sql
SELECT version, success
FROM flyway_schema_history
WHERE version IN ('4.096', '4.097')
ORDER BY installed_rank;

SELECT id, username, status
FROM sys_user
WHERE username = 'admin'
  AND BINARY password =
      BINARY '$2a$10$OuTN8naVk6kfkcyMNiSf.eO3rCVpGr2j7RL.iQvHkM6H/AJoFVtHG';

SHOW COLUMNS FROM sys_role_menu LIKE 'active_menu_id';
SHOW INDEX FROM sys_role_menu WHERE Key_name = 'uk_role_menu';
```

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
