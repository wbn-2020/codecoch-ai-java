package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class KnowledgeAskDTO {
    private String question;
    private Integer limit;
    private Double minScore;
    private String documentType;
}
