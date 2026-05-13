package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeProjectSaveDTO {

    @NotBlank(message = "projectName is required")
    private String projectName;

    private String role;
    private String techStack;
    private String description;
    private String highlights;
    private Integer sort;
}
