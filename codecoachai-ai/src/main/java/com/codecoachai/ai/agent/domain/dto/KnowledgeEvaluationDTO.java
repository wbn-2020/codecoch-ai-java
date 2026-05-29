package com.codecoachai.ai.agent.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeEvaluationDTO {

    private List<Sample> samples;
    private Integer limit;
    private Double minScore;

    @Data
    public static class Sample {
        private String caseId;
        private String query;
        private Long expectedDocumentId;
        private String expectedDocumentTitle;
        private String expectedDocumentType;
        private Long retrievalDocumentId;
        private String retrievalDocumentType;
        private Boolean expectNoAnswer;
        private String note;
    }
}
