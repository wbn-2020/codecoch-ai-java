package com.codecoachai.ai.agent.config;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.knowledge")
public class KnowledgeProperties {
    private String collection = "personal_knowledge_chunk";
    private String chunkStrategy = "SEMANTIC_BLOCK_800_OVERLAP_80";
    private int chunkSize = 800;
    private int chunkOverlap = 80;
    private int minChunkSize = 180;
    private int embeddingBatchSize = 64;
    private int askDefaultLimit = 5;
    private long uploadMaxBytes = 8L * 1024 * 1024;
    private int uploadMaxTextChars = 100_000;
    private double nearDuplicateThreshold = 0.88D;
    private double askMinScore = 0.55D;
    private Set<String> uploadExtensions = new LinkedHashSet<>(Set.of("txt", "md", "markdown", "pdf", "docx", "doc"));

    public int safeChunkSize() {
        return Math.max(chunkSize, 1);
    }

    public int safeChunkOverlap() {
        return Math.max(Math.min(chunkOverlap, safeChunkSize() - 1), 0);
    }

    public int safeMinChunkSize() {
        return Math.max(Math.min(minChunkSize, safeChunkSize()), 1);
    }

    public int safeEmbeddingBatchSize() {
        return Math.max(embeddingBatchSize, 1);
    }

    public int safeAskDefaultLimit() {
        return Math.max(askDefaultLimit, 1);
    }

    public int safeUploadMaxTextChars() {
        return Math.max(uploadMaxTextChars, 1);
    }

    public double safeNearDuplicateThreshold() {
        return Math.min(Math.max(nearDuplicateThreshold, 0D), 1D);
    }

    public double safeAskMinScore() {
        return Math.min(Math.max(askMinScore, 0D), 1D);
    }
}
