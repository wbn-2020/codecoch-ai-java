package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V4_118StudyPlanDailyCoverageMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_118__study_plan_daily_coverage_prompt_contract.sql";
    private static final String INIT_SQL = "sql/init.sql";

    @Test
    void migrationAndFreshBaselineRequireCompleteDailyCoverage() throws IOException {
        String migration = normalized(read(MIGRATION));
        String initSql = normalized(read(INIT_SQL));

        assertTrue(migration.contains("'learning_plan_generate'"));
        assertTrue(migration.contains("'targeted_study_plan_generate'"));
        assertTrue(migration.contains("'v4-118-daily-coverage'"));
        assertTrue(migration.contains(
                "cover every integer day from 1 through expecteddurationdays without gaps"));
        assertTrue(migration.contains(
                "cover every integer day from 1 through availabledays without gaps"));
        assertTrue(migration.contains(
                "every day must contain at least one executable task"));
        assertTrue(initSql.contains(
                "cover every integer day from 1 through expecteddurationdays without gaps"));
        assertTrue(initSql.contains(
                "cover every integer day from 1 through availabledays without gaps"));
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
