package com.codecoachai.resume.migration;

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
    void migrationGuardsAndAddsDecisionHistoryJson() throws IOException {
        String sql = normalized(read(
                "sql/migration/V4_094__evidence_usage_candidate_contract.sql"));
        String guard = columnGuardBlock(
                sql,
                "v4_094",
                "career_campaign_review_memory_candidate",
                "decision_history_json");

        assertTrue(guard.contains("not exists"), guard);
        assertTrue(guard.contains("information_schema.columns"), guard);
        assertTrue(guard.contains(
                "table_name = 'career_campaign_review_memory_candidate'"), guard);
        assertTrue(guard.contains("column_name = 'decision_history_json'"), guard);
        assertTrue(guard.contains(
                "add column decision_history_json text null after decision_payload_hash"), guard);
    }

    @Test
    void initBaselineIncludesDecisionHistoryJsonInTheSameDecisionSequence()
            throws IOException {
        String candidate = tableBlock(
                normalized(read("sql/init.sql")),
                "career_campaign_review_memory_candidate");

        int payloadHash = candidate.indexOf(
                "decision_payload_hash char(64) character set ascii collate ascii_bin default null");
        int history = candidate.indexOf("decision_history_json text default null");
        int decisionAt = candidate.indexOf("decision_at datetime default null");

        assertTrue(payloadHash >= 0, candidate);
        assertTrue(history > payloadHash, candidate);
        assertTrue(decisionAt > history, candidate);
    }

    private static String columnGuardBlock(
            String sql, String migration, String tableName, String columnName) {
        String marker = "column_name = '" + columnName + "'";
        int column = sql.indexOf(marker);
        assertTrue(column >= 0, marker);
        int start = sql.lastIndexOf("set @" + migration + "_sql = if(", column);
        int end = sql.indexOf(
                "deallocate prepare " + migration + "_stmt", column);
        assertTrue(start >= 0 && end > start, marker);
        String block = sql.substring(start, end);
        assertTrue(block.contains("table_name = '" + tableName + "'"), block);
        return block;
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
        for (Path candidate : List.of(
                Path.of(relative), Path.of("..").resolve(relative))) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Cannot locate " + relative);
    }
}
