package com.codecoachai.question.feign.vo;

import lombok.Data;

@Data
public class QuestionRecommendationDraftItemVO {

    private String title;
    private String content;
    private String questionType;
    private String difficulty;
    private String skillCode;
    private String skillName;
    private String gapSeverity;
    private String recommendReason;
    private String answerHint;
    private String evaluatePoints;
}
