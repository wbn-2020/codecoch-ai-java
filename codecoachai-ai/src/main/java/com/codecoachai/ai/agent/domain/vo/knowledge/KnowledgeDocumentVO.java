package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeDocumentVO {
    private Long id;
    private String title;
    private String documentType;
    private String status;
    private Integer chunkCount;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
