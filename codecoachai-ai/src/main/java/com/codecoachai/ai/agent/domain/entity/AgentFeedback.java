package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_feedback")
public class AgentFeedback extends BaseEntity {
    private Long userId;
    private Long agentTaskId;
    private Long agentRunId;
    private String feedbackType;
    private String comment;
}
