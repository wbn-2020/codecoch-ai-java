package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class EvaluateAnswerDTO {

    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private String referenceAnswer;
    private String answerContent;
    private Integer followUpCount;
    private String currentStage;
    private String historySummary;
}
