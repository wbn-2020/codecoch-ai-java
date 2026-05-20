package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class GenerateQuestionRecommendationDTO {

    private Long batchId;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private Long targetJobId;
    private Long matchReportId;
    private Long skillProfileId;
    private Long studyPlanId;
    private String strategy;
    private Integer questionCount;
    private String difficultyPreference;
    private String targetJobJson;
    private String matchReportJson;
    private String skillProfileJson;
    private String skillGapsJson;
    private String studyPlanJson;
    private String studyTasksJson;
}
