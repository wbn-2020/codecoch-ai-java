package com.codecoachai.ai.agent.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CampaignPulseMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_089__career_campaign_pulse.sql";

    @Test
    void migrationGuardsPulseAndImmutableSnapshotUniqueness() throws IOException {
        String sql = normalized(read(MIGRATION));
        String pulse = tableBlock(sql, "career_campaign_pulse");
        String snapshot = tableBlock(sql, "career_campaign_pulse_snapshot");
        String source = tableBlock(sql, "career_campaign_pulse_source");

        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.columns"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("prepare v4_089_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_089_stmt"), sql);
        assertTrue(sql.contains("create temporary table _v4_089_uniqueness_guard"), sql);
        assertTrue(sql.contains("having count(*) > 1"), sql);
        assertTrue(
                sql.indexOf("create temporary table _v4_089_uniqueness_guard")
                        < sql.lastIndexOf(
                                "add unique key "
                                        + "uk_career_campaign_pulse_snapshot_idempotency"),
                sql);

        assertBinaryHash(pulse, "generation_claim_fingerprint");
        assertTrue(pulse.contains(
                "live_campaign_id bigint generated always as ( "
                        + "case when deleted = 0 then campaign_id else null end ) stored"),
                pulse);
        assertTrue(pulse.contains(
                "unique key uk_career_campaign_pulse_live_campaign "
                        + "(user_id, live_campaign_id)"), pulse);

        for (String column : List.of(
                "input_hash",
                "generation_fingerprint",
                "idempotency_key_hash",
                "idempotency_payload_hash")) {
            assertBinaryHash(snapshot, column);
        }
        for (String column : List.of(
                "facts_json",
                "metrics_json",
                "changes_json",
                "drift_signals_json",
                "limits_json",
                "action_seeds_json",
                "narrative_json")) {
            assertTrue(snapshot.contains(column + " mediumtext not null"), column);
        }
        for (String index : List.of(
                "uk_career_campaign_pulse_snapshot_version",
                "uk_career_campaign_pulse_snapshot_input",
                "uk_career_campaign_pulse_snapshot_fingerprint",
                "uk_career_campaign_pulse_snapshot_idempotency")) {
            assertTrue(snapshot.contains("unique key " + index), index);
        }
        assertBinaryHash(source, "source_hash");
        assertFalse(snapshot.contains("on update current_timestamp"), snapshot);
        assertFalse(source.contains("on update current_timestamp"), source);
    }

    @Test
    void initBaselineMatchesCampaignPulseContract() throws IOException {
        String init = normalized(read("sql/init.sql"));
        String pulse = tableBlock(init, "career_campaign_pulse");
        String snapshot = tableBlock(init, "career_campaign_pulse_snapshot");
        String source = tableBlock(init, "career_campaign_pulse_source");

        assertTrue(pulse.contains("current_snapshot_id bigint default null"), pulse);
        assertTrue(pulse.contains("snapshot_version int not null default 0"), pulse);
        assertTrue(pulse.contains("generation_claim_token varchar(64) default null"), pulse);
        assertTrue(pulse.contains("live_campaign_id bigint generated always as"), pulse);
        assertTrue(
                pulse.contains("uk_career_campaign_pulse_live_campaign"), pulse);

        for (String column : List.of(
                "facts_json",
                "metrics_json",
                "changes_json",
                "drift_signals_json",
                "limits_json",
                "action_seeds_json",
                "narrative_json")) {
            assertTrue(snapshot.contains(column + " mediumtext not null"), column);
        }
        assertTrue(
                snapshot.contains("uk_career_campaign_pulse_snapshot_version"), snapshot);
        assertTrue(
                snapshot.contains("uk_career_campaign_pulse_snapshot_input"), snapshot);
        assertTrue(
                snapshot.contains("uk_career_campaign_pulse_snapshot_fingerprint"), snapshot);
        assertTrue(
                snapshot.contains("uk_career_campaign_pulse_snapshot_idempotency"), snapshot);
        assertBinaryHash(source, "source_hash");
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
