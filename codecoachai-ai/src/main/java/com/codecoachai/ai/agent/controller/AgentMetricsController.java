package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AgentMetricEventDTO;
import com.codecoachai.ai.agent.domain.vo.AgentMetricAckVO;
import com.codecoachai.ai.agent.service.AgentMetricsService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/metrics")
public class AgentMetricsController {

    private final AgentMetricsService agentMetricsService;

    @PostMapping("/events")
    public Result<AgentMetricAckVO> createEvent(@RequestBody AgentMetricEventDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentMetricsService.acceptEvent(userId, dto));
    }
}
