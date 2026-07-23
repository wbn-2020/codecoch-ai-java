package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import com.codecoachai.interview.voicedelivery.VoiceDeliverySummaryVO;

@Data
public class InterviewReportVO {

    private Long id;
    private Long sessionId;
    private Long userId;
    private Long applicationId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String targetJobTitle;
    private String targetCompanyName;
    private String jdEvidenceSummary;
    private List<InterviewReportMissingSkillVO> missingSkills;
    private String status;
    private Integer totalScore;
    private String summary;
    private Map<String, Object> stageReports;
    private Map<String, Object> stageScores;
    private List<String> weakPoints;
    private List<String> strengths;
    private List<String> mainProblems;
    private List<String> projectProblems;
    private List<String> reviewSuggestions;
    private List<String> recommendedQuestions;
    private List<InterviewReportNextActionVO> nextActions;
    private List<Map<String, Object>> questionReviews;
    private List<Map<String, Object>> rubricScores;
    private List<Map<String, Object>> followUpTree;
    private List<Map<String, Object>> adviceEvidence;
    private List<Map<String, Object>> abilityProfileUpdates;
    private String rubricVersion;
    private List<InterviewVoiceTraceVO> voiceTraces;
    private VoiceDeliverySummaryVO voiceDeliverySummary;
    private String reportContent;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private String failureReason;
    private String sourceType;
    private Long sourceId;
    private String trustStatus;
    private String evidenceSummary;
    private Boolean fallback;
    private Boolean remediationAvailable;
    private Boolean strongRemediationAvailable;
    private String strongRemediationUnavailableReason;
    private Boolean comparisonAvailable;
    private String comparisonUnavailableReason;
    private String comparisonRubricVersion;
    private String comparisonNormalizationSource;
    private List<InterviewComparisonReasonVO> comparisonWarnings;
    private List<Map<String, Object>> comparisonRubricScores;
    private Long sourceReportId;
    private List<Long> sourceRequirementIds;
    private String practicePurpose;
    private String remediationStrength;
    private Boolean remediationCreated;
    private Long remediationId;
    private Long remediationTargetSessionId;
    private String remediationStatus;
}
