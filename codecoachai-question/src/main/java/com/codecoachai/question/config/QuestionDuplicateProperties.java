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

    private int maxVectorSeedCount = 300;

    private int vectorSearchLimit = 30;

    private int embeddingBatchSize = 64;

    private double titleJaccardThreshold = 0.75D;

    private double titleLevenshteinThreshold = 0.82D;

    private double contentSimilarityThreshold = 0.70D;

    private double semanticSimilarityThreshold = 0.82D;

    private double semanticVectorWeight = 0.78D;

    private double semanticTextWeight = 0.22D;
}
