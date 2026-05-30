package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateEvalRunVO {

    private Long id;
    private String runNo;
    private String status;
    private Integer sampleCount;
    private Integer evaluatedCount;
    private Integer passedCount;
    private Integer failedCount;
    private Integer missingQuestionCount;
    private BigDecimal accuracyRate;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdBy;
    private String errorMessage;
    private List<ResultItem> results;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class ResultItem {
        private Long id;
        private Long evalCaseId;
        private String caseId;
        private Long sourceQuestionId;
        private Long targetQuestionId;
        private String expected;
        private String predicted;
        private Boolean passed;
        private String matchType;
        private BigDecimal score;
        private String scoreBand;
        private List<QuestionDuplicateReviewListVO.ScorePart> scoreParts;
        private String reason;
        private String note;
        private LocalDateTime createdAt;
    }
}
