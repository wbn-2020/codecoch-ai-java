package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.service.PromptSceneContracts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PromptContractMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_100__v12_review_hardening.sql";

    @Test
    void v13MigrationPublishesACompatibleActiveDailyPlanPrompt() throws IOException {
        String sql = Files.readString(repositoryRoot().resolve(MIGRATION));

        assertTrue(sql.contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_SCENE));
        assertTrue(sql.contains(PromptSceneContracts.JOB_COACH_DAILY_PLAN_VERSION));
        assertTrue(sql.contains("{{contextJson}}"));
        assertTrue(sql.contains("{{candidatesJson}}"));
        assertTrue(sql.contains("{{taskCount}}"));
        assertTrue(sql.contains("{{maxTotalMinutes}}"));
        assertTrue(sql.contains("SKILL_GAP_ITEM"));
        assertTrue(sql.contains("status = 'ACTIVE'"));
        assertTrue(sql.contains("is_active = 1"));
        assertTrue(sql.contains("active_version_id = @v4_100_prompt_version_id"));
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
