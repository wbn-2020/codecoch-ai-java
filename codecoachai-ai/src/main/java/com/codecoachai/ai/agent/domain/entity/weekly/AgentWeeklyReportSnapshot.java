package com.codecoachai.ai.agent.domain.entity.weekly;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_weekly_report_snapshot")
public class AgentWeeklyReportSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long weeklyReportId;
    private Integer snapshotVersion;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String targetScopeKey;
    private LocalDateTime rangeStartUtc;
    private LocalDateTime rangeEndUtc;
    private LocalDateTime sourceCutoffAt;
    private String inputHash;
    private String generationFingerprint;
    private String idempotencyKeyHash;
    private String idempotencyPayloadHash;
    private String requestId;
    private String calculationVersion;
    private String promptSchemaVersion;
    private String outputSchemaVersion;
    private String reportStatus;
    private String summary;
    private String confidenceLevel;
    private String factsJson;
    private String signalsJson;
    private String hypothesesJson;
    private String experimentSuggestionsJson;
    private String planDraftJson;
    private String coverageJson;
    private String resultSource;
    private Integer fallback;
    private String fallbackReason;
    private String traceId;
    private Long aiCallLogId;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
