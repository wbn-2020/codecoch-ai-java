package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V4_101ResumeMatchEvidenceBoundaryMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_101__resume_match_evidence_boundary.sql";

    @Test
    void migrationActivatesStrictEvidenceBoundResumeMatchPrompt() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("'RESUME_JOB_MATCH'"));
        assertTrue(sql.contains("'v4-101-evidence-bound'"));
        assertTrue(sql.contains("resumeVersionId: {{resumeVersionId}}"));
        assertTrue(sql.contains("不得使用行业常识补全事实"));
        assertTrue(sql.contains("不得把通用能力要求推断成某个具体实现"));
        assertTrue(sql.contains("recommendedLearningTopics"));
        assertTrue(sql.contains("recommendedInterviewTopics"));
        assertTrue(sql.contains("version.status = 'ACTIVE'"));
        assertTrue(sql.contains("template.active_version_id = version.id"));
    }

    @Test
    void migrationDoesNotSeedTechnologyNamesThatCouldBeEchoedAsEvidence() throws IOException {
        String sql = migrationSql().toLowerCase();

        assertFalse(sql.contains("seata"));
        assertFalse(sql.contains("nacos"));
        assertFalse(sql.contains("kubernetes"));
        assertFalse(sql.contains("k8s"));
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
