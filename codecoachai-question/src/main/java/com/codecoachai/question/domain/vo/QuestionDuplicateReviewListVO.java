package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private String scoreBand;
    private List<ScorePart> scoreParts;
    private BigDecimal vectorScore;
    private BigDecimal textScore;
    private BigDecimal finalScore;
    private Long sourceGroupId;
    private Long targetGroupId;
    private Long sourceCategoryId;
    private Long targetCategoryId;
    private String sourceQuestionType;
    private String targetQuestionType;
    private String sourceDifficulty;
    private String targetDifficulty;
    private Boolean sameCategory;
    private Boolean sameQuestionType;
    private Boolean sameDifficulty;
    private Long relationId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    @Data
    public static class ScorePart {
        private String code;
        private String label;
        private BigDecimal score;
    }
}
