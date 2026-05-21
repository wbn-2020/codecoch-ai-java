package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAgentTaskStatsVO;
import com.codecoachai.ai.agent.domain.vo.analytics.AdminAiOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import com.codecoachai.ai.agent.domain.vo.analytics.PersonalAgentOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.TrendPointVO;
import java.util.List;

public interface AgentAnalyticsService {

    PersonalAgentOverviewVO personalOverview(Long userId);

    List<TrendPointVO> personalTaskTrend(Long userId, Integer days);

    List<TrendPointVO> personalInterviewTrend(Long userId, Integer days);

    List<MetricPointVO> personalSkillDistribution(Long userId, Integer days);

    AdminAgentOverviewVO adminAgentOverview(Integer days);

    List<TrendPointVO> adminAgentTrend(Integer days);

    AdminAgentTaskStatsVO adminAgentTasks(Integer days);

    AdminAiOverviewVO adminAiOverview(Integer days);

    List<MetricPointVO> adminAiFailures(Integer days);
}
