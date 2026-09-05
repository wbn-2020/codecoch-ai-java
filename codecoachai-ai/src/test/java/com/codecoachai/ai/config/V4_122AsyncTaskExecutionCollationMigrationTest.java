package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_122AsyncTaskExecutionCollationMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_122__async_task_execution_collation_alignment.sql";

    @Test
    void migrationAlignsTheCrossTableExecutionKeyWithoutDeletingData() throws IOException {
        String sql = normalized(Files.readString(repositoryRoot().resolve(MIGRATION)));

        assertTrue(sql.contains("table_name = 'async_task'"));
        assertTrue(sql.contains("column_name = 'execution_id'"));
        assertTrue(sql.contains("requires async_task.execution_id from v4_111"));
        assertTrue(sql.contains("modify column execution_id varchar(64) character set utf8mb4"
                + " collate utf8mb4_unicode_ci null"));
        assertFalse(sql.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(sql.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(sql.matches("(?s).*\\bdrop\\s+table\\b.*"));
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve(MIGRATION))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate repository root");
        }
        return current;
    }
}
