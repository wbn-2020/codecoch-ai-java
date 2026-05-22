package com.codecoachai.ai.agent.domain.context;

import lombok.Data;

@Data
public class CandidateTask {

    private String candidateId;
    private String type;
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
}
