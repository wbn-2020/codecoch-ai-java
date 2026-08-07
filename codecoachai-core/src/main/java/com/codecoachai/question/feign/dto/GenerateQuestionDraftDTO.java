package com.codecoachai.question.feign.dto;

import lombok.Data;

@Data
public class GenerateQuestionDraftDTO {

    private String batchId;
    private Long adminUserId;
    private String targetPosition;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private Integer experienceYears;
    private Integer count;
    private Boolean generateReferenceAnswer;
    private Boolean generateFollowUps;
    private Boolean generateTagSuggestions;
    private Boolean generateCategorySuggestion;
    private String extraRequirements;
}
