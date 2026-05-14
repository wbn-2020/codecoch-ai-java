package com.codecoachai.interview.domain.dto;

import lombok.Data;

@Data
public class CreateInterviewDTO {

    private String mode;
    private String interviewMode;

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
