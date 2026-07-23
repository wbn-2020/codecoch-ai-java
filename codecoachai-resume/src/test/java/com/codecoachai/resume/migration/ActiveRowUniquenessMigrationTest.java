package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ActiveRowUniquenessMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("..", "sql", "migration");
    private static final String MIGRATION_NAME =
            "V4_069__active_row_uniqueness_and_import_dedupe_guard.sql";

    @Test
    void migrationDefinesForwardOnlyActiveRowUniquenessContract() throws Exception {
        List<Path> versionMigrations;
        try (var paths = Files.list(MIGRATION_DIR)) {
            versionMigrations = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_069__"))
                    .toList();
        }

        assertEquals(1, versionMigrations.size(), "V4_069 must have exactly one migration");
        assertEquals(MIGRATION_NAME, versionMigrations.get(0).getFileName().toString());

        String sql = normalized(Files.readString(MIGRATION_DIR.resolve(MIGRATION_NAME)));

        assertContains(sql, "create table if not exists career_import_dedupe_guard");
        assertContains(sql, "user_id bigint not null");
        assertContains(sql, "identity_hash char(64) not null");
        assertContains(sql, "created_at datetime not null default current_timestamp");
        assertContains(sql,
                "updated_at datetime not null default current_timestamp on update current_timestamp");
        assertContains(sql, "primary key (user_id, identity_hash)");
        assertContains(sql, "engine=innodb default charset=utf8mb4");

        assertContains(sql, "signal sqlstate '45000'");

        String jobApplicationPrecheck =
                extractPrecheck(sql, "duplicate_job_applications");
        assertContains(jobApplicationPrecheck, "from job_application");
        assertContains(jobApplicationPrecheck, "where deleted = 0");
        assertContains(jobApplicationPrecheck, "group by user_id, import_fingerprint");
        assertContains(jobApplicationPrecheck, "having count(1) > 1");

        String experimentAssignmentPrecheck =
                extractPrecheck(sql, "duplicate_experiment_assignments");
        assertContains(experimentAssignmentPrecheck, "from job_experiment_assignment");
        assertContains(experimentAssignmentPrecheck, "where deleted = 0");
        assertContains(experimentAssignmentPrecheck, "group by hypothesis_id, application_id");
        assertContains(experimentAssignmentPrecheck, "having count(1) > 1");

        String calendarEventPrecheck =
                extractPrecheck(sql, "duplicate_calendar_events");
        assertContains(calendarEventPrecheck, "from career_calendar_event");
        assertContains(calendarEventPrecheck, "where deleted = 0");
        assertContains(calendarEventPrecheck,
                "select user_id, external_uid collate utf8mb4_bin");
        assertContains(calendarEventPrecheck,
                "group by user_id, external_uid collate utf8mb4_bin");
        assertContains(calendarEventPrecheck, "having count(1) > 1");

        assertContains(sql,
                "active_import_fingerprint varchar(64) generated always as "
                        + "(case when deleted = 0 then import_fingerprint else null end) stored");
        assertContains(sql,
                "active_application_id bigint generated always as "
                        + "(case when deleted = 0 then application_id else null end) stored");
        assertContains(sql,
                "active_external_uid varchar(255) character set utf8mb4 collate utf8mb4_bin "
                        + "generated always as "
                        + "(case when deleted = 0 then external_uid else null end) stored");

        assertUniqueKey(sql, "uk_job_application_import_fingerprint",
                "user_id, active_import_fingerprint");
        assertUniqueKey(sql, "uk_jea_hypothesis_application",
                "hypothesis_id, active_application_id");
        assertUniqueKey(sql, "uk_cce_user_external_uid",
                "user_id, active_external_uid");
        assertFalse(Pattern.compile(
                        "unique key (uk_job_application_import_fingerprint"
                                + "|uk_jea_hypothesis_application"
                                + "|uk_cce_user_external_uid) \\([^)]*deleted")
                .matcher(sql)
                .find(), "replacement unique keys must not contain deleted");

        assertContains(sql, "information_schema.columns");
        assertContains(sql, "information_schema.statistics");
        assertContains(sql, "column_name = 'active_import_fingerprint'");
        assertContains(sql, "column_name = 'active_application_id'");
        assertContains(sql, "column_name = 'active_external_uid'");
        assertContains(sql, "index_name = 'uk_job_application_import_fingerprint'");
        assertContains(sql, "index_name = 'uk_jea_hypothesis_application'");
        assertContains(sql, "index_name = 'uk_cce_user_external_uid'");
        assertAtomicIndexReconciliation(
                sql,
                "job_application",
                "uk_job_application_import_fingerprint",
                "user_id,active_import_fingerprint",
                "active_import_fingerprint");
        assertAtomicIndexReconciliation(
                sql,
                "job_experiment_assignment",
                "uk_jea_hypothesis_application",
                "hypothesis_id,active_application_id",
                "active_application_id");
        assertAtomicIndexReconciliation(
                sql,
                "career_calendar_event",
                "uk_cce_user_external_uid",
                "user_id,active_external_uid",
                "active_external_uid");

        assertFalse(sql.contains("job_experiment_variant"));
        assertFalse(sql.contains("career_import_row"));
    }

    @Test
    void earlierMigrationsKeepTheirOriginalDefinitions() throws Exception {
        assertContains(normalized(Files.readString(MIGRATION_DIR.resolve(
                        "V4_065__job_experiment_attribution.sql"))),
                "unique key uk_jea_hypothesis_application "
                        + "(hypothesis_id, application_id, deleted)");
        assertContains(normalized(Files.readString(MIGRATION_DIR.resolve(
                        "V4_066__career_calendar_import.sql"))),
                "unique key uk_cce_user_external_uid (user_id, external_uid, deleted)");
        assertContains(normalized(Files.readString(MIGRATION_DIR.resolve(
                        "V4_068__career_import_application_fingerprint.sql"))),
                "unique key uk_job_application_import_fingerprint "
                        + "(user_id, import_fingerprint, deleted)");
    }

    private static void assertUniqueKey(String sql, String indexName, String columns) {
        assertContains(sql, "add unique key " + indexName + " (" + columns + ")");
    }

    private static void assertContains(String sql, String expected) {
        assertTrue(sql.contains(expected), () -> "Expected migration SQL to contain: " + expected);
    }

    private static void assertAtomicIndexReconciliation(
            String sql,
            String tableName,
            String indexName,
            String targetColumns,
            String generatedColumn) {
        String block = extractIndexReconciliation(sql, tableName, indexName);

        assertContains(block, "@index_columns <> '" + targetColumns + "'");
        assertContains(block,
                "'alter table " + tableName
                        + " drop index " + indexName
                        + ", add unique key " + indexName
                        + " (" + targetColumns.replace(",", ", ") + ")'");
        assertContains(block, "@index_columns is null");
        assertContains(block, "column_name = '" + generatedColumn + "'");
        assertContains(block,
                "'alter table " + tableName
                        + " add unique key " + indexName
                        + " (" + targetColumns.replace(",", ", ") + ")'");
        assertContains(block, "'select 1'");
    }

    private static String extractIndexReconciliation(
            String sql, String tableName, String indexName) {
        String indexLookup =
                "table_name = '" + tableName + "' and index_name = '" + indexName + "'";
        int lookup = sql.indexOf(indexLookup);
        assertTrue(lookup >= 0, () -> "Missing index lookup for: " + indexName);

        int start = sql.lastIndexOf("set @index_columns = (", lookup);
        assertTrue(start >= 0, () -> "Missing index reconciliation start for: " + indexName);

        String blockSuffix = "deallocate prepare stmt;";
        int end = sql.indexOf(blockSuffix, lookup);
        assertTrue(end >= 0, () -> "Missing index reconciliation end for: " + indexName);
        return sql.substring(start, end + blockSuffix.length());
    }

    private static String extractPrecheck(String sql, String derivedTableAlias) {
        String queryPrefix = "select count(1) into duplicate_count from (";
        String querySuffix = ") " + derivedTableAlias;
        int end = sql.indexOf(querySuffix);
        assertTrue(end >= 0, () -> "Missing precheck query block: " + derivedTableAlias);
        int start = sql.lastIndexOf(queryPrefix, end);
        assertTrue(start >= 0, () -> "Missing precheck query start: " + derivedTableAlias);
        return sql.substring(start + queryPrefix.length(), end).trim();
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
