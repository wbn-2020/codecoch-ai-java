package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_week_plan_item")
public class AgentWeekPlanItem extends BaseEntity {
    private Long weekPlanId;
    private Long userId;
    private String layer;
    private String actionType;
    private String title;
    private String description;
    private String reason;
    private String relatedBizType;
    private Long relatedBizId;
    private String relatedBizTitle;
    private Long agentTaskId;
    private String priority;
    private BigDecimal confidence;
    private String trustStatus;
    private Integer fallback;
    private String fallbackReason;
    private String traceId;
    private Integer snapshotVersion;
    private String confidenceLevel;
    private Integer sampleInsufficient;
    private String sampleWarning;
    private String itemStatus;
    private LocalDate plannedDate;
    private LocalDate dueDate;
    private String actionUrl;
    private String evidenceJson;
    private Integer sortOrder;
}
