package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan_influence")
public class AgentPlanInfluence extends BaseEntity {
    private Long userId;
    private Long weekPlanId;
    private Long weekPlanItemId;
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private String consumerType;
    private Long consumerId;
    private Long usageReferenceId;
    private String usageScene;
    private String influenceStrength;
    private BigDecimal confidence;
    private String traceId;
    private Integer snapshotVersion;
    private String snapshotHash;
    private Integer fallback;
}
