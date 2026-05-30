package com.codecoachai.question.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.question.duplicate")
public class QuestionDuplicateProperties {

    private int maxBatchCheckCount = 100;

    private int maxRuleCandidateCount = 200;

    /**
     * 向量优先召回模式。启用后语义候选以向量检索（searchSimilarIndexed，不受 maxRuleCandidateCount 限制）为主路径，
     * 规则候选数收紧为 ruleFallbackCandidateCount，仅作兜底；硬指纹仍走精确查询。
     */
    private boolean vectorFirstEnabled = false;

    /** 向量优先模式下规则候选的兜底数量上限。 */
    private int ruleFallbackCandidateCount = 50;

    private int vectorSearchLimit = 30;

    private int embeddingBatchSize = 64;

    private double titleJaccardThreshold = 0.75D;

    private double titleLevenshteinThreshold = 0.82D;

    private double contentSimilarityThreshold = 0.70D;

    private double semanticSimilarityThreshold = 0.82D;

    private double semanticReviewThreshold = 0.78D;

    private double semanticStrongThreshold = 0.88D;

    private double semanticVectorWeight = 0.78D;

    private double semanticTextWeight = 0.22D;

    private double semanticMetadataWeight = 0.08D;

    private double semanticTagWeight = 0.08D;

    public double effectiveSemanticReviewThreshold() {
        double threshold = semanticReviewThreshold > 0D ? semanticReviewThreshold : semanticSimilarityThreshold;
        return clampScore(threshold);
    }

    public double effectiveSemanticStrongThreshold() {
        return Math.max(effectiveSemanticReviewThreshold(), clampScore(semanticStrongThreshold));
    }

    private double clampScore(double value) {
        return Math.min(Math.max(value, 0D), 1D);
    }
}
