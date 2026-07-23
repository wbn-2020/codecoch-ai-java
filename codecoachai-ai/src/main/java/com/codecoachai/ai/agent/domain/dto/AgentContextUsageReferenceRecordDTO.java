package com.codecoachai.ai.agent.domain.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AgentContextUsageReferenceRecordDTO {
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
