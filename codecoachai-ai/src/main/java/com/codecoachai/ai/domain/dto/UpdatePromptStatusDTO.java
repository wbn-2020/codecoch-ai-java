package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePromptStatusDTO {

    @NotNull(message = "status is required")
    private Integer status;
}
