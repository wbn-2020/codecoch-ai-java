package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_116AiMockDisabledMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_116__disable_ai_mock_acceptance_config.sql";
    private static final String INIT_SQL = "sql/init.sql";

    @Test
    void migrationAndFreshBaselineRetireTheMisleadingDatabaseMockSwitch() throws IOException {
        String migration = normalized(read(MIGRATION));
        String initSql = normalized(read(INIT_SQL));

        assertTrue(migration.contains("update system_config set config_value = 'false'"));
        assertTrue(migration.contains("where config_key = 'ai.mock.enabled'"));
        assertTrue(migration.contains("where not exists"));
        assertTrue(migration.contains("'ai.mock.enabled', 'false', 'boolean'"));
        assertTrue(migration.contains("status = 0"));
        assertTrue(migration.contains("does not control runtime mock mode"));
        assertTrue(initSql.contains(
                "(3, 'ai.mock.enabled', 'false', 'boolean', "
                        + "'deprecated: this legacy database record does not control runtime mock mode; "
                        + "configure codecoachai.ai.mock-enabled in nacos or spring runtime configuration', 0)"));
        assertFalse(migration.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(migration.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(migration.matches("(?s).*\\bdrop\\s+table\\b.*"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath));
    }

    private String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
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
