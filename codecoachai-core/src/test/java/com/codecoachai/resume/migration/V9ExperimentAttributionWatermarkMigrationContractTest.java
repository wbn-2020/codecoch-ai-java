package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V9ExperimentAttributionWatermarkMigrationContractTest {

    @Test
    void migrationAddsWatermarkFieldsAndDatabaseIdempotency() throws IOException {
        String sql = normalized(read("sql/migration/V4_093__experiment_attribution_input_watermark.sql"));
        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.columns"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("prepare v4_093_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_093_stmt"), sql);
        for (String column : List.of(
                "data_cutoff_at", "input_hash", "algorithm_version",
                "source_watermark", "result_source", "fallback")) {
            assertTrue(sql.contains("add column " + column), column);
        }
        assertTrue(sql.contains(
                "add unique key uk_job_experiment_attribution_input "
                        + "(user_id, cohort_id, input_hash, algorithm_version)"), sql);
        assertTrue(sql.contains("ascii_bin"), sql);
    }

    @Test
    void initBaselineMatchesAttributionWatermarkContract() throws IOException {
        String table = tableBlock(normalized(read("sql/init.sql")),
                "job_experiment_attribution");
        assertTrue(table.contains("data_cutoff_at datetime default null"), table);
        assertTrue(table.contains(
                "input_hash char(64) character set ascii collate ascii_bin default null"), table);
        assertTrue(table.contains("algorithm_version varchar(32) default null"), table);
        assertTrue(table.contains("source_watermark text default null"), table);
        assertTrue(table.contains("result_source varchar(24) not null default 'rule'"), table);
        assertTrue(table.contains("fallback tinyint not null default 0"), table);
        assertTrue(table.contains("uk_job_experiment_attribution_input"), table);
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
