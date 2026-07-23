package com.codecoachai.ai.agent.domain.vo.review;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentReviewPlanSuggestionVO {

    private Long id;
    private Long reviewId;
    private Integer reviewVersion;
    private String title;
    private String content;
    private String reason;
    private String intentType;
    private String targetScope;
    private String confidenceLevel;
    private Boolean fallback;
    private String decisionStatus;
    private Integer decisionVersion;
    private LocalDateTime decidedAt;
    private String ignoredReason;
    private Boolean previouslyIgnored;
    private Boolean actionable;
}
