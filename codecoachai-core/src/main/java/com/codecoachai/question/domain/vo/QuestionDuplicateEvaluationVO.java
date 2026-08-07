package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateEvaluationVO {

    private Integer sampleCount;
    private Integer evaluatedCount;
    private Integer passedCount;
    private Integer failedCount;
    private Integer missingQuestionCount;
    private BigDecimal accuracyRate;
    private List<Item> items;
    private LocalDateTime generatedAt;

    @Data
    public static class Item {
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
    }
}
