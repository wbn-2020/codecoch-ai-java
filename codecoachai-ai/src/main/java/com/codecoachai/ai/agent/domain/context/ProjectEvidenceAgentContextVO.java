package com.codecoachai.ai.agent.domain.context;

import java.util.List;
import lombok.Data;

@Data
public class ProjectEvidenceAgentContextVO {

    private Long projectEvidenceId;
    private String title;
    private String techStack;
    private Integer completenessScore;
    private List<String> missingFields;
    private Long skillEvidenceCount;
    private List<String> topSkillNames;
    private Long targetJobId;
    private String suggestedActionPath;
}
