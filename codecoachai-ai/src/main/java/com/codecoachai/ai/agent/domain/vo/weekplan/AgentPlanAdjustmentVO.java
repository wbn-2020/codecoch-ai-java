package com.codecoachai.ai.agent.domain.vo.weekplan;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentPlanAdjustmentVO {
    private Long id;
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
    private LocalDateTime createdAt;
}
