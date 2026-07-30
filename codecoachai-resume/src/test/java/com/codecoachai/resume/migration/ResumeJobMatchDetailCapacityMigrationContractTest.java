package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ResumeJobMatchDetailCapacityMigrationContractTest {

    private static final String MIGRATION_NAME =
            "V4_103__resume_job_match_detail_text_capacity.sql";

    @Test
    void migrationWidensAllAiDetailTextWithoutTruncatingEvidence()
            throws Exception {
        Path migrationDirectory = migrationDirectory();
        List<Path> versionMigrations;
        try (var paths = Files.list(migrationDirectory)) {
            versionMigrations = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_103__"))
                    .toList();
        }

        assertEquals(1, versionMigrations.size(), "V4_103 must have exactly one migration");
        assertEquals(MIGRATION_NAME, versionMigrations.get(0).getFileName().toString());

        String sql = normalized(Files.readString(
                migrationDirectory.resolve(MIGRATION_NAME),
                StandardCharsets.UTF_8));

        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("table_name = 'resume_job_match_detail'"), sql);
        assertTrue(sql.contains(
                "modify column dimension varchar(255) default null"), sql);
        assertTrue(sql.contains(
                "modify column skill_name text default null"), sql);
        assertTrue(sql.contains(
                "modify column evidence mediumtext default null"), sql);
        assertTrue(sql.contains(
                "modify column gap_description mediumtext default null"), sql);
        assertTrue(sql.contains(
                "modify column suggestion mediumtext default null"), sql);
        assertTrue(sql.contains(
                "drop index idx_resume_match_detail_skill"), sql);
        assertTrue(sql.contains(
                "add key idx_resume_match_detail_skill (skill_name(191), deleted)"), sql);
        assertFalse(sql.contains("modify column match_level"), sql);
        assertFalse(sql.contains("left("), sql);
        assertFalse(sql.contains("substring("), sql);
    }

    private static Path migrationDirectory() {
        for (Path candidate : List.of(
                Path.of("sql", "migration"),
                Path.of("..", "sql", "migration"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Cannot locate sql/migration");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
