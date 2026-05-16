package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionDuplicateReviewListVO {

    private Long id;
    private Long sourceQuestionId;
    private String sourceTitle;
    private Long targetQuestionId;
    private String targetTitle;
    private String reviewStatus;
    private String matchType;
    private BigDecimal similarityScore;
    private String matchReason;
    private Long sourceGroupId;
    private Long targetGroupId;
    private Long relationId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
