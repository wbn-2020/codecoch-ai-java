package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_task")
public class AgentTask extends BaseEntity {

    private Long userId;
    private Long agentRunId;
    private Long targetJobId;
    private String candidateId;
    private String taskType;
    private String title;
    private String description;
    private String reason;
    private String priority;
    private Integer estimatedMinutes;
    private String relatedSkillCode;
    private String relatedSkillName;
    private String relatedBizType;
    private Long relatedBizId;
    private Long planChangeItemId;
    private String planOriginType;
    private Long planOriginId;
    private Boolean userConfirmed;
    private String actionUrl;
    private String status;
    private String skipReason;
    private String deferReason;
    private LocalDate dueDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deferredAt;
    private LocalDateTime skippedAt;
    private Integer sortOrder;
}
