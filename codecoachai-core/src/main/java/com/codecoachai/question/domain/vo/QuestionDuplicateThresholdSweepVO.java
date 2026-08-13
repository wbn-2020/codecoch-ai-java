package com.codecoachai.question.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateThresholdSweepVO {
    private Integer sampleCount;
    private Integer evaluatedCount;
    private Integer positiveExpectedCount;
    private Integer negativeExpectedCount;
    private Integer bestThreshold;
    private BigDecimal bestPrecision;
    private BigDecimal bestRecall;
    private BigDecimal bestF1;
    private BigDecimal bestAccuracy;
    private List<Bucket> buckets;
    private LocalDateTime generatedAt;

    @Data
    public static class Bucket {
        private Integer threshold;
        private Integer truePositive;
        private Integer falsePositive;
        private Integer trueNegative;
        private Integer falseNegative;
        private Integer predictedPositiveCount;
        private BigDecimal precision;
        private BigDecimal recall;
        private BigDecimal f1;
        private BigDecimal accuracy;
        private BigDecimal reviewWorkloadRate;
    }
}
