package com.codecoachai.system.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SystemConfigStatusDTO {

    @NotNull(message = "status is required")
    private Integer status;
}
