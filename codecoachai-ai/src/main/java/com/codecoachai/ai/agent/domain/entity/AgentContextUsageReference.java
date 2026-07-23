package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_context_usage_reference")
public class AgentContextUsageReference extends BaseEntity {
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private String sourceVersion;
    private String consumerType;
    private Long consumerId;
    private String traceId;
    private String usageScene;
    private String usageStrength;
    private BigDecimal confidence;
    private String snapshotHash;
}
