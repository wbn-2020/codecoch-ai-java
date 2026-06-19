package com.codecoachai.ai.domain.dto;

import java.util.Map;
import lombok.Data;

@Data
public class PromptVersionTestDTO {

    private Map<String, String> inputVariables;
    private Boolean callAi;
    private Boolean confirmSensitiveAccess;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
