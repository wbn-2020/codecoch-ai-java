package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI question generation request")
public class AiQuestionGenerateRequestDTO {

    @Schema(description = "Target position, optional. Empty value generates general Java backend questions.")
    private String targetPosition;

    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private Integer experienceYears;

    @Min(value = 1, message = "生成题目数量不能少于 1 道")
    @Max(value = 20, message = "生成题目数量不能超过 20 道")
    private Integer count = 5;

    private Boolean generateReferenceAnswer = true;
    private Boolean generateFollowUps = true;
    private Boolean generateTagSuggestions = true;
    private Boolean generateCategorySuggestion = true;
    private String extraRequirements;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
