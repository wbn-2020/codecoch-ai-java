package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class SuggestionEvidenceSourceVO {

    private String id;
    private String sourceType;
    private Long sourceId;
    private String sourceTitle;
    private String sourceLabel;
    private String evidenceSummary;
    private String sourceSummary;
    private String trustStatus;
    private LocalDateTime sourceUpdatedAt;
    private String actionUrl;
    private Map<String, Object> metadata;
}
