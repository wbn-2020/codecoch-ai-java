package com.codecoachai.ai.agent.domain.vo.feedback;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentFeedbackVO {
    private Long id;
    private Long userId;
    private Long agentTaskId;
    private Long agentRunId;
    private String feedbackType;
    private String comment;
    private LocalDateTime createdAt;
}
