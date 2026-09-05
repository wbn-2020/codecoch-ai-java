package com.codecoachai.common.mybatis.statistics;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Single read model for learning progress shown across dashboard, study and growth pages.
 */
@Service
public class StudyProgressStatisticsService {

    public static final String BUSINESS_TIMEZONE = "Asia/Shanghai";
    public static final ZoneId BUSINESS_ZONE_ID = ZoneId.of(BUSINESS_TIMEZONE);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public StudyProgressStatisticsService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.system(BUSINESS_ZONE_ID));
    }

    public StudyProgressStatisticsService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = (clock == null ? Clock.system(BUSINESS_ZONE_ID) : clock).withZone(BUSINESS_ZONE_ID);
    }

    public LocalDate businessDate() {
        return LocalDate.now(clock);
    }

    public StudyProgressSnapshot current(Long userId) {
        return current(userId, null);
    }

    public StudyProgressSnapshot current(Long userId, Long targetJobId) {
        PlanIdentity plan = jdbcTemplate.query("""
                SELECT id, target_job_id, plan_title, plan_summary, plan_status, updated_at
                FROM study_plan
                WHERE deleted = 0
                  AND user_id = ?
                  AND plan_status = 'ACTIVE'
                  AND (? IS NULL OR target_job_id = ?)
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """, rs -> rs.next() ? planIdentity(rs) : null, userId, targetJobId, targetJobId);
        return snapshot(userId, plan);
    }

    public StudyProgressSnapshot forPlan(Long userId, Long planId) {
        if (planId == null) {
            return current(userId);
        }
        PlanIdentity plan = jdbcTemplate.query("""
                SELECT id, target_job_id, plan_title, plan_summary, plan_status, updated_at
                FROM study_plan
                WHERE deleted = 0 AND user_id = ? AND id = ?
                LIMIT 1
                """, rs -> rs.next() ? planIdentity(rs) : null, userId, planId);
        return snapshot(userId, plan);
    }

    private StudyProgressSnapshot snapshot(Long userId, PlanIdentity plan) {
        LocalDate today = businessDate();
        StudyProgressSnapshot snapshot = new StudyProgressSnapshot();
        snapshot.setBusinessDate(today);
        snapshot.setBusinessTimezone(BUSINESS_TIMEZONE);
        applyStreak(snapshot, userId, today);
        if (plan == null) {
            return snapshot;
        }

        snapshot.setPlanId(plan.id());
        snapshot.setTargetJobId(plan.targetJobId());
        snapshot.setPlanTitle(plan.title());
        snapshot.setPlanSummary(plan.summary());
        snapshot.setPlanStatus(plan.status());
        snapshot.setPlanUpdatedAt(plan.updatedAt());

        long total = countTasks(userId, plan.id(), null, null);
        long completed = countTasks(userId, plan.id(), null, "DONE_OR_COMPLETED");
        long skipped = countTasks(userId, plan.id(), null, "SKIPPED");
        long todayTotal = countTasks(userId, plan.id(), today, null);
        long todayCompleted = countTasks(userId, plan.id(), today, "DONE_OR_COMPLETED");

        snapshot.setTotalTasks(safeInt(total));
        snapshot.setCompletedTasks(safeInt(completed));
        snapshot.setSkippedTasks(safeInt(skipped));
        snapshot.setPendingTasks(safeInt(Math.max(0L, total - completed - skipped)));
        snapshot.setCompletionRate(percent(completed, total));
        snapshot.setTodayTasks(safeInt(todayTotal));
        snapshot.setTodayCompletedTasks(safeInt(todayCompleted));
        snapshot.setTodayCompletionRate(percent(todayCompleted, todayTotal));
        return snapshot;
    }

    private void applyStreak(StudyProgressSnapshot snapshot, Long userId, LocalDate today) {
        List<LocalDate> dates = jdbcTemplate.queryForList("""
                SELECT DISTINCT checkin_date
                FROM study_checkin
                WHERE deleted = 0 AND user_id = ? AND checkin_date <= ?
                ORDER BY checkin_date DESC
                LIMIT 366
                """, LocalDate.class, userId, today);
        long totalDays = value(jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT checkin_date)
                FROM study_checkin
                WHERE deleted = 0 AND user_id = ?
                """, Long.class, userId));

        boolean checkedInToday = !dates.isEmpty() && today.equals(dates.get(0));
        LocalDate expected = checkedInToday ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate date : dates) {
            if (expected.equals(date)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (date.isBefore(expected)) {
                break;
            }
        }
        snapshot.setCurrentStreak(streak);
        snapshot.setTotalCheckinDays(safeInt(totalDays));
        snapshot.setCheckedInToday(checkedInToday);
    }

    private long countTasks(Long userId, Long planId, LocalDate plannedDate, String statusGroup) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM study_task
                WHERE deleted = 0 AND user_id = ? AND plan_id = ?
                """);
        if (plannedDate != null) {
            sql.append(" AND planned_date = ?");
        }
        if ("DONE_OR_COMPLETED".equals(statusGroup)) {
            sql.append(" AND task_status IN ('DONE','COMPLETED')");
        } else if ("SKIPPED".equals(statusGroup)) {
            sql.append(" AND task_status = 'SKIPPED'");
        }
        Long count = plannedDate == null
                ? jdbcTemplate.queryForObject(sql.toString(), Long.class, userId, planId)
                : jdbcTemplate.queryForObject(sql.toString(), Long.class, userId, planId, plannedDate);
        return value(count);
    }

    private PlanIdentity planIdentity(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        long targetJobId = rs.getLong("target_job_id");
        return new PlanIdentity(
                rs.getLong("id"),
                rs.wasNull() ? null : targetJobId,
                rs.getString("plan_title"),
                rs.getString("plan_summary"),
                rs.getString("plan_status"),
                updatedAt == null ? null : updatedAt.toLocalDateTime());
    }

    private int percent(long numerator, long denominator) {
        return denominator <= 0 ? 0 : safeInt(Math.round(numerator * 100.0D / denominator));
    }

    private int safeInt(long value) {
        return Math.toIntExact(Math.max(0L, Math.min(Integer.MAX_VALUE, value)));
    }

    private long value(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private record PlanIdentity(
            Long id,
            Long targetJobId,
            String title,
            String summary,
            String status,
            java.time.LocalDateTime updatedAt) {
    }
}
