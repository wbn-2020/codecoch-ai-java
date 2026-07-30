package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V4_102InterviewReportScoringContractMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_102__interview_report_scoring_contract.sql";
    private static final String VERSION_CODE = "v4-102-interview-score-contract";

    @Test
    void migrationActivatesPromptThatMatchesPersistedScoringContract() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("'INTERVIEW_REPORT_GENERATE'"));
        assertTrue(VERSION_CODE.length() <= 32);
        assertTrue(sql.contains("'" + VERSION_CODE + "'"));
        assertTrue(sql.contains("Role、Type、Question、CandidateAnswer、AiComment、Score、Content"));
        assertTrue(sql.contains("totalScore 必须按真实逐题评分计算"));
        assertTrue(sql.contains("qaReview 必须与有效回答逐条对应"));
        assertTrue(sql.contains("rubricScores 必须是非空数组"));
        assertTrue(sql.contains("fallback=true"));
        assertTrue(sql.contains("sampleInsufficient=true"));
        assertTrue(sql.contains("version.status = 'ACTIVE'"));
        assertTrue(sql.contains("template.active_version_id = version.id"));
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
