package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class JobApplicationRecoverableArchiveMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("..", "sql", "migration");
    private static final String MIGRATION_NAME = "V4_108__job_application_recoverable_archive.sql";

    @Test
    void migrationAddsRecoverableArchiveFieldsAndActiveListIndexIdempotently() throws Exception {
        try (var paths = Files.list(MIGRATION_DIR)) {
            List<Path> matches = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_108__"))
                    .toList();
            assertEquals(1, matches.size());
            assertEquals(MIGRATION_NAME, matches.get(0).getFileName().toString());
        }

        String sql = Files.readString(MIGRATION_DIR.resolve(MIGRATION_NAME))
                .toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("table_name = 'job_application'"), sql);
        assertTrue(sql.contains("column_name = 'archived_at'"), sql);
        assertTrue(sql.contains("add column archived_at datetime null"), sql);
        assertTrue(sql.contains("column_name = 'archive_reason'"), sql);
        assertTrue(sql.contains("add column archive_reason varchar(500) null"), sql);
        assertTrue(sql.contains("index_name = 'idx_job_application_user_archived'"), sql);
        assertTrue(sql.contains(
                "add key idx_job_application_user_archived (user_id, deleted, archived_at, updated_at, id)"), sql);
        assertTrue(sql.contains("'select 1'"), sql);
    }
}
