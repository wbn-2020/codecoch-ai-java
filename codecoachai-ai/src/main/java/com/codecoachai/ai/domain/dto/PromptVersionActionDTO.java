package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class PromptVersionActionDTO {

    private String changeLog;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
    private Long expectedCurrentActiveVersionId;
}
