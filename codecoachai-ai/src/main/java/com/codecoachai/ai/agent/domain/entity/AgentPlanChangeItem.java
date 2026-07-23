package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan_change_item")
public class AgentPlanChangeItem extends BaseEntity {
    private Long userId;
    private Long changeSetId;
    private Long suggestionId;
    private String itemKey;
    private String changeType;
    private LocalDate targetDate;
    private Long sourceTaskId;
    private Long baseDailyRunId;
    private String beforeJson;
    private String afterJson;
    private String dailyImpactJson;
    private String weekImpactJson;
    private String validationStatus;
    private String warningCodesJson;
    private String confidenceLevel;
    private Boolean fallback;
    private String applyStatus;
    private Long appliedRunId;
    private Long appliedTaskId;
    private Long appliedWeekPlanId;
    private Long appliedWeekPlanItemId;
    private Integer applyCount;
    private String sourceItemKey;
}
