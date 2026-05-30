package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class KnowledgeEvalCaseQueryDTO {

    private String keyword;
    private Long expectedDocumentId;
    private String expectedDocumentType;
    private Boolean expectNoAnswer;
    private Integer enabled;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
