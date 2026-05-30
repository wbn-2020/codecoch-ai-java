package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeConfigVO {
    private Boolean vectorEnabled;
    private String vectorCollection;
    private String retrievalMode;
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer minChunkSize;
    private Double nearDuplicateThreshold;
    private Double askMinScore;
    private Long uploadMaxBytes;
    private Integer uploadMaxTextChars;
    private List<String> uploadExtensions;
    private String exactDedupScope;
    private String nearDuplicateAction;
}
