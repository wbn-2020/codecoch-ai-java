package com.codecoachai.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemConfigSaveDTO {

    @NotBlank(message = "configKey is required")
    private String configKey;

    @NotBlank(message = "configValue is required")
    private String configValue;

    private String valueType;
    private String description;
    private Integer status;
}
