package com.codecoachai.ai.agent.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V9EvidenceUsageCandidateMigrationContractTest {

    @Test
    void migrationExpandsCandidateScopeDecisionAndMemoryPromotionContracts() throws IOException {
        String sql = normalized(read("sql/migration/V4_094__evidence_usage_candidate_contract.sql"));
        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.columns"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("prepare v4_094_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_094_stmt"), sql);
        assertTrue(sql.contains("modify column review_id bigint null"), sql);
        assertTrue(sql.contains("modify column snapshot_id bigint null"), sql);
        for (String column : List.of(
                "candidate_scope_type", "candidate_scope_key", "candidate_type",
                "usage_source_hash", "evidence_count", "sample_count", "limits_json",
                "decision_code", "decision_payload_hash", "decision_history_json", "decision_at",
                "promoted_memory_id")) {
            assertTrue(sql.contains("add column " + column), column);
        }
        assertTrue(sql.contains("add column live_semantic_hash"), sql);
        assertTrue(sql.contains("modify column live_semantic_hash"), sql);
        assertTrue(sql.contains(
                "when deleted = 0 and status not in (''rejected'', ''expired'')"), sql);
        assertTrue(sql.contains(
                "add unique key uk_campaign_review_memory_live_semantic "
                        + "(user_id, live_semantic_hash)"), sql);
        assertTrue(sql.contains("idx_campaign_review_memory_scope"), sql);
        assertTrue(sql.contains("add column promotion_key_hash"), sql);
        assertTrue(sql.contains("uk_agent_memory_promotion"), sql);
    }

    @Test
    void everyDynamicOperationChecksItsTargetTableExists() throws IOException {
        String sql = normalized(read("sql/migration/V4_094__evidence_usage_candidate_contract.sql"));
        String setMarker = "set @v4_094_sql = if(";
        String endMarker = "deallocate prepare v4_094_stmt";
        int cursor = 0;
        int operationCount = 0;

        while (true) {
            int start = sql.indexOf(setMarker, cursor);
            if (start < 0) {
                break;
            }
            int end = sql.indexOf(endMarker, start);
            assertTrue(end > start, sql.substring(start));
            String block = sql.substring(start, end);
            String tableName = targetTable(block);
            assertTrue(tableName != null, block);
            assertTrue(block.contains(
                    "from information_schema.tables "
                            + "where table_schema = @v4_094_schema_name "
                            + "and table_name = '" + tableName + "'"), block);
            operationCount++;
            cursor = end + endMarker.length();
        }

        assertTrue(operationCount >= 24, "Unexpected guarded operation count: " + operationCount);
    }

    @Test
    void migrationBackfillsAgentMemoryBeforeApplyingNotNullDefinitions() throws IOException {
        String sql = normalized(read("sql/migration/V4_094__evidence_usage_candidate_contract.sql"));
        int backfill = sql.indexOf("update agent_memory");
        int createdAt = sql.indexOf(
                "modify column created_at datetime not null default current_timestamp");
        int updatedAt = sql.indexOf(
                "modify column updated_at datetime not null "
                        + "default current_timestamp on update current_timestamp");
        int deleted = sql.indexOf(
                "modify column deleted tinyint not null default 0");

        assertTrue(backfill >= 0, sql);
        assertTrue(sql.contains("created_at = coalesce(created_at, current_timestamp)"), sql);
        assertTrue(sql.contains("updated_at = coalesce(updated_at, current_timestamp)"), sql);
        assertTrue(sql.contains("deleted = coalesce(deleted, 0)"), sql);
        assertTrue(createdAt > backfill, sql);
        assertTrue(updatedAt > backfill, sql);
        assertTrue(deleted > backfill, sql);
    }

    @Test
    void initBaselineMatchesCandidateAndMemoryPromotionContract() throws IOException {
        String init = normalized(read("sql/init.sql"));
        String candidate = tableBlock(init, "career_campaign_review_memory_candidate");
        String memory = tableBlock(init, "agent_memory");
        String mapper = normalized(read(
                "codecoachai-ai/src/main/java/com/codecoachai/ai/agent/campaignreview/mapper/"
                        + "CareerCampaignReviewMemoryCandidateMapper.java"));

        assertTrue(candidate.contains("review_id bigint default null"), candidate);
        assertTrue(candidate.contains("snapshot_id bigint default null"), candidate);
        assertTrue(candidate.contains("candidate_scope_type varchar(32) default null"), candidate);
        assertTrue(candidate.contains("decision_code varchar(16) default null"), candidate);
        assertTrue(candidate.contains("decision_history_json text default null"), candidate);
        assertTrue(candidate.contains("status varchar(24) not null default 'pending'"), candidate);
        assertTrue(candidate.contains(
                "live_semantic_hash char(64) character set ascii collate ascii_bin"), candidate);
        assertTrue(candidate.contains(
                "when deleted = 0 and status not in ('rejected', 'expired') "
                        + "then semantic_hash else null end"), candidate);
        assertTrue(candidate.contains(
                "unique key uk_campaign_review_memory_live_semantic "
                        + "(user_id, live_semantic_hash)"), candidate);
        assertTrue(candidate.contains("idx_campaign_review_memory_scope"), candidate);
        assertTrue(memory.contains(
                "promotion_key_hash char(64) character set ascii collate ascii_bin default null"),
                memory);
        assertTrue(memory.contains("uk_agent_memory_promotion"), memory);
        assertTrue(memory.contains(
                "created_at datetime not null default current_timestamp"), memory);
        assertTrue(memory.contains(
                "updated_at datetime not null default current_timestamp "
                        + "on update current_timestamp"), memory);
        assertTrue(memory.contains("deleted tinyint not null default 0"), memory);
        assertTrue(mapper.contains("coalesce(#{status}, 'pending_confirmation')"), mapper);
    }

    private static String targetTable(String block) {
        if (block.contains("alter table career_campaign_review_memory_candidate")) {
            return "career_campaign_review_memory_candidate";
        }
        if (block.contains("alter table agent_memory") || block.contains("update agent_memory")) {
            return "agent_memory";
        }
        return null;
    }

    private static String tableBlock(String sql, String tableName) {
        String marker = "create table if not exists " + tableName + " (";
        int start = sql.indexOf(marker);
        assertTrue(start >= 0, tableName);
        int end = sql.indexOf(") engine=innodb", start);
        assertTrue(end > start, tableName);
        return sql.substring(start, end);
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String read(String relative) throws IOException {
        for (Path candidate : List.of(Path.of(relative), Path.of("..").resolve(relative))) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Cannot locate " + relative);
    }
}
