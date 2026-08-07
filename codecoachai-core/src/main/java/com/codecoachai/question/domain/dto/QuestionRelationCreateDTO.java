package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionRelationCreateDTO {

    @NotNull(message = "请选择要关联的题目")
    private Long targetQuestionId;

    private String relationType;
    private String reason;
    private Boolean confirm;
    private Boolean dryRun;
    private String idempotencyKey;
}
