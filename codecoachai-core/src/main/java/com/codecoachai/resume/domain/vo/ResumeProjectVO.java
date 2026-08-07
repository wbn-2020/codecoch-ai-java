package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ResumeProjectVO {

    private Long id;
    private Long resumeId;
    private String projectName;
    private String projectPeriod;
    private String projectBackground;
    private String role;
    private String techStack;
    private String responsibility;
    private String coreFeatures;
    private String technicalDifficulties;
    private String optimizationResults;
    private String description;
    private String highlights;
    private Integer sort;
    private Integer sortOrder;
}
