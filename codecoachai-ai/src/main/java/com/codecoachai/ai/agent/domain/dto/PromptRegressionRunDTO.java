package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class PromptRegressionRunDTO {
    private Long promptVersionId;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
