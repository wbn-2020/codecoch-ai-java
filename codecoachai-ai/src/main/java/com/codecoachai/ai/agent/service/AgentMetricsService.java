package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentMetricEventDTO;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.ActivationHandoffVO;
import com.codecoachai.ai.agent.domain.vo.AgentMetricAckVO;
import java.util.List;

public interface AgentMetricsService {

    AgentMetricAckVO acceptEvent(Long actorUserId, AgentMetricEventDTO event);

    void recordTaskCompleted(AgentTask task, String requestId, List<ActivationHandoffVO> activationHandoffs,
                             boolean verifiedBusinessAction);
}
