package com.codecoachai.ai.agent.domain.vo.weekplan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentPlanInfluenceVO {
    private Long id;
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
    private Boolean fallback;
    private LocalDateTime createdAt;
}
