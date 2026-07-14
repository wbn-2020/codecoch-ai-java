package com.codecoachai.ai.agent.domain.context;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobCoachAgentContext {

    private Long userId;
    private Long targetJobId;
    private LocalDate planDate;
    private TargetJobSnapshot targetJob;
    private List<ApplicationSnapshot> applications = new ArrayList<>();
    private List<ProjectEvidenceSnapshot> projectEvidences = new ArrayList<>();
    private RequirementReadinessSnapshot requirementReadiness;
    private List<JobExperimentSnapshot> jobExperiments = new ArrayList<>();
    private List<String> recentMemories = new ArrayList<>();
    private List<MemoryReference> recentMemoryReferences = new ArrayList<>();
    private List<String> personalKnowledgeHints = new ArrayList<>();
    private List<PersonalKnowledgeReference> personalKnowledgeReferences = new ArrayList<>();
    private String agentHistorySummary;
    private List<String> contextWarnings = new ArrayList<>();

    @Data
    public static class TargetJobSnapshot {
        private Long id;
        private String jobTitle;
        private String companyName;
        private String jobLevel;
        private String jdSource;
        private String analysisSummary;
        private Object requiredSkills;
        private Object interviewFocusPoints;
    }

    @Data
    public static class ApplicationSnapshot {
        private Long id;
        private Long targetJobId;
        private Long resumeVersionId;
        private Long resumeId;
        private Integer resumeVersionNo;
        private String resumeVersionName;
        private Integer resumeVersionCurrentFlag;
        private Long matchReportId;
        private String companyName;
        private String jobTitle;
        private String source;
        private String status;
        private LocalDateTime appliedAt;
        private LocalDateTime nextFollowUpAt;
        private Boolean followUpOverdue;
        private Boolean followUpDueToday;
        private Long daysUntilFollowUp;
        private String note;
        private Long latestEventId;
        private String latestEventType;
        private LocalDateTime latestEventTime;
        private String latestEventSummary;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class ProjectEvidenceSnapshot {
        private Long projectEvidenceId;
        private String title;
        private String techStack;
        private Integer completenessScore;
        private List<String> missingFields;
        private Long skillEvidenceCount;
        private List<String> topSkillNames;
        private Long targetJobId;
        private String suggestedActionPath;
    }

    @Data
    public static class RequirementReadinessSnapshot {
        private Long targetJobId;
        private Long jdAnalysisId;
        private Long snapshotId;
        private String snapshotHash;
        private String policyVersion;
        private LocalDateTime generatedAt;
        private Integer readinessScore;
        private String readinessLevel;
        private String confidenceLevel;
        private Boolean fallback;
        private Boolean matrixCurrent;
        private Boolean sampleSufficient;
        private Integer requirementCount;
        private List<String> warnings = new ArrayList<>();
        private List<MissingRequirementSnapshot> missingRequirements = new ArrayList<>();
    }

    @Data
    public static class MissingRequirementSnapshot {
        private Long requirementId;
        private String requirementKey;
        private String requirementType;
        private String requirementName;
        private String priority;
        private String coverageLevel;
        private String confidenceLevel;
        private Boolean fallback;
        private List<Long> projectEvidenceIds = new ArrayList<>();
    }

    @Data
    public static class JobExperimentSnapshot {
        private Long id;
        private String title;
        private String targetDirection;
        private String status;
        private Integer sampleCount;
        private String confidenceLevel;
        private String sampleWarning;
        private String nextStrategy;
    }

    @Data
    public static class MemoryReference {
        private Long id;
        private String memoryType;
        private String sourceType;
        private Long sourceId;
        private BigDecimal confidence;
        private String snapshotHash;
    }

    @Data
    public static class PersonalKnowledgeReference {
        private String sourceType;
        private Long sourceId;
        private String sourceVersion;
        private String sourceTitle;
        private BigDecimal confidence;
        private String snapshotHash;
    }
}
