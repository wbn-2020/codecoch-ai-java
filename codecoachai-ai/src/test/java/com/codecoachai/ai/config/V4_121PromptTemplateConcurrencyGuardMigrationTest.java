package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_121PromptTemplateConcurrencyGuardMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_121__prompt_template_scene_concurrency_guard.sql";
    private static final String INIT_SQL = "sql/init.sql";

    @Test
    void migrationRepairsHistoricalConflictsAndAddsAtomicSceneGuards() throws IOException {
        String sql = normalized(Files.readString(repositoryRoot().resolve(MIGRATION)));

        assertTrue(sql.contains("update prompt_template loser join prompt_template winner"));
        assertTrue(sql.contains("set loser.enabled = 0, loser.status = 0, loser.deleted = 1"));
        assertTrue(sql.contains("case when deleted = 0 then scene else null end"));
        assertTrue(sql.contains("add unique key uk_prompt_template_live_scene (live_scene_guard)"));
        assertTrue(sql.contains("update prompt_template_version loser join prompt_template_version winner"));
        assertTrue(sql.contains("set loser.status = 'inactive', loser.is_active = 0"));
        assertTrue(sql.contains(
                "when deleted = 0 and status = 'active' and is_active = 1 then scene"));
        assertTrue(sql.contains("add unique key uk_prompt_version_active_scene (active_scene_guard)"));
        assertTrue(sql.contains(
                "add unique key uk_prompt_template_version (template_id, version_code)"));
        assertTrue(sql.contains("information_schema.columns"));
        assertTrue(sql.contains("information_schema.statistics"));
        assertFalse(sql.matches("(?s).*\\bdelete\\s+from\\b.*"));
        assertFalse(sql.matches("(?s).*\\btruncate\\s+table\\b.*"));
        assertFalse(sql.matches("(?s).*\\bdrop\\s+table\\b.*"));
    }

    @Test
    void freshSchemaUsesTheSameAtomicSceneGuards() throws IOException {
        String sql = normalized(Files.readString(repositoryRoot().resolve(INIT_SQL)));

        assertTrue(sql.contains("case when deleted = 0 then scene else null end"));
        assertTrue(sql.contains("unique key uk_prompt_template_live_scene (live_scene_guard)"));
        assertTrue(sql.contains(
                "case when deleted = 0 and status = 'active' and is_active = 1 then scene else null end"));
        assertTrue(sql.contains("unique key uk_prompt_version_active_scene (active_scene_guard)"));
        assertTrue(sql.contains("unique key uk_prompt_template_version (template_id, version_code)"));
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replace("''", "'")
                .replaceAll("\\s+", " ")
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s+\\)", ")")
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
