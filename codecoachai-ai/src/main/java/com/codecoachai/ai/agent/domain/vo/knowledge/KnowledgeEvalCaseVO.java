package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeEvalCaseVO {

    private Long id;
    private String caseId;
    private String query;
    private Long expectedDocumentId;
    private String expectedDocumentTitle;
    private String expectedDocumentType;
    private Boolean expectNoAnswer;
    private String note;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
