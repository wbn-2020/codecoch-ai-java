package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_recommendation_batch")
public class QuestionRecommendationBatch extends BaseEntity {

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
    private String requestJson;
    private String resultJson;
    private String errorMessage;
}
