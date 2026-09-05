package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V4_100MigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_100__v12_review_hardening.sql";

    @Test
    void migrationContainsEvidenceProjectionDurabilityContract() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("evidence_profile_feedback_outbox"));
        assertTrue(sql.contains("evidence_profile_feedback_lock"));
        assertTrue(sql.contains("evidence_usage_ability_projection"));
        assertTrue(sql.contains("uk_evidence_ability_projection_result_skill"));
        assertTrue(sql.contains("idx_evidence_ability_projection_user_skill_usage"));
        assertTrue(sql.contains("usage_id BIGINT NOT NULL"));
        assertTrue(sql.contains("evidence_projection_done"));
        assertTrue(sql.contains("ability_projection_done"));
        assertTrue(sql.contains("table_name = 'career_evidence_usage'"));
        assertTrue(sql.contains("uk_skill_profile_active_evidence"));
        assertTrue(sql.contains("uk_skill_gap_active_evidence"));
        assertTrue(sql.contains("INSERT IGNORE INTO evidence_profile_feedback_outbox"));
        assertTrue(sql.contains("MODIFY COLUMN `match_report_id` BIGINT NULL"));
        assertTrue(Pattern.compile(
                        "source_type\\s+VARCHAR\\(64\\)\\s+CHARACTER\\s+SET\\s+utf8mb4\\s+"
                                + "COLLATE\\s+utf8mb4_bin\\s+NOT\\s+NULL",
                        Pattern.CASE_INSENSITIVE)
                .matcher(sql)
                .find());
        assertTrue(Pattern.compile(
                        "k\\.source_type\\s*=\\s*g\\.source_type\\s+COLLATE\\s+utf8mb4_bin",
                        Pattern.CASE_INSENSITIVE)
                .matcher(sql)
                .find());
    }

    @Test
    void migrationContainsReplayClaimAndActiveIdempotencyContract() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("'interview_replay',"));
        assertTrue(sql.contains("'interview_remediation',"));
        assertTrue(sql.contains("'claim_token',"));
        assertTrue(sql.contains("'claimed_at',"));
        assertTrue(sql.contains("'active_idempotency_key',"));
        assertTrue(sql.contains("idx_interview_replay_claim_recovery"));
        assertTrue(sql.contains("idx_interview_remediation_claim_recovery"));
        assertTrue(sql.contains(
                "'VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"));
        assertTrue(Pattern.compile(
                        "MODIFY\\s+COLUMN\\s+claim_token\\s+"
                                + "VARCHAR\\(64\\)\\s+CHARACTER\\s+SET\\s+ascii",
                        Pattern.CASE_INSENSITIVE)
                .matcher(sql)
                .find());
        assertFalse(Pattern.compile(
                        "MODIFY\\s+COLUMN\\s+idempotency_key\\s+"
                                + "VARCHAR\\(64\\)\\s+CHARACTER\\s+SET\\s+ascii",
                        Pattern.CASE_INSENSITIVE)
                .matcher(sql)
                .find());
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
