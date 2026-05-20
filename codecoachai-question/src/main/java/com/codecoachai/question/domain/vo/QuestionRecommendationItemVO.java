package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionRecommendationItemVO {

    private Long id;
    private Long batchId;
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
    private String matchStatus;
    private String practiceStatus;
    private Boolean canPractice;
    private Long practiceQuestionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
