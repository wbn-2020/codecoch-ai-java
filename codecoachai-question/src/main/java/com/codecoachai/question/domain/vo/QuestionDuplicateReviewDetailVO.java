package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateReviewDetailVO {

    private Long id;
    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String reviewStatus;
    private String matchType;
    private BigDecimal similarityScore;
    private String matchReason;
    private String scoreBand;
    private List<QuestionDuplicateReviewListVO.ScorePart> scoreParts;
    private BigDecimal vectorScore;
    private BigDecimal textScore;
    private BigDecimal finalScore;
    private String scoreDetailJson;
    private String sourceTitleSnapshot;
    private String targetTitleSnapshot;
    private String sourceContentSnapshot;
    private String targetContentSnapshot;
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
    private Long createdBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String ignoredReason;
    private Long relationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private QuestionSummaryVO sourceQuestion;
    private QuestionSummaryVO targetQuestion;
}
