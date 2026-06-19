package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePromptStatusDTO {

    @NotNull(message = "status is required")
    private Integer status;

    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
    private Integer expectedStatus;
    private Long expectedActiveVersionId;
}
