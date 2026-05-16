package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class GenerateLearningPlanDTO {

    private Long learningPlanId;
    private Long userId;
    private Long reportId;
    private Long sessionId;
    private Long resumeId;
    private Long optimizeRecordId;
    private String targetPosition;
    private String industryDirection;
    private String experienceLevel;
    private String interviewSummary;
    private String weaknessSummary;
    private String questionPerformanceSummary;
    private String resumeWeaknessSummary;
    private Integer expectedDurationDays;
    private String extraRequirements;
}
