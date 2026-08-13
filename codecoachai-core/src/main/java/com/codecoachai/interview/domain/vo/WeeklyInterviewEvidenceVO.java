package com.codecoachai.interview.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklyInterviewEvidenceVO {

    private Long userId;
    private List<SessionItem> sessions = new ArrayList<>();
    private List<ReportItem> reports = new ArrayList<>();
    private List<ComparisonGroupItem> comparisonGroups = new ArrayList<>();
    private Map<String, Integer> sourceCounts = new LinkedHashMap<>();
    private String consistencyLevel;
    private Boolean truncated;
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class SessionItem {

        private Long sessionId;
        private Long targetJobId;
        private Long applicationId;
        private String mode;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime updatedAt;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
        private String safeSummary;
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class ReportItem {

        private Long reportId;
        private Long sessionId;
        private Long targetJobId;
        private Long applicationId;
        private String reportStatus;
        private String trustStatus;
        private Boolean fallback;
        private Boolean sampleInsufficient;
        private BigDecimal totalScore;
        private String rubricVersion;
        private String dimensionFingerprint;
        private LocalDateTime generatedAt;
        private LocalDateTime updatedAt;
        private Boolean included;
        private String excludeReason;
        private String sourceHash;
        private String safeSummary;
        private List<String> normalizedWeaknesses = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    @Data
    public static class ComparisonGroupItem {

        private String comparisonKey;
        private Long targetJobId;
        private String rubricVersion;
        private String dimensionFingerprint;
        private Integer trustedReportCount;
        private Integer excludedReportCount;
        private BigDecimal firstScore;
        private BigDecimal lastScore;
        private BigDecimal averageScore;
        private String direction;
        private List<Long> sourceReportIds = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }
}
