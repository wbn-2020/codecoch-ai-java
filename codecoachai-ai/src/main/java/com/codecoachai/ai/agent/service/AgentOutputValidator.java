package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import java.util.List;

public interface AgentOutputValidator {

    void validateDailyPlan(DailyPlanResult result, List<CandidateTask> candidates, int taskCount, int maxTotalMinutes);
}
