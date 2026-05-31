package com.codecoachai.ai.agent.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeEvalRunRequestDTO {

    private List<Long> caseIds;
    private Boolean onlyEnabled = true;
    /**
     * Number of evaluation cases to run. If omitted, the legacy limit field is used for compatibility.
     */
    private Integer caseLimit;
    /**
     * Retrieval topK used inside each RAG sample. Kept as legacy request field.
     */
    private Integer limit;
    private Double minScore;
}
