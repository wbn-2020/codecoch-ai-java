package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentCoachActionDTO {

    @NotNull(message = "taskId is required")
    private Long taskId;

    @NotBlank(message = "actionType is required")
    private String actionType;

    private String requestId;

    private String idempotencyKey;
}
