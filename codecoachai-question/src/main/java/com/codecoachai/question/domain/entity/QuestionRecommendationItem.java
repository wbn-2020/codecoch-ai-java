package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_recommendation_item")
public class QuestionRecommendationItem extends BaseEntity {

    private Long batchId;
    private Long userId;
    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private String questionType;
    private String difficulty;
    private String skillCode;
    private String skillName;
    private String gapSeverity;
    private String recommendReason;
    private String answerHint;
    private String evaluatePoints;
    private Integer sortOrder;
    private String practiceStatus;
}
