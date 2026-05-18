package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class QuestionRecommendationItemVO {

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
