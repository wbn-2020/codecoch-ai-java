package com.codecoachai.interview.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InterviewRemediationComparisonMigrationTest {

    @Test
    void migrationContainsSourceFieldsRubricVersionAndConcurrencyGuards() throws Exception {
        Path migration = Path.of("..", "sql", "migration",
                "V4_060__interview_remediation_comparison.sql");
        String sql = Files.readString(migration);

        assertTrue(sql.contains("source_report_id"));
        assertTrue(sql.contains("source_requirement_ids"));
        assertTrue(sql.contains("practice_purpose"));
        assertTrue(sql.contains("rubric_version"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `interview_remediation`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `interview_comparison`"));
        assertTrue(sql.contains("uk_interview_remediation_user_token"));
        assertTrue(sql.contains("uk_interview_comparison_user_token"));
        assertTrue(sql.contains("idx_interview_comparison_user_created"));
    }
}
