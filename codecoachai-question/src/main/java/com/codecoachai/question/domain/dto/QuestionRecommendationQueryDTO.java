package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionRecommendationQueryDTO {

    private String sourceType;
    private String status;
    private Long jobTargetId;
    private Long matchReportId;
    private Long skillProfileId;
    private Long studyPlanId;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
