package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V9EvidenceUsageResultMigrationContractTest {

    @Test
    void migrationDefinesResultRootAndAppendOnlySnapshots() throws IOException {
        String sql = normalized(read("sql/migration/V4_092__evidence_usage_result_feedback.sql"));
        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("prepare v4_092_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_092_stmt"), sql);

        String root = tableBlock(sql, "career_evidence_usage_result");
        for (String column : List.of(
                "usage_id", "application_id", "event_type", "event_key_hash",
                "current_snapshot_id", "snapshot_version", "status", "lock_version")) {
            assertTrue(root.contains(column + " "), column);
        }
        assertHash(root, "event_key_hash");
        assertTrue(root.contains(
                "unique key uk_evidence_usage_result_event "
                        + "(user_id, usage_id, event_key_hash)"), root);

        String snapshot = tableBlock(sql, "career_evidence_usage_result_snapshot");
        for (String column : List.of(
                "status", "outcome_code", "known_facts_json", "external_feedback_text",
                "user_interpretation_text", "unknowns_json", "limits_json",
                "source_type", "source_hash", "content_hash", "idempotency_key_hash",
                "idempotency_payload_hash", "supersedes_snapshot_id")) {
            assertTrue(snapshot.contains(column + " "), column);
        }
        for (String column : List.of(
                "source_hash", "content_hash", "idempotency_key_hash",
                "idempotency_payload_hash")) {
            assertHash(snapshot, column);
        }
        assertTrue(snapshot.contains(
                "unique key uk_evidence_usage_result_snapshot_version "
                        + "(result_id, snapshot_version)"), snapshot);
        assertTrue(snapshot.contains(
                "unique key uk_evidence_usage_result_snapshot_idempotency "
                        + "(result_id, idempotency_key_hash)"), snapshot);
        assertFalse(snapshot.contains("on update current_timestamp"), snapshot);
    }

    @Test
    void snapshotStatusAlterIsGuardedByTableExistenceAndMissingColumn() throws IOException {
        String sql = normalized(read("sql/migration/V4_092__evidence_usage_result_feedback.sql"));
        String guard = columnGuardBlock(
                sql, "v4_092", "career_evidence_usage_result_snapshot", "status");

        assertTrue(guard.contains("information_schema.tables"), guard);
        assertTrue(guard.contains(
                "table_name = 'career_evidence_usage_result_snapshot'"), guard);
        assertTrue(guard.contains("information_schema.columns"), guard);
        assertTrue(guard.contains("column_name = 'status'"), guard);
        assertTrue(guard.contains(
                "add column status varchar(24) not null default ''recorded''"), guard);
    }

    @Test
    void initBaselineMatchesResultFeedbackContract() throws IOException {
        String init = normalized(read("sql/init.sql"));
        String root = tableBlock(init, "career_evidence_usage_result");
        String snapshot = tableBlock(init, "career_evidence_usage_result_snapshot");

        assertTrue(root.contains("lock_version int not null default 0"), root);
        assertTrue(root.contains("current_snapshot_id bigint default null"), root);
        assertTrue(snapshot.contains(
                "status varchar(24) not null default 'recorded'"), snapshot);
        assertTrue(snapshot.contains("known_facts_json mediumtext not null"), snapshot);
        assertTrue(snapshot.contains("unknowns_json text not null"), snapshot);
        assertTrue(snapshot.contains("limits_json text not null"), snapshot);
        assertTrue(snapshot.contains("supersedes_snapshot_id bigint default null"), snapshot);
        assertTrue(snapshot.contains(
                "uk_evidence_usage_result_snapshot_version"), snapshot);
    }

    private static void assertHash(String table, String column) {
        assertTrue(table.contains(
                column + " char(64) character set ascii collate ascii_bin"), table);
    }

    private static String tableBlock(String sql, String tableName) {
        String marker = "create table if not exists " + tableName + " (";
        int start = sql.indexOf(marker);
        if (start < 0) {
            marker = "create table " + tableName + " (";
            start = sql.indexOf(marker);
        }
        assertTrue(start >= 0, tableName);
        int end = sql.indexOf(") engine=innodb", start);
        assertTrue(end > start, tableName);
        return sql.substring(start, end);
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
