package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectSkillEvidenceSaveDTO {

    @NotBlank(message = "skill name is required")
    private String skillName;
    private String skillCategory;
    private String evidenceText;
    private String strengthLevel;
    private String jdKeyword;
    private String riskPoints;
    private String sourceType;
    private Boolean confirmed;
}
