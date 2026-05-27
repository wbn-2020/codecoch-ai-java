package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeVectorRebuildVO {
    private Boolean vectorEnabled;
    private Integer documentCount;
    private Integer chunkCount;
    private Integer vectorUpdated;
    private List<Long> failedDocuments;
    private List<String> errors;
}
