package com.codecoachai.ai.agent.domain.vo.weekly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklyReportSignalVO {

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
