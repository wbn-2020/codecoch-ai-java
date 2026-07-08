package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class TraceGovernanceSuggestionVO {

    private String id;
    private String actionType;
    private String title;
    private String reason;
    private String riskLevel;
    private String nodeId;
    private String targetType;
    private String targetId;
    private TraceLinkVO link;
    private Boolean executableInCockpit = false;
    private String requiredPermission;
}
