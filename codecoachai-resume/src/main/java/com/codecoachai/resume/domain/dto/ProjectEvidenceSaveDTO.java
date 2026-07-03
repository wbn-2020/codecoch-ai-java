package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectEvidenceSaveDTO {

    @NotBlank(message = "project title is required")
    private String title;
    private String role;
    private String startDate;
    private String endDate;
    private String background;
    private String responsibility;
    private String techStack;
    private String difficulty;
    private String solution;
    private String result;
    private String reflection;
    private Long sourceResumeId;
    private Long sourceResumeProjectId;
    private Long targetJobId;
}
