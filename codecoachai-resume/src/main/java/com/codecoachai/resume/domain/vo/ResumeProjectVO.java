package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ResumeProjectVO {

    private Long id;
    private Long resumeId;
    private String projectName;
    private String role;
    private String techStack;
    private String description;
    private String highlights;
    private Integer sort;
}
