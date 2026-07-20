package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateAgentWeeklyReportVO {

    private String summary;
    private List<String> factNarrative = new ArrayList<>();
    private List<String> signalNarrative = new ArrayList<>();
    private List<Hypothesis> hypotheses = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private Long aiCallLogId;
    private String rawResponse;

    @Data
    public static class Hypothesis {

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
        private String status;
    }
}
