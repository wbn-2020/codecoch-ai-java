package com.codecoachai.interview.domain.dto;

import lombok.Data;

@Data
public class CreateInterviewDTO {

    private String mode;
    private String interviewMode;

    private Long resumeId;
    private Long applicationId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String title;
    private Integer maxQuestionCount;
    private String targetPosition;
    private String experienceLevel;
    private Long industryTemplateId;
    private String industryDirection;
    private String difficulty;
    private String interviewerStyle;
    private String practiceMode;
    private String recommendationSource;
    private String recommendationReason;
    private Boolean basedOnResume;
}
