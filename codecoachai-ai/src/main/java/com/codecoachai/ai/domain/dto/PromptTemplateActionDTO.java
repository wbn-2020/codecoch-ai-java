package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class PromptTemplateActionDTO {

    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
    private Integer expectedStatus;
    private Long expectedActiveVersionId;
}
