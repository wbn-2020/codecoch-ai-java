package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.util.Map;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeStatsVO {
    private Integer documentCount;
    private Integer chunkCount;
    private Integer duplicateChunkCount;
    private Boolean vectorEnabled;
    private String retrievalMode;
    private String chunkStrategy;
    private Map<String, Integer> documentTypeCounts;
    private Map<String, Integer> indexStatusCounts;
    private Map<String, Integer> embeddingModelCounts;
    private Map<String, Integer> duplicateTypeCounts;
    private List<DuplicateDocumentHotspot> duplicateDocumentHotspots;

    @Data
    public static class DuplicateDocumentHotspot {
        private Long documentId;
        private String title;
        private String documentType;
        private Integer duplicateChunkCount;
        private Integer chunkCount;
        private Double duplicateRatio;
    }
}
