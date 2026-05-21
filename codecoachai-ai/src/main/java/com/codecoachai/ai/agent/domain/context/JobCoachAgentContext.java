package com.codecoachai.ai.agent.domain.context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobCoachAgentContext {

    private Long userId;
    private Long targetJobId;
    private LocalDate planDate;
    private TargetJobSnapshot targetJob;
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
}
