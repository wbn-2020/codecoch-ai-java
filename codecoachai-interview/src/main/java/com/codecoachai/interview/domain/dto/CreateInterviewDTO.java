package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInterviewDTO {

    @NotBlank(message = "mode is required")
    private String mode;

    private Long resumeId;
    private String title;
    private Integer maxQuestionCount;
    private String targetPosition;
    private String experienceLevel;
    private String industryDirection;
    private String difficulty;
    private String interviewerStyle;
    private Boolean basedOnResume;
}
