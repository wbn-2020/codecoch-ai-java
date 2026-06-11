package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeProjectSaveDTO {

    @NotBlank(message = "请填写项目名称")
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
