package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentPlanTaskSnapshotDTO {

    private Long taskId;
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
    private String actionUrl;
    private String status;
    private LocalDate dueDate;
    private Long planChangeItemId;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
