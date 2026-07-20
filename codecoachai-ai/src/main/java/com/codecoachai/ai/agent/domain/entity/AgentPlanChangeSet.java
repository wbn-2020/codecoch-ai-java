package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan_change_set")
public class AgentPlanChangeSet extends BaseEntity {
    private Long userId;
    private Long reviewId;
    private Integer reviewVersion;
    private Long targetJobId;
    private String targetScopeKey;
    private LocalDate targetDate;
    private String status;
    private String selectionHash;
    private String sourceSnapshotHash;
    private Long baseDailyRunId;
    private String baseDailyStatus;
    private String baseDailyTaskHash;
    private Long baseWeekPlanId;
    private Integer baseWeekSnapshotVersion;
    private String baseWeekItemHash;
    private Integer previewVersion;
    private String previewHash;
    private String previewSummaryJson;
    private String resultSource;
    private Boolean fallback;
    private String previewRequestKeyHash;
    private String previewPayloadHash;
    private String confirmRequestKeyHash;
    private String confirmPayloadHash;
    private Integer lockVersion;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime appliedAt;
    private String failureCode;
    private String failureMessage;
    private String sourceType;
    private Long sourceId;
    private Integer sourceVersion;
    private String sourceContextHash;
}
