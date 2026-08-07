package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveQuestionGroupDTO {

    @NotBlank(message = "请填写题组名称")
    private String groupName;

    private String canonicalTitle;
    private String canonicalAnswer;
    private String mainKnowledgePoint;
    private String difficulty;
    private String description;
    private Long categoryId;
    private Integer status;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
