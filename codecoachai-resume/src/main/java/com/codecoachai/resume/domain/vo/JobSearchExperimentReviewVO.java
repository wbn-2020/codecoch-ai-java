package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
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
    private String aiTraceId;
    private String confidenceLevel;
    private Integer demoFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
