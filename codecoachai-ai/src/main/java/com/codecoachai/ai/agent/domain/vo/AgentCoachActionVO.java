package com.codecoachai.ai.agent.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentCoachActionVO {

    private String actionType;
    private Long taskId;
    private String summary;
    private List<String> reasons = new ArrayList<>();
    private List<String> evidenceRefs = new ArrayList<>();
    private List<SuggestionEvidenceSourceVO> evidenceSources = new ArrayList<>();
    private Boolean fallback;
    private String nextAction;
    private String requestId;
    private String traceId;
    private String idempotencyKey;
    private String resultSource;
    private Long aiCallLogId;
    private Long latencyMs;
    private Double estimatedCost;
}
