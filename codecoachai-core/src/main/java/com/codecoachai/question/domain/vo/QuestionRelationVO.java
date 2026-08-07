package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionRelationVO {

    private Long id;
    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String relationType;
    private String relationStatus;
    private String reason;
    private BigDecimal similarityScore;
    private Long createdBy;
    private LocalDateTime createdAt;
    private QuestionSummaryVO sourceQuestion;
    private QuestionSummaryVO targetQuestion;
}
