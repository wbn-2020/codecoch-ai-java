package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_123AsyncTaskMessageIdCapacityMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_123__async_task_message_id_capacity.sql";
    @Test
    void migrationWidensBothMessageCorrelationColumnsWithoutDeletingData() throws IOException {
        String sql = normalized(Files.readString(repositoryRoot().resolve(MIGRATION)));

        assertTrue(sql.contains("table_name = 'async_task'"));
        assertTrue(sql.contains("table_name = 'message_dead_letter'"));
        assertTrue(sql.contains("column_name = 'message_id'"));
        assertTrue(sql.contains("requires async_task and message_dead_letter message_id columns from v3_007"));
        assertTrue(sql.contains("modify column message_id varchar(128) not null"));
        assertFalse(sql.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(sql.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(sql.matches("(?s).*\\bdrop\\s+table\\b.*"));
    }

    @Test
    void migrationLeavesAppliedHistoricalMigrationsImmutable() throws IOException {
        String historicalSql = normalized(Files.readString(
                repositoryRoot().resolve("sql/migration/V3_007__async_task_and_dead_letter.sql")));

        assertTrue(historicalSql.contains("message_id varchar(64) not null"));
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
