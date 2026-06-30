package com.codecoachai.ai.agent.domain.context;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private List<String> recentMemories = new ArrayList<>();
    private List<String> personalKnowledgeHints = new ArrayList<>();
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
}
