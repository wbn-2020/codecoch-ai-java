package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V9EvidenceUsageSnapshotMigrationContractTest {

    @Test
    void migrationDefinesImmutableAssetPackageAndUsageContracts() throws IOException {
        String sql = normalized(read("sql/migration/V4_091__evidence_usage_snapshot_contract.sql"));
        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.columns"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("prepare v4_091_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_091_stmt"), sql);

        String evidenceVersion = tableBlock(sql, "project_evidence_version");
        assertTrue(evidenceVersion.contains("snapshot_json mediumtext not null"), evidenceVersion);
        assertHash(evidenceVersion, "content_hash");
        assertTrue(evidenceVersion.contains(
                "unique key uk_project_evidence_version_no "
                        + "(project_evidence_id, version_no)"), evidenceVersion);
        assertTrue(evidenceVersion.contains(
                "unique key uk_project_evidence_version_content "
                        + "(project_evidence_id, content_hash)"), evidenceVersion);

        String packageSnapshot = tableBlock(sql, "job_application_package_snapshot");
        assertTrue(packageSnapshot.contains("snapshot_json mediumtext not null"), packageSnapshot);
        assertHash(packageSnapshot, "content_hash");
        assertTrue(packageSnapshot.contains(
                "unique key uk_application_package_snapshot_version "
                        + "(package_id, snapshot_version)"), packageSnapshot);
        assertTrue(packageSnapshot.contains(
                "unique key uk_application_package_snapshot_content "
                        + "(package_id, content_hash)"), packageSnapshot);

        String usage = tableBlock(sql, "career_evidence_usage");
        for (String column : List.of(
                "application_id", "asset_type", "asset_id", "asset_version",
                "package_snapshot_id", "source_hash", "content_hash", "usage_scene",
                "usage_key_hash", "idempotency_key_hash", "idempotency_payload_hash",
                "status", "stale")) {
            assertTrue(usage.contains(column + " "), column);
        }
        for (String column : List.of(
                "source_hash", "content_hash", "usage_key_hash",
                "idempotency_key_hash", "idempotency_payload_hash")) {
            assertHash(usage, column);
        }
        assertTrue(usage.contains(
                "unique key uk_career_evidence_usage_fact (user_id, usage_key_hash)"), usage);
        assertTrue(usage.contains(
                "unique key uk_career_evidence_usage_idempotency "
                        + "(user_id, idempotency_key_hash)"), usage);
        assertTrue(sql.contains("add column current_snapshot_id"), sql);
        assertTrue(sql.contains("idx_job_application_package_current_snapshot"), sql);
    }

    @Test
    void initBaselineMatchesEvidenceUsageSnapshotContract() throws IOException {
        String init = normalized(read("sql/init.sql"));
        String evidenceVersion = tableBlock(init, "project_evidence_version");
        String packageSnapshot = tableBlock(init, "job_application_package_snapshot");
        String usage = tableBlock(init, "career_evidence_usage");

        assertTrue(evidenceVersion.contains("snapshot_json mediumtext not null"), evidenceVersion);
        assertHash(evidenceVersion, "content_hash");
        assertTrue(packageSnapshot.contains("captured_at datetime not null"), packageSnapshot);
        assertHash(packageSnapshot, "content_hash");
        assertTrue(usage.contains("asset_version varchar(64) not null"), usage);
        assertTrue(usage.contains("stale tinyint not null default 0"), usage);
        assertTrue(usage.contains("uk_career_evidence_usage_fact"), usage);
        assertTrue(usage.contains("uk_career_evidence_usage_idempotency"), usage);
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
