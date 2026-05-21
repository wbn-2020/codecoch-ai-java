package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class AdminAgentOverviewVO {

    private Long totalAgentRuns;
    private Long successAgentRuns;
    private Long failedAgentRuns;
    private Double agentSuccessRate;
    private Long avgDurationMs;
    private Long totalAgentTasks;
    private Long doneTaskCount;
    private Long skippedTaskCount;
    private Double taskCompletionRate;
}
