package com.codecoachai.task.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AsyncTaskLeaseFencingMigrationTest {

    private static final String MIGRATION_PREFIX = "V4_098__";
    private static final String MIGRATION =
            "sql/migration/V4_098__async_task_persistent_lease_fencing.sql";
    private static final String ASYNC_TASK_BASELINE =
            "sql/migration/V3_007__async_task_and_dead_letter.sql";

    @Test
    void versionHasOneOwnerAndAddsNullablePersistentFenceColumn() throws IOException {
        Path migrationDirectory = resolve("sql/migration");
        List<Path> owners;
        try (var paths = Files.list(migrationDirectory)) {
            owners = paths
                    .filter(path -> path.getFileName().toString().startsWith(MIGRATION_PREFIX))
                    .toList();
        }
        assertEquals(1, owners.size(), "V4_098 must have exactly one migration owner");
        assertEquals(
                Path.of(MIGRATION).getFileName().toString(),
                owners.get(0).getFileName().toString());

        String sql = normalized(MIGRATION);
        assertTrue(sql.contains("alter table `async_task`"), sql);
        assertTrue(sql.contains("add column `lease_token` varchar(64) null"), sql);
        assertTrue(sql.contains("after `status`"), sql);
    }

    @Test
    void migrationBackfillsOnlyActiveRunningRowsWithRandomTokens() throws IOException {
        String sql = normalized(MIGRATION);

        int columnAddition = sql.indexOf("add column `lease_token`");
        int backfill = sql.indexOf("update `async_task`");
        assertTrue(columnAddition >= 0, sql);
        assertTrue(backfill > columnAddition, sql);
        assertTrue(sql.contains(
                "set `lease_token` = replace(uuid(), '-', '')"), sql);
        assertTrue(sql.contains("where `deleted` = 0"), sql);
        assertTrue(sql.contains("and `status` = 'running'"), sql);
        assertTrue(sql.contains(
                "and (`lease_token` is null or `lease_token` = '')"), sql);
    }

    @Test
    void migrationDependsOnlyOnAsyncTaskColumnsAvailableBeforeVersion4085()
            throws IOException {
        String baseline = normalized(ASYNC_TASK_BASELINE);
        assertTrue(baseline.contains("create table if not exists `async_task`"), baseline);
        assertTrue(baseline.contains("`status` varchar(16)"), baseline);
        assertTrue(baseline.contains("`deleted` tinyint(1)"), baseline);
        assertTrue(Files.isRegularFile(resolve(
                "sql/migration/V4_085__career_campaign_review.sql")));
    }

    private static String normalized(String relativePath) throws IOException {
        return Files.readString(resolve(relativePath), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Path resolve(String relativePath) {
        for (Path candidate : List.of(
                Path.of(relativePath),
                Path.of("..").resolve(relativePath))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Cannot locate " + relativePath);
    }
}
