package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.context.DailyPlanResult;

public interface AgentOutputParser {

    DailyPlanResult parseDailyPlan(String rawOutput);
}
