package com.codecoachai.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.common.mybatis.statistics.StudyProgressSnapshot;
import com.codecoachai.common.mybatis.statistics.StudyProgressStatisticsService;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class StudyProgressStatisticsServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void usesShanghaiDateDeletedFiltersAndOneProgressDefinition() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<String> queriedSql = new ArrayList<>();
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            queriedSql.add(sql);
            ResultSetExtractor extractor = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getLong("id")).thenReturn(88L);
            when(rs.getLong("target_job_id")).thenReturn(77L);
            when(rs.wasNull()).thenReturn(false);
            when(rs.getString("plan_title")).thenReturn("Active plan");
            when(rs.getString("plan_summary")).thenReturn("Plan summary");
            when(rs.getString("plan_status")).thenReturn("ACTIVE");
            when(rs.getTimestamp("updated_at"))
                    .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 18, 0, 5)));
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        when(jdbcTemplate.queryForList(anyString(), eq(LocalDate.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    queriedSql.add(invocation.getArgument(0));
                    return List.of(
                            LocalDate.of(2026, 8, 17),
                            LocalDate.of(2026, 8, 16),
                            LocalDate.of(2026, 8, 14));
                });
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    queriedSql.add(sql);
                    if (sql.contains("COUNT(DISTINCT checkin_date)")) {
                        return 3L;
                    }
                    if (sql.contains("planned_date") && sql.contains("DONE','COMPLETED")) {
                        return 1L;
                    }
                    if (sql.contains("planned_date")) {
                        return 2L;
                    }
                    if (sql.contains("DONE','COMPLETED")) {
                        return 2L;
                    }
                    if (sql.contains("task_status = 'SKIPPED'")) {
                        return 1L;
                    }
                    return 4L;
                });

        Clock clock = Clock.fixed(Instant.parse("2026-08-17T16:30:00Z"), ZoneId.of("UTC"));
        StudyProgressStatisticsService service = new StudyProgressStatisticsService(jdbcTemplate, clock);

        StudyProgressSnapshot snapshot = service.current(1001L, 77L);

        assertEquals(LocalDate.of(2026, 8, 18), snapshot.getBusinessDate());
        assertEquals("Asia/Shanghai", snapshot.getBusinessTimezone());
        assertEquals(88L, snapshot.getPlanId());
        assertEquals(4, snapshot.getTotalTasks());
        assertEquals(2, snapshot.getCompletedTasks());
        assertEquals(1, snapshot.getSkippedTasks());
        assertEquals(1, snapshot.getPendingTasks());
        assertEquals(50, snapshot.getCompletionRate());
        assertEquals(2, snapshot.getTodayTasks());
        assertEquals(1, snapshot.getTodayCompletedTasks());
        assertEquals(50, snapshot.getTodayCompletionRate());
        assertEquals(2, snapshot.getCurrentStreak());
        assertEquals(3, snapshot.getTotalCheckinDays());
        assertFalse(snapshot.isCheckedInToday());

        assertTrue(queriedSql.stream()
                .filter(sql -> sql.contains("study_plan")
                        || sql.contains("study_task")
                        || sql.contains("study_checkin"))
                .allMatch(sql -> sql.contains("deleted = 0")));
        assertTrue(queriedSql.stream().anyMatch(sql ->
                sql.contains("plan_status = 'ACTIVE'")
                        && sql.contains("ORDER BY updated_at DESC, id DESC")));
        assertTrue(queriedSql.stream().anyMatch(sql ->
                sql.contains("task_status IN ('DONE','COMPLETED')")));
    }
}
