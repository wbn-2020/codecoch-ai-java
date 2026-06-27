package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class AgentRunFailureDTO {

    private Long userId;

    private String executionToken;

    private String errorCode;

    private String errorMessage;
}
