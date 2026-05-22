package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentTaskVO {

    private Long id;
    private Long runId;
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
    private String skipReason;
    private LocalDate dueDate;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime skippedAt;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
