package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_review_plan_suggestion")
public class AgentReviewPlanSuggestion extends BaseEntity {
    private Long userId;
    private Long reviewId;
    private Integer reviewVersion;
    private String suggestionKey;
    private String suggestionFingerprint;
    private String title;
    private String content;
    private String reason;
    private String intentType;
    private String targetScope;
    private String intentJson;
    private String evidenceJson;
    private String confidenceLevel;
    private Boolean fallback;
    private String decisionStatus;
    private Integer decisionVersion;
    private LocalDateTime decidedAt;
    private String ignoredReason;
    private String sourceType;
    private Long sourceId;
    private Integer sourceVersion;
    private String sourceSnapshotHash;
    private String sourceItemKey;
}
