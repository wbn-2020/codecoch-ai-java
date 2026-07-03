package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.vo.analytics.CareerInsightOverviewVO;

public interface CareerInsightService {

    CareerInsightOverviewVO personalCareerInsights(Long userId, Integer days);
}
