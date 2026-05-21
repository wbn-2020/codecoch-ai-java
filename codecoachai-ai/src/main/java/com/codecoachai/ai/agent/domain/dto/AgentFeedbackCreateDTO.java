package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class AgentFeedbackCreateDTO {
    private Long agentTaskId;
    private Long agentRunId;
    private String feedbackType;
    private String comment;
}
