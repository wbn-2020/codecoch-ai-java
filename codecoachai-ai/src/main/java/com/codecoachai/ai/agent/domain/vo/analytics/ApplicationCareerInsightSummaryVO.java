package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class ApplicationCareerInsightSummaryVO {

    private Integer rangeDays;
    private Long applicationCount = 0L;
    private Long followedUpApplicationCount = 0L;
    private Long interviewApplicationCount = 0L;
    private Long offerApplicationCount = 0L;
    private Long rejectedOrClosedApplicationCount = 0L;
    private CareerFunnelVO funnel;
    private ApplicationQualityVO quality;
    private ApplicationQualityVO applicationQuality;
    private ResumeVersionEffectVO resumeVersionEffect;
}
