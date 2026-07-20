package com.codecoachai.ai.agent.domain.vo.weekly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklyExperimentSuggestionVO {

    private String suggestionId;
    private String semanticKey;
    private String title;
    private String hypothesis;
    private String primaryVariable;
    private List<String> fixedVariables = new ArrayList<>();
    private List<Map<String, Object>> eligibleSegments = new ArrayList<>();
    private String expectedSignal;
    private String successMetric;
    private Integer targetSample;
    private Integer minimumSample;
    private Integer observationDays;
    private String stopCondition;
    private String confidenceLevel;
    private List<String> basedOnSignalIds = new ArrayList<>();
    private List<String> sourceRefs = new ArrayList<>();
    private String status = "TO_VALIDATE";
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
