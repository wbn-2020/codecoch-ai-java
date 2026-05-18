package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionRecommendationBatchListVO {

    private Long batchId;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private Long jobTargetId;
    private Long matchReportId;
    private Long skillProfileId;
    private Long studyPlanId;
    private String strategy;
    private Integer questionCount;
    private String status;
    private Long aiCallLogId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
