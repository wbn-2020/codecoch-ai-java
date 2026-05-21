package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import java.util.List;

public interface CandidateTaskBuilder {

    List<CandidateTask> build(JobCoachAgentContext context, int taskCount);
}
