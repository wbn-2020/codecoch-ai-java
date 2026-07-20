package com.codecoachai.task.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class NotificationDailyReminderMigrationTest {

    private static final String MIGRATION =
            "sql/migration/V4_078__notification_daily_reminder_idempotency.sql";
    private static final String BASELINE = "sql/init.sql";

    @Test
    void migrationAddsAtomicDailyReminderIdentityIdempotently() throws IOException {
        String sql = normalized(MIGRATION);

        assertTrue(sql.contains("from information_schema.tables"), sql);
        assertTrue(sql.contains("from information_schema.columns"), sql);
        assertTrue(sql.contains("from information_schema.statistics"), sql);
        assertTrue(sql.contains("add column `reminder_date` date null"), sql);
        assertTrue(sql.contains("add column `live_reminder_date` date"), sql);
        assertTrue(sql.contains(
                "case when `deleted` = 0 then `reminder_date` else null end"), sql);
        assertTrue(sql.contains(
                "add unique key `uk_notification_daily_reminder`"), sql);
        assertTrue(sql.contains(
                "`user_id`, `type`, `biz_type`, `biz_id`, `live_reminder_date`"), sql);
    }

    @Test
    void migrationNormalizesHistoryBeforeCreatingUniqueKey() throws IOException {
        String sql = normalized(MIGRATION);

        int duplicateNormalization =
                sql.indexOf("set n.`reminder_date` = null");
        int guardedBackfill =
                sql.indexOf("v4_078_notification_backfill_candidates");
        int uniqueKey = sql.indexOf(
                "add unique key `uk_notification_daily_reminder`");

        assertTrue(guardedBackfill >= 0, sql);
        assertTrue(sql.contains("'agent_reminder'"), sql);
        assertTrue(sql.contains("'application_follow_up_reminder'"), sql);
        assertTrue(sql.contains("'calendar_reminder'"), sql);
        assertTrue(sql.contains("v4_078_notification_duplicate_ids"), sql);
        assertTrue(sql.contains("and earlier.`id` < n.`id`"), sql);
        assertTrue(sql.contains("select min(planned.`id`), planned.`business_date`"), sql);
        assertTrue(sql.contains(
                "when n.`type` = ''agent_reminder'' "
                        + "then date_sub(date(n.`created_at`), interval 1 day)"), sql);
        assertTrue(sql.contains("left join `notification` current_notification"), sql);
        assertTrue(sql.contains("and current_notification.`id` is null"), sql);
        assertTrue(duplicateNormalization >= 0, sql);
        assertTrue(guardedBackfill > duplicateNormalization, sql);
        assertTrue(uniqueKey > guardedBackfill, sql);
    }

    @Test
    void migrationLeavesDuplicateHistoryNullOnRepeatedExecution() throws IOException {
        String sql = normalized(MIGRATION);

        assertTrue(sql.contains(
                "current_notification.`reminder_date` = planned.`business_date`"), sql);
        assertTrue(sql.contains(
                "join `v4_078_notification_backfill_candidates` c"), sql);
        assertTrue(sql.contains(
                "set n.`reminder_date` = c.`reminder_date`"), sql);
        assertTrue(sql.contains(
                "drop temporary table if exists "
                        + "`v4_078_notification_backfill_candidates`"), sql);
    }

    @Test
    void initializationBaselineMatchesMigratedNotificationIdentity() throws IOException {
        String sql = normalized(BASELINE);

        assertTrue(sql.contains("create table if not exists notification"), sql);
        assertTrue(sql.contains("reminder_date date default null"), sql);
        assertTrue(sql.contains("live_reminder_date date generated always as"), sql);
        assertTrue(sql.contains(
                "unique key uk_notification_daily_reminder"), sql);
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
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Cannot locate " + relativePath);
    }
}
