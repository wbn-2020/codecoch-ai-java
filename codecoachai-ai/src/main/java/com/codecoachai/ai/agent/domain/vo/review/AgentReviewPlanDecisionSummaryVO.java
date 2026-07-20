package com.codecoachai.ai.agent.domain.vo.review;

import lombok.Data;

@Data
public class AgentReviewPlanDecisionSummaryVO {

    private Integer pendingCount = 0;
    private Integer acceptedCount = 0;
    private Integer ignoredCount = 0;
    private Integer supersededCount = 0;
}
