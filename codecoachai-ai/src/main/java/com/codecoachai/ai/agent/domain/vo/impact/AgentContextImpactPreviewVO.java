package com.codecoachai.ai.agent.domain.vo.impact;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentContextImpactPreviewVO {
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private Integer referenceCount;
    private Integer recentReferenceCount;
    private List<String> affectedModules = new ArrayList<>();
    private List<AffectedConsumerVO> affectedConsumers = new ArrayList<>();
    private Boolean futureContextImpact;
    private Boolean historicalOnly;
    private Boolean safeToDisable;
    private List<String> warnings = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private String previewSource = "BACKEND_REFERENCES";
    private String resultSource = "BACKEND_REFERENCES";
    private LocalDateTime generatedAt;

    @Data
    public static class AffectedConsumerVO {
        private String consumerType;
        private Long consumerId;
        private String traceId;
        private String usageScene;
        private String usageStrength;
        private BigDecimal confidence;
        private String snapshotHash;
        private Boolean historical;
        private LocalDateTime createdAt;
        private String summary;
    }
}
