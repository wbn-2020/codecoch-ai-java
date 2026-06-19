package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptTemplateVersionCreateDTO {

    @NotBlank(message = "versionCode is required")
    private String versionCode;

    private String versionName;

    @NotBlank(message = "content is required")
    private String content;

    private String variablesJson;
    private String modelParamsJson;
    private String status;
    private String changeLog;

    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
