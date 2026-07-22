package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V8StageSevenMigrationContractTest {

    @Test
    void migrationAndInitDeclareOwnerIdempotencyAndArchiveLimits() throws IOException {
        String migration = read("sql/migration/V4_090__career_campaign_archive_export.sql");
        String normalized = migration.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("information_schema"), migration);
        assertTrue(normalized.contains("not exists"), migration);
        assertTrue(normalized.contains("prepare"), migration);
        assertTrue(normalized.contains("deallocate prepare"), migration);
        assertTrue(normalized.contains("uk_campaign_archive_export_source"), migration);
        assertTrue(normalized.contains("uk_campaign_archive_export_idempotency"), migration);
        assertTrue(normalized.contains("source_hash"), migration);
        assertTrue(normalized.contains("manifest_hash"), migration);
        assertTrue(normalized.contains("ascii_bin"), migration);

        String init = read("sql/init.sql").toLowerCase(Locale.ROOT);
        String table = tableBlock(init, "career_campaign_archive_export");
        assertTrue(table.contains("user_id bigint"), table);
        assertTrue(table.contains("campaign_id bigint"), table);
        assertTrue(table.contains("data_cutoff_at datetime"), table);
        assertTrue(table.contains("status varchar(24)"), table);
        assertTrue(table.contains("file_size bigint"), table);
        assertTrue(table.contains("deleted tinyint"), table);
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
        Path direct = Path.of(relative);
        Path candidate = Files.isRegularFile(direct) ? direct : Path.of("..").resolve(relative);
        return Files.readString(candidate, StandardCharsets.UTF_8);
    }
}
