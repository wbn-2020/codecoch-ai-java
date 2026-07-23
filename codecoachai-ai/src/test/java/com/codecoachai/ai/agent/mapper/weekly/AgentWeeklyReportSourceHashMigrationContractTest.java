package com.codecoachai.ai.agent.mapper.weekly;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AgentWeeklyReportSourceHashMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_077__agent_weekly_report_source_hash_capacity.sql";
    private static final String BASE_MIGRATION =
            "sql/migration/V4_076__agent_weekly_report.sql";

    @Test
    void migrationWidensPrefixedSha256HashesIdempotently() throws IOException {
        String sql = Files.readString(resolveMigration(), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(sql.contains("from information_schema.tables"), sql);
        assertTrue(sql.contains("from information_schema.columns"), sql);
        assertTrue(sql.contains("column_name = 'source_hash'"), sql);
        assertTrue(sql.contains("add column `source_hash` varchar(80) null"), sql);
        assertTrue(sql.contains("modify column `source_hash` varchar(80) null"), sql);
        assertTrue(sql.contains("@v4_077_source_hash_capacity < 80"), sql);
        assertTrue(sql.contains("prepare v4_077_source_hash_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_077_source_hash_stmt"), sql);

        String baseSql = Files.readString(resolveMigration(BASE_MIGRATION), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        assertTrue(baseSql.contains("`source_hash` varchar(80) default null"), baseSql);
        assertTrue(baseSql.contains("add column `source_hash` varchar(80) null"), baseSql);
    }

    private static Path resolveMigration() {
        return resolveMigration(MIGRATION);
    }

    private static Path resolveMigration(String migration) {
        for (Path candidate : List.of(
                Path.of(migration),
                Path.of("..").resolve(migration))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Cannot locate " + migration);
    }
}
