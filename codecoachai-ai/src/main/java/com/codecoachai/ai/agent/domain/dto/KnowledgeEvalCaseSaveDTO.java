package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class KnowledgeEvalCaseSaveDTO {

    private Long id;
    private String caseId;
    private String query;
    private Long expectedDocumentId;
    private String expectedDocumentTitle;
    private String expectedDocumentType;
    private Long retrievalDocumentId;
    private String retrievalDocumentType;
    private Boolean expectNoAnswer;
    private String note;
    private Integer enabled;
}
