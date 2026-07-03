package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.vo.analytics.CareerInsightOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import com.codecoachai.ai.agent.domain.vo.analytics.PersonalAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.TrendPointVO;
import com.codecoachai.ai.agent.service.AgentAnalyticsService;
import com.codecoachai.ai.agent.service.CareerInsightService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/analytics/personal")
public class AgentAnalyticsController {

    private final AgentAnalyticsService agentAnalyticsService;
    private final CareerInsightService careerInsightService;
    private final V4FeatureGate v4FeatureGate;

    @ModelAttribute
    public void requirePersonalAnalyticsEnabled() {
        SecurityAssert.requireLoginUserId();
        v4FeatureGate.requireGrowthEnabled();
    }

    @GetMapping("/agent-overview")
    public Result<PersonalAgentOverviewVO> agentOverview() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalOverview(userId));
    }

    @GetMapping("/overview")
    public Result<PersonalAgentOverviewVO> overview() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalOverview(userId));
    }

    @GetMapping("/task-trend")
    public Result<List<TrendPointVO>> taskTrend(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalTaskTrend(userId, days));
    }

    @GetMapping("/training-trend")
    public Result<List<TrendPointVO>> trainingTrend(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalTaskTrend(userId, days));
    }

    @GetMapping("/task-completion")
    public Result<List<TrendPointVO>> taskCompletion(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalTaskTrend(userId, days));
    }

    @GetMapping("/skill-distribution")
    public Result<List<MetricPointVO>> skillDistribution(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalSkillDistribution(userId, days));
    }

    @GetMapping("/interview-trend")
    public Result<List<TrendPointVO>> interviewTrend(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalInterviewTrend(userId, days));
    }

    @GetMapping("/agent-effectiveness")
    public Result<PersonalAgentOverviewVO> agentEffectiveness() {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentAnalyticsService.personalOverview(userId));
    }

    @GetMapping("/career-insights")
    public Result<CareerInsightOverviewVO> careerInsights(@RequestParam(required = false) Integer days) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(careerInsightService.personalCareerInsights(userId, days));
    }
}
