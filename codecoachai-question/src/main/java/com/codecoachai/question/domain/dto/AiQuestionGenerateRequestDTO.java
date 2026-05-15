package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AiQuestionGenerateRequestDTO {

    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private Integer experienceYears;

    @Min(value = 1, message = "count must be at least 1")
    @Max(value = 20, message = "count must be at most 20")
    private Integer count = 5;

    private Boolean generateReferenceAnswer = true;
    private Boolean generateFollowUps = true;
    private Boolean generateTagSuggestions = true;
    private Boolean generateCategorySuggestion = true;
    private String extraRequirements;
}
