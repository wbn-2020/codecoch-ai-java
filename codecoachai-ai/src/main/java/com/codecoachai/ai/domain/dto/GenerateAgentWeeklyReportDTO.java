package com.codecoachai.ai.domain.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class GenerateAgentWeeklyReportDTO {

    private Long userId;
    private String weekStartDate;
    private String weekEndDate;
    private String targetScopeKey;
    private String timezone;
    private List<WeeklyFact> facts = new ArrayList<>();
    private List<WeeklySignal> signals = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private List<AllowedSuggestion> allowedSuggestions = new ArrayList<>();
    private List<AllowedPlanItem> allowedPlanItems = new ArrayList<>();

    @Data
    public static class WeeklyFact {

        private String factId;
        private String factType;
        private String label;
        private Object value;
        private String unit;
        private String scope;
        private String timeWindow;
        private List<String> sourceRefs = new ArrayList<>();
        private String calculationVersion;
    }

    @Data
    public static class WeeklySignal {

        private String signalId;
        private String signalType;
        private String direction;
        private String title;
        private String description;
        private Map<String, Object> metric = new LinkedHashMap<>();
        private String confidenceLevel;
        private Map<String, Object> sampleBoundary = new LinkedHashMap<>();
        private String scope;
        private String comparedScope;
        private List<String> sourceRefs = new ArrayList<>();
        private List<String> blockedConclusions = new ArrayList<>();
    }

    @Data
    public static class AllowedSuggestion {

        private String suggestionId;
        private String title;
        private String statement;
        private String primaryVariable;
        private List<String> fixedVariables = new ArrayList<>();
        private String expectedSignal;
        private String successMetric;
        private Integer minimumSample;
        private Integer observationDays;
        private String stopCondition;
        private String confidenceLevel;
        private List<String> basedOnSignalIds = new ArrayList<>();
        private List<String> sourceRefs = new ArrayList<>();
    }

    @Data
    public static class AllowedPlanItem {

        private String itemId;
        private String title;
        private String description;
        private String actionType;
        private Integer priority;
        private Integer estimatedMinutes;
        private List<String> sourceRefs = new ArrayList<>();
    }
}
