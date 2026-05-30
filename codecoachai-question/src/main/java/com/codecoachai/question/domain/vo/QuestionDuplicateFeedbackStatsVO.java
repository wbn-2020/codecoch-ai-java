package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class QuestionDuplicateFeedbackStatsVO {

    private Long totalCount;
    private Long pendingCount;
    private Long confirmedCount;
    private Long ignoredCount;
    private Long resolvedCount;
    private BigDecimal confirmationRate;
    private BigDecimal ignoreRate;
    private BigDecimal sampleCoverageRate;
    private BigDecimal averageSimilarityScore;
    private Map<String, Long> statusCounts;
    private Map<String, Long> matchTypeCounts;
    private Map<String, Long> scoreBandCounts;
    private List<Bucket> scoreBuckets;
    private String thresholdRecommendation;
    private List<String> warningItems;
    private LocalDateTime generatedAt;

    @Data
    public static class Bucket {
        private String label;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private Long totalCount;
        private Long pendingCount;
        private Long confirmedCount;
        private Long ignoredCount;
        private BigDecimal confirmationRate;
        private BigDecimal ignoreRate;
    }
}
