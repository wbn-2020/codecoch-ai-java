package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeEvalRunVO {

    private Long id;
    private String runNo;
    private String status;
    private Integer sampleCount;
    private Integer evaluatedCount;
    private Integer passedCount;
    private Integer failedCount;
    private Double passRate;
    private Integer limit;
    private Double minScore;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private List<ResultItem> results;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class ResultItem {
        private Long id;
        private Long evalCaseId;
        private String caseId;
        private String query;
        private Long expectedDocumentId;
        private String expectedDocumentTitle;
        private String expectedDocumentType;
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
        private LocalDateTime createdAt;
    }
}
