package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import com.codecoachai.interview.voicedelivery.VoiceDeliverySummaryVO;

@Data
public class InterviewListVO {

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
    private Long reportId;
    private Integer totalScore;
    private Boolean comparisonAvailable;
    private String comparisonUnavailableReason;
    private String comparisonRubricVersion;
    private String comparisonNormalizationSource;
    private List<InterviewComparisonReasonVO> comparisonWarnings;
    private Integer answeredQuestionCount;
    private VoiceDeliverySummaryVO voiceDeliverySummary;
    private LocalDateTime updatedAt;
}
