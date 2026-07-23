package com.codecoachai.ai.agent.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentPlanSuggestionIntentDTO {

    private Long sourceTaskId;
    private List<String> relatedTaskRefs = new ArrayList<>();
    private String taskType;
    private String title;
    private String description;
    private String reason;
    private String priority;
    private String targetPriority;
    private Integer estimatedMinutes;
    private Integer estimatedMinutesDelta;
    private String relatedSkillCode;
    private String relatedSkillName;
    private String relatedBizType;
    private Long relatedBizId;
    private String actionUrl;
}
