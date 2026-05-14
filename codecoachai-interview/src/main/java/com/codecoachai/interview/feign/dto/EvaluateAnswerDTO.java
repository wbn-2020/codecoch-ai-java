package com.codecoachai.interview.feign.dto;

import lombok.Data;

@Data
public class EvaluateAnswerDTO {

    private Long questionId;
    private String questionTitle;
    private String rootQuestionContent;
    private String currentQuestionContent;
    private String questionContent;
    private String referenceAnswer;
    private String answerContent;
    private Integer followUpCount;
    private Integer maxFollowUpCount;
    private String currentStage;
    private String historySummary;
    private String knowledgePoints;
}
