package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ResumeUploadDedupeGuardMigrationTest {

    private static final Path SQL_DIR = Path.of("..", "sql");
    private static final Path MIGRATION_DIR = SQL_DIR.resolve("migration");
    private static final String MIGRATION_NAME =
            "V4_120__resume_upload_content_dedupe_guard.sql";

    @Test
    void migrationAddsContentHashAndAtomicUserScopedGuard() throws Exception {
        try (var paths = Files.list(MIGRATION_DIR)) {
            List<Path> matches = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_120__"))
                    .toList();
            assertEquals(1, matches.size(), "V4_120 must have exactly one migration");
            assertEquals(MIGRATION_NAME, matches.get(0).getFileName().toString());
        }

        String sql = normalized(Files.readString(MIGRATION_DIR.resolve(MIGRATION_NAME)));
        assertContains(sql, "column_name = 'content_sha256'");
        assertContains(sql,
                "add column content_sha256 char(64) character set ascii collate ascii_bin null");
        assertContains(sql, "index_name = 'idx_file_info_resume_content'");
        assertContains(sql,
                "add key idx_file_info_resume_content "
                        + "(user_id, biz_type, content_sha256, status, deleted, id)");
        assertGuardContract(sql);
        assertContains(sql, "insert into resume_upload_dedupe_guard");
        assertContains(sql, "where existing.biz_type = 'resume'");
        assertContains(sql, "and existing.status = 'available'");
        assertContains(sql, "and existing.deleted = 0");
        assertContains(sql, "group by existing.user_id, existing.content_sha256");
        assertContains(sql, "on duplicate key update file_id = values(file_id)");
    }

    @Test
    void freshSchemaUsesTheSameGuardContract() throws Exception {
        String sql = normalized(Files.readString(SQL_DIR.resolve("init.sql")));
        assertContains(sql,
                "content_sha256 char(64) character set ascii collate ascii_bin default null");
        assertContains(sql,
                "key idx_file_info_resume_content "
                        + "(user_id, biz_type, content_sha256, status, deleted, id)");
        assertGuardContract(sql);
    }

    private static void assertGuardContract(String sql) {
        assertContains(sql, "create table if not exists resume_upload_dedupe_guard");
        assertContains(sql,
                "content_sha256 char(64) character set ascii collate ascii_bin not null");
        assertContains(sql, "file_id bigint default null");
        assertContains(sql, "primary key (user_id, content_sha256)");
        assertContains(sql, "key idx_resume_upload_guard_file (file_id)");
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
