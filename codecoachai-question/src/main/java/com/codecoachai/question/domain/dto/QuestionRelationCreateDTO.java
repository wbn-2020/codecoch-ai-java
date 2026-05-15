package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionRelationCreateDTO {

    @NotNull(message = "targetQuestionId is required")
    private Long targetQuestionId;

    private String relationType;
    private String reason;
}
