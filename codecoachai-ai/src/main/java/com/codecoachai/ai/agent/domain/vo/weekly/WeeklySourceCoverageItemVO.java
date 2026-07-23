package com.codecoachai.ai.agent.domain.vo.weekly;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklySourceCoverageItemVO {

    private String sourceType;
    private Long sourceId;
    private LocalDateTime sourceTime;
    private LocalDateTime sourceUpdatedAt;
    private String scopeKey;
    private String inclusionStatus;
    private String excludeReason;
    private String sourceHash;
    private String safeSummary;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
