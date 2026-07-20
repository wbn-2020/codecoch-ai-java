package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_review")
public class AgentReview extends BaseEntity {
    private Long userId;
    private Long targetJobId;
    private LocalDate reviewDate;
    private String reviewType;
    private Long sourceTaskId;
    private String idempotencyKey;
    private String targetScopeKey;
    private Integer reviewVersion;
    private String sourceSnapshotHash;
    private String summary;
    private Integer doneCount;
    private Integer skippedCount;
    private Integer todoCount;
    private BigDecimal completionRate;
    private Integer readinessScore;
    private String nextActionsJson;
    private String reviewJson;
    private Long agentRunId;
    private Long aiCallLogId;
    private String confidenceLevel;
    private Boolean fallback;
}
