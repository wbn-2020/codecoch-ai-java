package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_106AiModelGovernanceMetadataMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_106__ai_model_governance_metadata.sql";

    @Test
    void migrationDocumentsPhysicalAndRuntimeDefaultScopesAndOnlyMarksPlaceholder() throws IOException {
        String sql = migrationSql();
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("physical uniqueness guard remains provider-scoped"));
        assertTrue(sql.contains("one active global default"));
        assertTrue(sql.contains("runtime routing requires one enabled global default"));
        assertTrue(sql.contains("governance_status"));
        assertTrue(sql.contains("governance_note"));
        assertTrue(sql.contains("'PLACEHOLDER'"));
        assertTrue(sql.contains("'default-chat'"));
        assertFalse(lower.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(lower.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(lower.matches("(?s).*\\bdrop\\s+table\\b.*"));
        assertFalse(lower.matches("(?s).*set\\s+`?enabled`?\\s*=.*"));
        assertFalse(lower.matches("(?s).*set\\s+`?default_model`?\\s*=.*"));
        assertFalse(lower.matches("(?s).*set\\s+`?deleted`?\\s*=.*"));
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
