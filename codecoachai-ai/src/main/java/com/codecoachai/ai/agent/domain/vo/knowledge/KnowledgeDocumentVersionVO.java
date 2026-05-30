package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeDocumentVersionVO {
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String title;
    private String documentType;
    private String content;
    private String contentHash;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
