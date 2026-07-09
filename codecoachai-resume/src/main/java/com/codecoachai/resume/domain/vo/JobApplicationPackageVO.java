package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JobApplicationPackageVO {

    private String id;
    private String packageNo;
    private Long userId;
    private Long targetJobId;
    private Long jobApplicationId;
    private Long jdAnalysisId;
    private Long recommendedResumeVersionId;
    private Long matchReportId;
    private List<Long> projectEvidenceIds = new ArrayList<>();
    private Long interviewQuestionGroupId;
    private String readinessLevel;
    private Integer readinessScore;
    private String readinessReason;
    private String packageStatus;
    private String companyName;
    private String jobTitle;
    private String resultSource;
    private Boolean fallback;
    private String fallbackReason;
    private Integer snapshotVersion;
    private Integer contextPackageCount;
    private Integer contextVersionNo;
    private Long latestContextPackageId;
    private String latestContextPackageNo;
    private Boolean latestContextPackage;
    private LocalDateTime refreshedAt;
    private LocalDateTime generatedAt;
    private RecommendedResumeVO recommendedResume;
    private MatchSummaryVO matchSummary;
    private ProjectEvidenceCoverageVO projectEvidenceCoverage;
    private InterviewPreparationVO interviewPreparation;
    private List<ApplicationPackageChecklistItemVO> checklist = new ArrayList<>();
    private List<CareerRiskSignalVO> riskSignals = new ArrayList<>();
    private List<CareerActionItemVO> actions = new ArrayList<>();
    private List<ExplainableSuggestionVO> suggestions = new ArrayList<>();
    private List<EvidenceSourceVO> evidenceSources = new ArrayList<>();
    private SuggestionTraceVO trace;

    @Data
    public static class RecommendedResumeVO {
        private Long resumeVersionId;
        private Long resumeId;
        private Integer versionNo;
        private String versionName;
        private Integer currentFlag;
        private String reason;
    }

    @Data
    public static class MatchSummaryVO {
        private Integer overallScore;
        private Integer techStackScore;
        private Integer projectExperienceScore;
        private Integer businessFitScore;
        private Integer communicationScore;
        private String status;
        private String trustStatus;
        private Boolean fallback;
        private Integer schemaWarningCount;
        private String summary;
        private List<String> gaps = new ArrayList<>();
        private List<String> interviewTopics = new ArrayList<>();
    }

    @Data
    public static class ProjectEvidenceCoverageVO {
        private List<String> coveredRequirements = new ArrayList<>();
        private List<String> insufficientRequirements = new ArrayList<>();
        private List<String> suggestedFields = new ArrayList<>();
        private List<ProjectEvidenceSummaryVO> selectedEvidence = new ArrayList<>();
    }

    @Data
    public static class ProjectEvidenceSummaryVO {
        private Long id;
        private String title;
        private String role;
        private String techStack;
        private Integer completenessScore;
        private String completenessStatus;
        private List<String> missingFields = new ArrayList<>();
    }

    @Data
    public static class InterviewPreparationVO {
        private String entryUrl;
        private List<String> topics = new ArrayList<>();
        private Map<String, Object> createParams = new LinkedHashMap<>();
    }

    @Data
    public static class ApplicationPackageChecklistItemVO {
        private String key;
        private String label;
        private Boolean passed;
        private String reason;
        private String severity;
        private String actionType;
        private String actionUrl;
        private List<String> evidenceSourceIds = new ArrayList<>();
    }

    @Data
    public static class CareerRiskSignalVO {
        private String key;
        private String level;
        private String title;
        private String description;
        private List<String> evidenceSourceIds = new ArrayList<>();
    }

    @Data
    public static class CareerActionItemVO {
        private String id;
        private String actionType;
        private String title;
        private String description;
        private String priority;
        private String status;
        private String actionUrl;
        private String sourceType;
        private String sourceId;
        private List<String> evidenceSourceIds = new ArrayList<>();
    }

    @Data
    public static class ExplainableSuggestionVO {
        private String id;
        private String suggestionType;
        private String title;
        private String content;
        private String confidence;
        private String boundary;
        private List<String> evidenceSourceIds = new ArrayList<>();
    }

    @Data
    public static class EvidenceSourceVO {
        private String id;
        private String sourceType;
        private String sourceId;
        private String title;
        private String summary;
        private String confidence;
    }

    @Data
    public static class SuggestionTraceVO {
        private String traceId;
        private Boolean fallback;
        private Boolean degraded;
        private Boolean mock;
        private String inputSummary;
        private String outputSummary;
    }
}
