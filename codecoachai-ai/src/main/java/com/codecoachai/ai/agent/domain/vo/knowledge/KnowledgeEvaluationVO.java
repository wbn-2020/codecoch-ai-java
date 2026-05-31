package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeEvaluationVO {

    private Integer sampleCount;
    private Integer evaluatedCount;
    private Integer passedCount;
    private Integer failedCount;
    private Double passRate;
    private Integer limit;
    private Double minScore;
    private List<Item> items;
    private LocalDateTime generatedAt;

    @Data
    public static class Item {
        private String caseId;
        private String query;
        private Long expectedDocumentId;
        private String expectedDocumentTitle;
        private String expectedDocumentType;
        private Long retrievalDocumentId;
        private String retrievalDocumentType;
        private Boolean expectNoAnswer;
        private Boolean passed;
        private Long topDocumentId;
        private String topTitle;
        private String topDocumentType;
        private Double topScore;
        private Integer referenceCount;
        private Boolean citationValid;
        private Boolean answerGrounded;
        private String answerExcerpt;
        private String citationWarning;
        private String failureReason;
        private String note;
        private List<KnowledgeSearchResultVO> references;
    }
}
