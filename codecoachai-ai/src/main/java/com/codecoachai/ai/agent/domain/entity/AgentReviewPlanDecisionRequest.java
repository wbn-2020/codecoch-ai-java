package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_review_plan_decision_request")
public class AgentReviewPlanDecisionRequest extends BaseEntity {
    private Long userId;
    private Long reviewId;
    private String decisionRequestKeyHash;
    private String decisionPayloadHash;
    private String requestId;
}
