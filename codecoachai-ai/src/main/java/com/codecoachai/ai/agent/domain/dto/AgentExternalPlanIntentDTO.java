package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AgentExternalPlanIntentDTO {

    @NotBlank
    @Size(max = 128)
    private String sourceItemKey;
    @NotBlank
    @Size(max = 200)
    private String title;
    @Size(max = 2000)
    private String description;
    private LocalDate planDate;
    private Integer weekday;
    private Integer estimatedMinutes = 30;
    private String priority = "MEDIUM";
    private String confidenceLevel = "MEDIUM";
    private Boolean fallback = false;
    private String taskType = "PRACTICE";
    private String relatedSkillCode;
    private String relatedSkillName;
    private String relatedBizType;
    private Long relatedBizId;
    private String actionUrl;
}
