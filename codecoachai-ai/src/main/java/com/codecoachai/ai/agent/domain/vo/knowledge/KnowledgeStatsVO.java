package com.codecoachai.ai.agent.domain.vo.knowledge;

import lombok.Data;

@Data
public class KnowledgeStatsVO {
    private Integer documentCount;
    private Integer chunkCount;
    private Integer duplicateChunkCount;
    private Boolean vectorEnabled;
    private String retrievalMode;
    private String chunkStrategy;
}
