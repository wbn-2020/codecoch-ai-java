package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.service.PromptRenderResult;
import java.util.List;

public interface AgentPromptBuilder {

    PromptRenderResult buildDailyPlanPrompt(JobCoachAgentContext context, List<CandidateTask> candidates,
                                            int taskCount, int maxTotalMinutes);
}
