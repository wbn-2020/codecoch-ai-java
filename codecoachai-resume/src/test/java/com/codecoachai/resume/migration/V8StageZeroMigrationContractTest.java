package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V8StageZeroMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_086__v7_lifecycle_and_evidence_contract_reconciliation.sql";

    @Test
    void stageZeroMigrationUsesForwardSafeGuardsForEveryRepair() throws IOException {
        String sql = read(MIGRATION);
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("information_schema"), sql);
        assertTrue(normalized.contains("not exists"), sql);
        assertTrue(sql.contains("PREPARE"), sql);
        assertTrue(sql.contains("DEALLOCATE PREPARE"), sql);
        assertTrue(normalized.contains("modify column"), sql);
        assertTrue(normalized.contains("job_application_event"), sql);
        assertTrue(normalized.contains("career_campaign_event"), sql);
        assertTrue(normalized.contains("career_campaign_review_snapshot"), sql);
        assertTrue(normalized.contains("request_hash"), sql);
        assertTrue(normalized.contains("result_lock_version"), sql);
        assertTrue(normalized.contains("evidence_manifest_json"), sql);
        assertTrue(normalized.contains("evidence_schema_version"), sql);
        assertTrue(normalized.contains("rule_version"), sql);
    }

    @Test
    void reviewSnapshotAddColumnGuardsCheckTableAndColumnNames() throws IOException {
        String sql = read(MIGRATION).toLowerCase(Locale.ROOT);
        assertAddColumnGuard(sql, "evidence_manifest_json");
        assertAddColumnGuard(sql, "evidence_schema_version");
        assertAddColumnGuard(sql, "rule_version");
    }

    private void assertAddColumnGuard(String sql, String columnName) {
        int addColumn = sql.indexOf("add column `" + columnName + "`");
        assertTrue(addColumn > 0, sql);
        String guard = sql.substring(Math.max(0, addColumn - 600), addColumn);

        assertTrue(guard.contains("table_name = 'career_campaign_review_snapshot'"), guard);
        assertTrue(guard.contains("column_name = '" + columnName + "'"), guard);
        assertFalse(guard.contains("table_name = '" + columnName + "'"), guard);
    }

    @Test
    void initBaselineContainsStageZeroMetadata() throws IOException {
        String init = read("sql/init.sql").toLowerCase(Locale.ROOT);
        String application = tableBlock(init, "job_application");
        String applicationEvent = tableBlock(init, "job_application_event");
        String campaignEvent = tableBlock(init, "career_campaign_event");
        String reviewSnapshot = tableBlock(init, "career_campaign_review_snapshot");
        assertTrue(application.contains("campaign_id bigint"), application);
        assertTrue(application.contains("lock_version int"), application);
        assertTrue(applicationEvent.contains("idempotency_key_hash char(64)"), applicationEvent);
        assertTrue(applicationEvent.contains("request_hash char(64)"), applicationEvent);
        assertTrue(applicationEvent.contains("result_lock_version int"), applicationEvent);
        assertTrue(campaignEvent.contains("request_hash char(64)"), campaignEvent);
        assertTrue(campaignEvent.contains("result_lock_version int"), campaignEvent);
        assertTrue(reviewSnapshot.contains("evidence_manifest_json mediumtext"), reviewSnapshot);
        assertTrue(reviewSnapshot.contains("evidence_schema_version varchar(24)"), reviewSnapshot);
        assertTrue(reviewSnapshot.contains("rule_version varchar(64)"), reviewSnapshot);
    }

    private String tableBlock(String sql, String tableName) {
        String marker = "create table if not exists " + tableName;
        int start = sql.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("无法定位基线表：" + tableName);
        }
        int end = sql.indexOf("\ncreate table", start + marker.length());
        return sql.substring(start, end < 0 ? sql.length() : end);
    }

    private String read(String relative) throws IOException {
        for (Path candidate : Path.of(relative).toAbsolutePath().normalize()
                .toFile().isFile()
                ? java.util.List.of(Path.of(relative))
                : java.util.List.of(Path.of(relative), Path.of("..").resolve(relative))) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("无法定位迁移文件：" + relative);
    }
}
