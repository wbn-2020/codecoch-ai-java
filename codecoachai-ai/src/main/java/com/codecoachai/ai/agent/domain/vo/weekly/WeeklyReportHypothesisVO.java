package com.codecoachai.ai.agent.domain.vo.weekly;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class WeeklyReportHypothesisVO {

    private String hypothesisId;
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
    private String status = "TO_VALIDATE";
}
