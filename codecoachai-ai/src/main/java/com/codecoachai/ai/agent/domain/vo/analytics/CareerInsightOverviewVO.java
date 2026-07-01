package com.codecoachai.ai.agent.domain.vo.analytics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerInsightOverviewVO {

    private Integer rangeDays;
    private LocalDateTime generatedAt;
    private CareerFunnelVO funnel;
    private ApplicationQualityVO applicationQuality;
    private ResumeVersionEffectVO resumeVersionEffect;
    private InterviewWeaknessInsightVO interviewWeaknesses;
    private List<CareerRecommendedActionVO> recommendedActions = new ArrayList<>();
    private List<String> dataWarnings = new ArrayList<>();
}
