package com.codecoachai.interview.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class InnerStudyPlanVO {

    private Long planId;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String targetPosition;
    private String industryDirection;
    private String planTitle;
    private String planSummary;
    private String planStatus;
    private Integer durationDays;
    private Integer dailyMinutes;
    private LocalDate startDate;
    private Long aiCallLogId;
    private String resultJson;
    private List<InnerStudyTaskVO> tasks;
    private List<InnerStudyPlanSkillRelationVO> skillRelations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
