package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_duplicate_review")
public class QuestionDuplicateReview extends BaseEntity {

    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String reviewStatus;
    private String matchType;
    private BigDecimal similarityScore;
    private String matchReason;
    private String scoreBand;
    private String scoreDetailJson;
    private String sourceTitleSnapshot;
    private String targetTitleSnapshot;
    private String sourceContentSnapshot;
    private String targetContentSnapshot;
    private Long sourceGroupId;
    private Long targetGroupId;
    private Long createdBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String ignoredReason;
    private Long relationId;
}
