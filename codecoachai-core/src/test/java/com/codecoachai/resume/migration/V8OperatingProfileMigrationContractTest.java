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

class V8OperatingProfileMigrationContractTest {

    private static final String MIGRATION =
            "sql/migration/V4_087__career_campaign_operating_profile.sql";

    @Test
    void migrationGuardsPartialRunsAndActiveProfileUniqueness() throws IOException {
        String sql = normalized(read(MIGRATION));
        String table = tableBlock(sql, "career_campaign_operating_profile");

        assertTrue(sql.contains("information_schema.tables"), sql);
        assertTrue(sql.contains("information_schema.columns"), sql);
        assertTrue(sql.contains("information_schema.statistics"), sql);
        assertTrue(sql.contains("prepare v4_087_stmt"), sql);
        assertTrue(sql.contains("deallocate prepare v4_087_stmt"), sql);
        assertTrue(sql.contains("create temporary table _v4_087_uniqueness_guard"), sql);
        assertTrue(sql.contains("having count(*) > 1"), sql);
        assertTrue(
                sql.indexOf("create temporary table _v4_087_uniqueness_guard")
                        < sql.lastIndexOf(
                                "add unique key "
                                        + "uk_career_campaign_operating_profile_live_campaign"),
                sql);

        assertTrue(table.contains("focus_roles_json mediumtext not null"), table);
        assertTrue(table.contains("focus_locations_json mediumtext not null"), table);
        assertTrue(table.contains("focus_channels_json mediumtext not null"), table);
        assertTrue(table.contains(
                "active_guard bigint generated always as ( "
                        + "case when deleted = 0 then campaign_id else null end ) stored"),
                table);
        assertTrue(table.contains(
                "unique key uk_career_campaign_operating_profile_live_campaign "
                        + "(user_id, active_guard)"),
                table);
        assertFalse(table.contains(
                "unique key uk_career_campaign_operating_profile_live_campaign "
                        + "(user_id, campaign_id, deleted)"),
                table);
    }

    @Test
    void initBaselineMatchesOperatingProfileContract() throws IOException {
        String table = tableBlock(
                normalized(read("sql/init.sql")),
                "career_campaign_operating_profile");

        assertTrue(table.contains("weekly_application_target int not null default 3"), table);
        assertTrue(table.contains(
                "weekly_time_budget_minutes int not null default 180"), table);
        assertTrue(table.contains("max_active_opportunities int not null default 10"), table);
        assertTrue(table.contains("timezone varchar(64) not null default 'utc'"), table);
        assertTrue(table.contains("lock_version int not null default 1"), table);
        assertTrue(table.contains("focus_roles_json mediumtext not null"), table);
        assertTrue(table.contains("focus_locations_json mediumtext not null"), table);
        assertTrue(table.contains("focus_channels_json mediumtext not null"), table);
        assertTrue(table.contains("generated always as"), table);
        assertTrue(table.contains(
                "uk_career_campaign_operating_profile_live_campaign"), table);
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
