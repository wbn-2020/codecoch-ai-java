package com.codecoachai.ai.agent.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CampaignActionDecisionMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_088__career_campaign_action_decision.sql";

    @Test
    void migrationUsesBinaryHashesAndGuardedBusinessUniqueness() throws IOException {
        String sql = normalized(read(MIGRATION));
        String table = tableBlock(sql, "career_campaign_action_decision");

        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.columns"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("prepare v4_088_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_088_stmt"), sql);
        assertTrue(sql.contains("create temporary table _v4_088_uniqueness_guard"), sql);
        assertTrue(sql.contains("having count(*) > 1"), sql);
        assertTrue(
                sql.indexOf("create temporary table _v4_088_uniqueness_guard")
                        < sql.lastIndexOf(
                                "add unique key "
                                        + "uk_career_campaign_action_decision_live_source"),
                sql);

        assertBinaryHash(table, "source_hash");
        assertBinaryHash(table, "idempotency_key_hash");
        assertBinaryHash(table, "payload_hash");
        assertTrue(table.contains(
                "live_semantic_source varchar(320) character set utf8mb4 "
                        + "collate utf8mb4_bin generated always as"), table);
        assertTrue(table.contains(
                "when deleted = 0 and active_guard = 1 "
                        + "then concat(semantic_key, '#', source_hash)"), table);
        assertTrue(table.contains(
                "unique key uk_career_campaign_action_decision_idempotency "
                        + "(user_id, idempotency_key_hash)"), table);
        assertTrue(table.contains(
                "unique key uk_career_campaign_action_decision_live_source "
                        + "(user_id, campaign_id, live_semantic_source)"), table);
    }

    @Test
    void initBaselineMatchesActionDecisionContract() throws IOException {
        String table = tableBlock(
                normalized(read("sql/init.sql")),
                "career_campaign_action_decision");

        assertBinaryHash(table, "source_hash");
        assertBinaryHash(table, "idempotency_key_hash");
        assertBinaryHash(table, "payload_hash");
        assertTrue(table.contains("active_guard tinyint not null default 1"), table);
        assertTrue(table.contains("live_semantic_source varchar(320)"), table);
        assertTrue(table.contains("generated always as"), table);
        assertTrue(table.contains(
                "uk_career_campaign_action_decision_idempotency"), table);
        assertTrue(table.contains(
                "uk_career_campaign_action_decision_live_source"), table);
    }

    private static void assertBinaryHash(String table, String column) {
        assertTrue(table.contains(
                column + " char(64) character set ascii collate ascii_bin"),
                table);
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
                Path.of(relative),
                Path.of("..").resolve(relative))) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Cannot locate " + relative);
    }
}
