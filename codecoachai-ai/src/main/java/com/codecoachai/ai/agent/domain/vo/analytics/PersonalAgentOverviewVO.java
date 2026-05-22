package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class PersonalAgentOverviewVO {

    private Long todayTaskCount;
    private Long todayDoneCount;
    private Long todaySkippedCount;
    private Long todayEstimatedMinutes;
    private Long last7DaysTaskCount;
    private Long last7DaysDoneCount;
    private Double last7DaysCompletionRate;
    private Long totalAgentPlanCount;
    private Long agentGeneratedTaskCount;
    private Double taskCompletionRate;
    private Double agentSuccessRate;
    private Long avgAgentDurationMs;
}
