package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusDTO {

    @NotNull(message = "请选择状态")
    private Integer status;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
