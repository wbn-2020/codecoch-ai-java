package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobApplicationEventStructuredReviewVO {

    private String schemaVersion;
    private String scenario;
    private String eventScope;
    private UserInputVO userInput;
    private List<ReviewFactVO> systemFacts = new ArrayList<>();
    private ReviewAnalysisVO analysis;
    private ReviewGenerationVO generation;

    @Data
    public static class UserInputVO {
        private String owner;
        private List<ReviewFactVO> observedFacts = new ArrayList<>();
        private ReviewFactVO externalFeedback;
        private String selfReflection;
    }

    @Data
    public static class ReviewFactVO {
        private String id;
        private String content;
        private String owner;
        private String sourceType;
    }

    @Data
    public static class ReviewSignalVO {
        private String content;
        private List<String> factRefs = new ArrayList<>();
        private String confidenceLevel;
        private String owner;
    }

    @Data
    public static class ReviewAnalysisVO {
        private String owner;
        private String summary;
        private List<String> limits = new ArrayList<>();
        private List<ReviewSignalVO> signals = new ArrayList<>();
        private List<String> adjustments = new ArrayList<>();
        private List<String> nextActions = new ArrayList<>();
    }

    @Data
    public static class ReviewGenerationVO {
        private String owner;
        private String status;
        private Boolean fallback;
        private String fallbackReason;
        private String confidenceLevel;
        private List<String> confidenceBasis = new ArrayList<>();
        private Long aiCallLogId;
        private String inputFingerprint;
        private String requestId;
        private String generatorVersion;
        private LocalDateTime startedAt;
        private LocalDateTime generatedAt;
    }
}
