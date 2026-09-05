package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class JobApplicationAttachmentScopeMigrationTest {

    private static final Path SQL_DIR = Path.of("..", "sql");
    private static final Path MIGRATION_DIR = SQL_DIR.resolve("migration");
    private static final String MIGRATION_NAME =
            "V4_119__job_application_attachment_scope_repair.sql";

    @Test
    void migrationRepairsLegacyApplicationAttachmentSchemaWithoutEditingHistory() throws Exception {
        try (var paths = Files.list(MIGRATION_DIR)) {
            List<Path> matches = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_119__"))
                    .toList();
            assertEquals(1, matches.size(), "V4_119 must have exactly one migration");
            assertEquals(MIGRATION_NAME, matches.get(0).getFileName().toString());
        }

        String sql = normalized(Files.readString(MIGRATION_DIR.resolve(MIGRATION_NAME)));

        assertContains(sql, "column_name = 'package_id' and is_nullable = 'no'");
        assertContains(sql, "modify column package_id bigint null");
        assertContains(sql, "index_name = 'uk_job_application_attachment_live_file'");
        assertContains(sql, "@index_columns <> 'active_file_id'");
        assertContains(sql,
                "add unique key uk_job_application_attachment_live_file (active_file_id)");
        assertContains(sql, "index_name = 'idx_job_application_attachment_application'");
        assertContains(sql,
                "@index_columns <> 'user_id,application_id,deleted,sort_order,id'");
        assertContains(sql,
                "add key idx_job_application_attachment_application "
                        + "(user_id, application_id, deleted, sort_order, id)");
        assertContains(sql, "constraint_name = 'chk_job_application_attachment_scope'");
        assertContains(sql,
                "check (package_id is not null or application_id is not null)");
        assertContains(sql, "'select 1'");
    }

    @Test
    void freshSchemaUsesTheSameApplicationAttachmentContract() throws Exception {
        String sql = normalized(Files.readString(SQL_DIR.resolve("init.sql")));
        int tableStart = sql.indexOf("create table if not exists job_application_attachment");
        assertTrue(tableStart >= 0, "fresh schema must define job_application_attachment");
        int tableEnd = sql.indexOf("engine=innodb", tableStart);
        assertTrue(tableEnd > tableStart, "attachment table definition must be complete");
        String table = sql.substring(tableStart, tableEnd);

        assertContains(table, "package_id bigint default null");
        assertContains(table,
                "unique key uk_job_application_attachment_live_file (active_file_id)");
        assertContains(table,
                "key idx_job_application_attachment_application "
                        + "(user_id, application_id, deleted, sort_order, id)");
        assertContains(table,
                "constraint chk_job_application_attachment_scope "
                        + "check (package_id is not null or application_id is not null)");
    }

    private static void assertContains(String sql, String expected) {
        assertTrue(sql.contains(expected), () -> "Expected SQL to contain: " + expected);
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s+\\)", ")")
                .trim();
    }
}
