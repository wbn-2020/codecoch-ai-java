package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_week_plan")
public class AgentWeekPlan extends BaseEntity {
    private Long userId;
    private Long targetJobId;
    private Long agentRunId;
    private String targetScopeKey;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String planStatus;
    private String summary;
    private String focusJson;
    private String traceId;
    private String resultSource;
    private Integer fallback;
    private String fallbackReason;
    private Integer snapshotVersion;
    private LocalDateTime generatedAt;
    private LocalDateTime refreshedAt;
}
