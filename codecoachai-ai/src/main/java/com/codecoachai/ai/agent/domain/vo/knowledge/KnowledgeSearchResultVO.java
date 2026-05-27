package com.codecoachai.ai.agent.domain.vo.knowledge;

import lombok.Data;

@Data
public class KnowledgeSearchResultVO {
    private Long documentId;
    private Long chunkId;
    private String title;
    private String documentType;
    private String snippet;
    private String sourceRef;
    private Double score;
    private String matchType;
}
