package com.codecoachai.common.mybatis.statistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Authoritative learning progress and check-in snapshot shared by user-facing endpoints.
 */
@Data
public class StudyProgressSnapshot {

    private Long planId;
    private Long targetJobId;
    private String planTitle;
    private String planSummary;
    private String planStatus;
    private LocalDateTime planUpdatedAt;
    private int totalTasks;
    private int completedTasks;
    private int skippedTasks;
    private int pendingTasks;
    private int completionRate;
    private int todayTasks;
    private int todayCompletedTasks;
    private int todayCompletionRate;
    private int currentStreak;
    private int totalCheckinDays;
    private boolean checkedInToday;
    private LocalDate businessDate;
    private String businessTimezone;
}
