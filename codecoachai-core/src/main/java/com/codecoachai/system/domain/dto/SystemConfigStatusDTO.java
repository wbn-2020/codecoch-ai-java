package com.codecoachai.system.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SystemConfigStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
