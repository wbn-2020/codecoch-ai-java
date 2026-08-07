package com.codecoachai.resume.domain.dto;

import java.util.Map;
import lombok.Data;

@Data
public class JobSearchExperimentReviewSaveDTO {

    private String factSummary;
    private String insightSummary;
    private String unsupportedConclusion;
    private String sampleWarning;
    private String nextAction;
    private Map<String, Object> strategy;
    private String aiTraceId;
    private String confidenceLevel;
}
