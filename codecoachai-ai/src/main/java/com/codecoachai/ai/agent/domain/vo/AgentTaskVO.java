package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentTaskVO {

    private Long id;
    private Long runId;
    private String schemaVersion;
    private String traceId;
    private Long aiCallLogId;
    private Long promptVersionId;
    private String resultSource;
    private String resultSourceLabel;
    private Boolean mock;
    private Long targetJobId;
    private String candidateId;
    private String taskType;
    private String title;
    private String description;
    private String reason;
    private String priority;
    private Integer estimatedMinutes;
    private Integer estimatedEffortMinutes;
    private String relatedSkillCode;
    private String relatedSkillName;
    private String relatedBizType;
    private Long relatedBizId;
    private String actionUrl;
    private String actionType;
    private String sourceType;
    private Long sourceId;
    private String trustStatus;
    private String evidenceSummary;
    private List<SuggestionEvidenceSourceVO> evidenceSources = new ArrayList<>();
    private SuggestionQualityGateVO qualityGate;
    private Boolean fallback;
    private Long reviewId;
    private String reviewSummary;
    private List<String> reviewNextActions = new ArrayList<>();
    private String reviewSource;
    private String reviewSourceLabel;
    private String reviewNote;
    private String status;
    private String skipReason;
    private String deferReason;
    private LocalDate dueDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deferredAt;
    private LocalDateTime skippedAt;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ActivationHandoffVO> activationHandoffs = new ArrayList<>();
}
