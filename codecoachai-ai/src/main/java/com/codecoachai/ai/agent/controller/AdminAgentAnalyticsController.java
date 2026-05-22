package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentTaskStatsVO;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAiOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import com.codecoachai.ai.agent.domain.vo.analytics.TrendPointVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentAnalyticsService;
import com.codecoachai.common.core.domain.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/analytics")
public class AdminAgentAnalyticsController {

    private final AgentAnalyticsService agentAnalyticsService;
    private final V4AdminPermissionGuard permissionGuard;

    @GetMapping("/agent/overview")
    public Result<AdminAgentOverviewVO> agentOverview(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:agent");
        return Result.success(agentAnalyticsService.adminAgentOverview(days));
    }

    @GetMapping("/agent/trend")
    public Result<List<TrendPointVO>> agentTrend(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:agent");
        return Result.success(agentAnalyticsService.adminAgentTrend(days));
    }

    @GetMapping("/agent/tasks")
    public Result<AdminAgentTaskStatsVO> agentTasks(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:agent");
        return Result.success(agentAnalyticsService.adminAgentTasks(days));
    }

    @GetMapping("/ai/overview")
    public Result<AdminAiOverviewVO> aiOverview(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:ai");
        return Result.success(agentAnalyticsService.adminAiOverview(days));
    }

    @GetMapping("/ai/failures")
    public Result<List<MetricPointVO>> aiFailures(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:ai");
        return Result.success(agentAnalyticsService.adminAiFailures(days));
    }
}
