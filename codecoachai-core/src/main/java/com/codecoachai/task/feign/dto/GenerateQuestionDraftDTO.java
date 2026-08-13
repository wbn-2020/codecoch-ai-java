package com.codecoachai.task.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateQuestionDraftDTO {
    private String topic;
    private String difficulty;
    private Integer count;
    private List<String> tags;
    private String targetPosition;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private Integer experienceYears;
    private String experienceLevel;
    private String batchId;
    private Long adminUserId;
    private Boolean generateReferenceAnswer;
    private Boolean generateFollowUps;
    private Boolean generateTagSuggestions;
    private Boolean generateCategorySuggestion;
    private String extraRequirements;
}
