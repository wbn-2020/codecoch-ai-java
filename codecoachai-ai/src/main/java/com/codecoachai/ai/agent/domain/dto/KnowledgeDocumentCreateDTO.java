package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class KnowledgeDocumentCreateDTO {
    private String title;
    private String documentType;
    private String content;
}
