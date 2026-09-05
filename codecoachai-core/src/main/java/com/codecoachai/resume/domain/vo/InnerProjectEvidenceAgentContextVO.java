package com.codecoachai.resume.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InnerProjectEvidenceAgentContextVO {

    private Long projectEvidenceId;
    private String title;
    private String techStack;
    private Integer completenessScore;
    private String completenessStatus;
    private List<String> missingFields;
    private Long skillEvidenceCount;
    private List<String> topSkillNames;
    private Long targetJobId;
    private String suggestedActionPath;
}
