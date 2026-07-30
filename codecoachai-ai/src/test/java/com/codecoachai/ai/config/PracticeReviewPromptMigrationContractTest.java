package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.service.PromptSceneContracts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PracticeReviewPromptMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_104__practice_review_prompt_contract.sql";

    @Test
    void migrationPublishesManagedChineseJsonPracticeReviewPrompt() throws IOException {
        String sql = Files.readString(repositoryRoot().resolve(MIGRATION));

        assertTrue(sql.contains(PromptSceneContracts.PRACTICE_ANSWER_REVIEW_SCENE));
        assertTrue(sql.contains(PromptSceneContracts.PRACTICE_ANSWER_REVIEW_VERSION));
        assertTrue(sql.contains("{{referenceAnswer}}"));
        assertTrue(sql.contains("{{userAnswer}}"));
        assertTrue(sql.contains("level 只能是 EXCELLENT、GOOD、NORMAL、WEAK"));
        assertTrue(sql.contains("所有面向用户的文本必须使用正式中文"));
        assertTrue(sql.contains("只输出一个合法 JSON 对象"));
        assertTrue(sql.contains("status = 'ACTIVE'"));
        assertTrue(sql.contains("is_active = 1"));
        assertTrue(sql.contains("template.active_version_id = version.id"));
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
