package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CareerImportRowMediumTextMigrationTest {

    private static final Path MIGRATION_DIR = Path.of("..", "sql", "migration");
    private static final String MIGRATION_NAME =
            "V4_070__career_import_row_mediumtext.sql";

    @Test
    void migrationIdempotentlyWidensBothCareerImportJsonColumns() throws Exception {
        List<Path> versionMigrations;
        try (var paths = Files.list(MIGRATION_DIR)) {
            versionMigrations = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_070__"))
                    .toList();
        }

        assertEquals(1, versionMigrations.size(), "V4_070 must have exactly one migration");
        assertEquals(MIGRATION_NAME, versionMigrations.get(0).getFileName().toString());

        String sql = normalized(Files.readString(MIGRATION_DIR.resolve(MIGRATION_NAME)));

        assertTrue(sql.contains("set @schema_name = database()"), sql);
        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("table_name = 'career_import_row'"), sql);
        assertColumnGuard(sql, "raw_data_json");
        assertColumnGuard(sql, "duplicate_candidates_json");
        assertTrue(sql.contains(
                "modify column raw_data_json mediumtext null"), sql);
        assertTrue(sql.contains(
                "modify column duplicate_candidates_json mediumtext null"), sql);
        assertTrue(sql.contains("'select 1'"), sql);
    }

    private static void assertColumnGuard(String sql, String columnName) {
        assertTrue(sql.contains("column_name = '" + columnName + "'"), sql);
        assertTrue(sql.contains("data_type <> 'mediumtext'"), sql);
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
