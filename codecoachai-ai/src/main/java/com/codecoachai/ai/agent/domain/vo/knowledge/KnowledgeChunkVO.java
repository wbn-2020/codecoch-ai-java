package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeChunkVO {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String chunkHash;
    private String sourceRef;
    private String embeddingModel;
    private Integer embeddingDimension;
    private LocalDateTime indexedAt;
    private String indexStatus;
    private String lastError;
    private Boolean duplicateInDocument;
    private Boolean cleanupCandidate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
