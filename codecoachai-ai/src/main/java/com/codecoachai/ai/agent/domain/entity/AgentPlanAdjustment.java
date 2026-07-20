package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan_adjustment")
public class AgentPlanAdjustment extends BaseEntity {
    private Long userId;
    private Long weekPlanId;
    private Long weekPlanItemId;
    private Long agentTaskId;
    private String adjustmentType;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private String traceId;
    private Integer snapshotVersion;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime occurredAt;
    private String metadataJson;
    private String eventKey;
}
