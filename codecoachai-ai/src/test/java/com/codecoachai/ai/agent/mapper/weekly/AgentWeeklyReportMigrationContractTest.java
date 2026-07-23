package com.codecoachai.ai.agent.mapper.weekly;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AgentWeeklyReportMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_076__agent_weekly_report.sql";

    @Test
    void migrationChecksEveryWeeklyTableColumnAndNamedIndex() throws IOException {
        String sql = normalizedMigration();

        Map<String, List<String>> columns = Map.of(
                "agent_weekly_report",
                List.of(
                        "id", "user_id", "target_job_id", "target_scope_key",
                        "week_start_date", "week_end_date", "timezone",
                        "current_snapshot_id", "report_status", "snapshot_version",
                        "summary", "confidence_level", "fallback", "fallback_reason",
                        "generation_claim_fingerprint", "generation_claim_token",
                        "generation_claim_idempotency_key_hash",
                        "generation_claim_payload_hash", "generation_claimed_at",
                        "created_at", "updated_at", "deleted", "live_identity_key"),
                "agent_weekly_report_snapshot",
                List.of(
                        "id", "user_id", "weekly_report_id", "snapshot_version",
                        "week_start_date", "week_end_date", "target_scope_key",
                        "range_start_utc", "range_end_utc", "source_cutoff_at",
                        "input_hash", "generation_fingerprint",
                        "idempotency_key_hash", "idempotency_payload_hash",
                        "request_id", "calculation_version", "prompt_schema_version",
                        "output_schema_version", "report_status", "summary",
                        "confidence_level", "facts_json", "signals_json",
                        "hypotheses_json", "experiment_suggestions_json",
                        "plan_draft_json", "coverage_json", "result_source",
                        "fallback", "fallback_reason", "trace_id", "ai_call_log_id",
                        "generated_at", "created_at", "updated_at", "deleted"),
                "agent_weekly_report_source",
                List.of(
                        "id", "user_id", "snapshot_id", "source_type", "source_id",
                        "source_time", "source_updated_at", "scope_key",
                        "inclusion_status", "exclude_reason", "source_hash",
                        "safe_summary", "metadata_json", "created_at", "updated_at",
                        "deleted"));

        columns.forEach((table, tableColumns) -> {
            assertTrue(
                    sql.contains("create table if not exists `" + table + "`"),
                    table);
            tableColumns.forEach(column ->
                    assertColumnRepair(sql, table, column));
        });

        List<String> indexes = List.of(
                "uk_aw_report_identity",
                "idx_awr_user_week",
                "idx_awr_current_snapshot",
                "uk_awrs_version",
                "uk_awrs_input_hash",
                "uk_awrs_generation_fingerprint",
                "uk_awrs_idempotency",
                "idx_awrs_scope_week",
                "idx_awrs_cutoff",
                "idx_awrs_source_snapshot",
                "idx_awrs_source_lookup");
        assertTrue(sql.contains(
                "create temporary table `_v4_076_expected_index`"), sql);
        assertTrue(sql.contains("from information_schema.statistics"), sql);
        assertTrue(sql.contains(
                "actual.index_columns <> expected.index_columns"), sql);
        assertTrue(sql.contains(
                "actual.non_unique <> expected.non_unique"), sql);
        assertTrue(sql.contains("set @v4_076_report_index_drop ="), sql);
        assertTrue(sql.contains("set @v4_076_report_index_add ="), sql);
        assertTrue(sql.contains("set @v4_076_report_index_sql = if("), sql);
        assertTrue(sql.contains("set @v4_076_snapshot_index_drop ="), sql);
        assertTrue(sql.contains("set @v4_076_snapshot_index_add ="), sql);
        assertTrue(sql.contains("set @v4_076_snapshot_index_sql = if("), sql);
        assertTrue(sql.contains("set @v4_076_source_index_drop ="), sql);
        assertTrue(sql.contains("set @v4_076_source_index_add ="), sql);
        assertTrue(sql.contains("set @v4_076_source_index_sql = if("), sql);
        assertTrue(
                sql.indexOf("recover all source columns")
                        < sql.indexOf("set @sql = @v4_076_report_index_sql"),
                sql);
        indexes.forEach(index ->
                assertTrue(sql.contains("'" + index + "'"), index));
    }

    @Test
    void liveIdentityAndSnapshotHistoryUniquenessDoNotUseDeletedTombstones()
            throws IOException {
        String report = createTableBlock(normalizedMigration(), "agent_weekly_report");
        String snapshot =
                createTableBlock(normalizedMigration(), "agent_weekly_report_snapshot");

        assertTrue(report.contains(
                "generated always as ( case when `deleted` = 0 "
                        + "then `target_scope_key` else null end ) stored"), report);
        assertTrue(report.contains(
                "unique key `uk_aw_report_identity` "
                        + "(`user_id`, `week_start_date`, `timezone`, "
                        + "`live_identity_key`)"), report);
        assertFalse(indexDefinition(report, "uk_aw_report_identity").contains("deleted"));

        assertTrue(snapshot.contains(
                "unique key `uk_awrs_version` "
                        + "(`weekly_report_id`, `snapshot_version`)"), snapshot);
        assertTrue(snapshot.contains(
                "unique key `uk_awrs_input_hash` "
                        + "(`weekly_report_id`, `input_hash`, `calculation_version`, "
                        + "`prompt_schema_version`, `output_schema_version`)"), snapshot);
        assertTrue(snapshot.contains(
                "unique key `uk_awrs_generation_fingerprint` "
                        + "(`weekly_report_id`, `generation_fingerprint`)"), snapshot);
        assertTrue(snapshot.contains(
                "unique key `uk_awrs_idempotency` "
                        + "(`user_id`, `idempotency_key_hash`)"), snapshot);
        assertTrue(snapshot.contains(
                "`idempotency_key_hash` char(64) default null"), snapshot);

        for (String index : List.of(
                "uk_awrs_version",
                "uk_awrs_input_hash",
                "uk_awrs_generation_fingerprint",
                "uk_awrs_idempotency")) {
            assertFalse(indexDefinition(snapshot, index).contains("deleted"), index);
        }
    }

    @Test
    void snapshotAndSourceTimestampsAreImmutableAndOldDraftsAreRepaired()
            throws IOException {
        String sql = normalizedMigration();
        String snapshot = createTableBlock(sql, "agent_weekly_report_snapshot");
        String source = createTableBlock(sql, "agent_weekly_report_source");

        assertFalse(snapshot.contains("on update current_timestamp"), snapshot);
        assertFalse(source.contains("on update current_timestamp"), source);
        assertTrue(sql.contains(
                "table_name = 'agent_weekly_report_snapshot' "
                        + "and column_name = 'updated_at' "
                        + "and lower(extra) like '%on update%'"), sql);
        assertTrue(sql.contains(
                "modify column `updated_at` datetime not null default current_timestamp"),
                sql);
        assertTrue(sql.contains(
                "set `generation_fingerprint` = sha2( concat( ''[\"''"),
                sql);
        assertTrue(source.contains("`source_hash` varchar(80) default null"), source);
    }

    @Test
    void draftValuesAreNormalizedBeforeUniqueIndexesAreRepaired() throws IOException {
        String sql = normalizedMigration();

        int promptNormalization = sql.indexOf(
                "set `prompt_schema_version` = ''none''");
        int fingerprintBackfill = sql.indexOf(
                "set `generation_fingerprint` = sha2(");
        int uniqueIndexRepair = sql.indexOf(
                "set @sql = @v4_076_snapshot_index_sql");

        assertTrue(promptNormalization >= 0, sql);
        assertTrue(fingerprintBackfill > promptNormalization, sql);
        assertTrue(uniqueIndexRepair > fingerprintBackfill, sql);
    }

    private static void assertColumnRepair(String sql, String table, String column) {
        Pattern pattern = Pattern.compile(
                "table_name\\s*=\\s*'"
                        + Pattern.quote(table)
                        + "'.{0,420}?column_name\\s*=\\s*'"
                        + Pattern.quote(column)
                        + "'",
                Pattern.DOTALL);
        assertTrue(pattern.matcher(sql).find(), table + "." + column);
    }

    private static String indexDefinition(String tableBlock, String indexName) {
        Pattern pattern = Pattern.compile(
                "(?:unique\\s+)?key\\s+`"
                        + Pattern.quote(indexName)
                        + "`\\s*\\([^)]*\\)");
        var matcher = pattern.matcher(tableBlock);
        assertTrue(matcher.find(), indexName);
        return matcher.group();
    }

    private static String createTableBlock(String sql, String table) {
        Pattern pattern = Pattern.compile(
                "create table if not exists `"
                        + Pattern.quote(table)
                        + "`\\s*\\(.*?\\) engine=innodb",
                Pattern.DOTALL);
        var matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), table);
        return matcher.group();
    }

    private static String normalizedMigration() throws IOException {
        return Files.readString(resolveMigration(), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Path resolveMigration() {
        for (Path candidate : List.of(
                Path.of(MIGRATION),
                Path.of("..").resolve(MIGRATION))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Cannot locate " + MIGRATION);
    }
}
