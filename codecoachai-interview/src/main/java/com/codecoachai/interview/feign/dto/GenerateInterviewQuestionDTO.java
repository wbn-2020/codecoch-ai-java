package com.codecoachai.interview.feign.dto;

import lombok.Data;

@Data
public class GenerateInterviewQuestionDTO {

    private String mode;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String skillGapContext;
    private String stageType;
    private String currentStage;
    private String focusPoints;
    private String targetPosition;
    private String experienceLevel;
    private String industryDirection;
    private String industryContext;
    private String difficulty;
    private String interviewerStyle;
    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private String resumeSummary;
    private String resumeContent;
    private String projectContent;
    private String historySummary;
}
