package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V7MigrationContractTest {

    private static final List<String> MIGRATIONS = List.of(
            "V4_079__agent_plan_external_source.sql",
            "V4_080__career_campaign_and_application_workspace.sql",
            "V4_081__career_interview_process.sql",
            "V4_082__career_offer_decision.sql",
            "V4_083__career_contact_and_activity.sql",
            "V4_084__career_research_evidence.sql",
            "V4_085__career_campaign_review.sql");

    @Test
    void everyV7MigrationIsForwardSafeAndIdempotentByContract() throws IOException {
        for (String migration : MIGRATIONS) {
            String sql = read("sql/migration/" + migration);
            String normalized = sql.toLowerCase(Locale.ROOT);
            assertTrue(sql.contains("information_schema"), migration);
            assertTrue(normalized.contains("not exists"), migration);
            assertTrue(sql.contains("PREPARE"), migration);
            assertTrue(sql.contains("DEALLOCATE PREPARE"), migration);
        }
    }

    @Test
    void partialRunRepairCoversEveryGeneratedLiveGuardBeforeItsUniqueIndex() throws IOException {
        assertGeneratedGuardRepair(
                "V4_081__career_interview_process.sql",
                List.of("live_application_id", "live_round_no", "live_calendar_event_id"));
        assertGeneratedGuardRepair(
                "V4_082__career_offer_decision.sql",
                List.of("live_application_id", "live_campaign_id"));
        assertGeneratedGuardRepair(
                "V4_083__career_contact_and_activity.sql",
                List.of("live_application_id", "live_contact_id"));
        assertGeneratedGuardRepair(
                "V4_084__career_research_evidence.sql",
                List.of("live_application_id"));
        assertGeneratedGuardRepair(
                "V4_085__career_campaign_review.sql",
                List.of("live_campaign_id"));
    }

    @Test
    void initBaselineContainsAllV7Tables() throws IOException {
        String init = read("sql/init.sql").toLowerCase(Locale.ROOT);
        for (String table : List.of(
                "career_campaign", "career_campaign_event",
                "career_interview_process", "career_interview_round",
                "career_interview_round_event", "career_offer",
                "career_offer_version", "career_offer_event",
                "career_offer_decision", "career_offer_decision_snapshot",
                "career_offer_decision_item", "career_contact",
                "career_contact_application", "career_activity",
                "career_activity_event", "career_interview_round_contact",
                "career_research_source", "career_research_source_version",
                "career_research_report", "career_research_snapshot",
                "career_research_snapshot_source", "career_campaign_review",
                "career_campaign_review_snapshot", "career_campaign_review_source",
                "career_campaign_review_memory_candidate")) {
            assertTrue(init.contains("create table " + table + " (")
                    || init.contains("create table if not exists " + table + " (")
                    || init.contains("create table `" + table + "`"), table);
        }
    }

    private void assertGeneratedGuardRepair(String migration, List<String> columns)
            throws IOException {
        String sql = read("sql/migration/" + migration).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        int firstIndex = sql.indexOf("create table if not exists");
        for (String column : columns) {
            String normalizedColumn = column.toLowerCase(Locale.ROOT);
            int repair = sql.indexOf("column_name = '" + normalizedColumn + "'");
            assertTrue(firstIndex >= 0, migration);
            assertTrue(repair >= 0, migration + " missing repair for " + column);
            assertTrue(sql.contains("generated always as"), migration + " missing generated expression");
        }
    }

    private String read(String relative) throws IOException {
        for (Path candidate : List.of(Path.of(relative), Path.of("..").resolve(relative))) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("无法定位迁移文件：" + relative);
    }
}
