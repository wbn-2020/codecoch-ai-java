package com.codecoachai.ai.agent.domain.vo.memory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AgentMemoryVO {
    private Long id;
    private String memoryType;
    private String content;
    private String sourceType;
    private Long sourceId;
    private BigDecimal confidence;
    private Integer enabled;
    private String memoryStatus;
    private LocalDateTime confirmedAt;
    private String disabledReason;
    private Boolean lowConfidence;
    private Boolean canBeEvidence;
    private String evidenceTrustStatus;
    private List<String> impactPreview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
