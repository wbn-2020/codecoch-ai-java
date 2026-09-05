package com.codecoachai.interview.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StudyPlanListVO {

    private Long id;
    private Long reportId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private Long sessionId;
    private String sourceType;
    private String targetPosition;
    private String industryDirection;
    private String planTitle;
    private String planSummary;
    private String planStatus;
    private Integer durationDays;
    private Integer dailyMinutes;
    private LocalDate startDate;
    private LocalDate effectiveStartDate;
    private LocalDate endDate;
    private String scheduleStatus;
    private Boolean expired;
    private Boolean renewable;
    private Integer totalTaskCount;
    private Integer doneTaskCount;
    private Integer progressPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
