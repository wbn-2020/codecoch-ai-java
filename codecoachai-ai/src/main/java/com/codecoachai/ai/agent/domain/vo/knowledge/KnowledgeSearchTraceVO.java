package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeSearchTraceVO {
    private String query;
    private List<String> expandedTerms;
    private Integer limit;
    private Integer recallLimit;
    private Double minScore;
    private Long documentId;
    private String documentType;
    private Boolean vectorEnabled;
    private String retrievalMode;
    private Integer vectorCandidateCount;
    private Integer keywordCandidateCount;
    private Integer mergedCandidateCount;
    private Integer filteredCandidateCount;
    private Integer finalCandidateCount;
    private List<KnowledgeSearchResultVO> vectorCandidates;
    private List<KnowledgeSearchResultVO> keywordCandidates;
    private List<KnowledgeSearchResultVO> mergedCandidates;
    private List<KnowledgeSearchResultVO> finalResults;
    private List<String> warnings;
    private LocalDateTime generatedAt;
}
