package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_124AsyncExecutionIdCapacityMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_124__async_execution_id_capacity.sql";

    @Test
    void migrationWidensExecutionCorrelationColumnsAcrossAsyncRecords() throws IOException {
        String sql = normalized(Files.readString(repositoryRoot().resolve(MIGRATION)));

        assertTrue(sql.contains("table_name_value = 'async_task'"));
        assertTrue(sql.contains("table_name_value = 'agent_run'"));
        assertTrue(sql.contains("table_name_value = 'ai_call_log'"));
        assertTrue(sql.contains("column_name_value = 'execution_id'"));
        assertTrue(sql.contains("column_name_value = 'parent_execution_id'"));
        assertTrue(sql.contains("modify column execution_id varchar(128)"));
        assertTrue(sql.contains("modify column parent_execution_id varchar(128)"));
        assertFalse(sql.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(sql.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(sql.matches("(?s).*\\bdrop\\s+table\\b.*"));
    }

    @Test
    void migrationLeavesAppliedHistoricalMigrationsImmutable() throws IOException {
        String historicalSql = normalized(Files.readString(
                repositoryRoot().resolve("sql/migration/V4_111__async_execution_contract.sql")));

        assertTrue(historicalSql.contains("async_task', 'execution_id'"));
        assertTrue(historicalSql.contains("varchar(64) null"));
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
