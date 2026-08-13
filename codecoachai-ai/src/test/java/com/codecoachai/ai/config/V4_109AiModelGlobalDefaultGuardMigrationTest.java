package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_109AiModelGlobalDefaultGuardMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_109__ai_model_global_default_guard.sql";

    @Test
    void migrationDeterministicallyKeepsOneActiveGlobalDefaultAndAddsIdempotentGuard() throws IOException {
        String lower = migrationSql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertTrue(lower.contains("where loser.deleted = 0 and loser.default_model = 1"));
        assertTrue(lower.contains("winner.sort_order"));
        assertTrue(lower.contains("winner.updated_at"));
        assertTrue(lower.contains("winner.id > loser.id"));
        assertTrue(lower.contains("set target.default_model = 0"));
        assertTrue(lower.contains("case when `deleted` = 0 and `default_model` = 1 then 1 else null end"));
        assertTrue(lower.contains("add unique key `uk_ai_model_one_global_default`"));
        assertTrue(lower.contains("not exists("));
        assertTrue(lower.contains("information_schema.columns"));
        assertTrue(lower.contains("information_schema.statistics"));
        assertFalse(lower.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(lower.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(lower.matches("(?s).*\\bdrop\\s+table\\b.*"));
        assertFalse(lower.matches("(?s).*set\\s+target\\.deleted\\s*=.*"));
        assertFalse(lower.matches("(?s).*set\\s+target\\.enabled\\s*=.*"));
    }

    private String migrationSql() throws IOException {
        return Files.readString(repositoryRoot().resolve(MIGRATION));
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
