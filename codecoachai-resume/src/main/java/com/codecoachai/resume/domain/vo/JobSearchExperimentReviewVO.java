package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JobSearchExperimentReviewVO {

    private Long id;
    private Long experimentId;
    private String factSummary;
    private String insightSummary;
    private String unsupportedConclusion;
    private String sampleWarning;
    private String nextAction;
    private Map<String, Object> strategy;
    private List<String> facts = new ArrayList<>();
    private Map<String, Object> sampleBoundary;
    private List<String> unsupportedConclusions = new ArrayList<>();
    private List<String> weakObservations = new ArrayList<>();
    private List<Map<String, Object>> actionCandidates = new ArrayList<>();
    private Map<String, Object> reviewDsl;
    private Map<String, Object> trustedSuggestion;
    private String aiTraceId;
    private String traceId;
    private Long aiCallLogId;
    private String resultSource;
    private Boolean fallback;
    private Map<String, Object> qualityGate;
    private String confidenceLevel;
    private Integer demoFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
