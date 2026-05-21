package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import java.time.LocalDate;

public interface AgentContextBuilder {

    JobCoachAgentContext build(Long userId, Long targetJobId, LocalDate planDate);
}
