package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeDocumentVO {
    private Long id;
    private String title;
    private String documentType;
    private String status;
    private String normalizationVersion;
    private Integer chunkCount;
    private Integer duplicateChunkCount;
    private Integer nearDuplicateChunkCount;
    private Double nearDuplicateThreshold;
    private Long duplicateDocumentId;
    private Boolean duplicateDocument;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
