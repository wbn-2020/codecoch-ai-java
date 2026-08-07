package com.codecoachai.task.domain.dto;

import lombok.Data;

@Data
public class AdminTaskActionDTO {
    private String note;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
