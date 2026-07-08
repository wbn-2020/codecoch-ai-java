package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class CreateInterviewVO {

    private Long id;
    private Long applicationId;
    private Long applicationPackageId;
    private Long jdAnalysisId;
    private Long resumeVersionId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String title;
    private String mode;
    private String targetPosition;
    private String experienceLevel;
    private Long industryTemplateId;
    private String industryDirection;
    private String industryContext;
    private String difficulty;
    private String interviewerStyle;
    private Boolean basedOnResume;
    private String trainingScene;
    private String targetSkillDomain;
    private List<String> targetSkillCodes;
    private String targetLevel;
    private List<Long> projectEvidenceIds;
    private String followUpIntensity;
    private String status;
    private String reportStatus;
    private Integer maxQuestionCount;
    private Integer totalQuestionCount;
    private Integer answeredQuestionCount;
    private String overallProgress;
    private List<InterviewStageVO> stages;
}
