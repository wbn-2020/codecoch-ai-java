package com.codecoachai.interview.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class CreateInterviewDTO {

    private String mode;
    private String interviewMode;

    private Long resumeId;
    private Long applicationId;
    private String applicationPackageId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private Long resumeVersionId;
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
    private Long scenarioVersionId;
    private String recommendationSource;
    private String recommendationReason;
    private Boolean basedOnResume;
    private String trainingScene;
    private String targetSkillDomain;
    private List<String> targetSkillCodes;
    private String targetLevel;
    private List<Long> projectEvidenceIds;
    private String followUpIntensity;
}
