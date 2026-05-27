package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDuplicateCleanupVO {
    private Boolean dryRun;
    private Integer duplicateGroupCount;
    private Integer deleteCandidateCount;
    private Integer deletedCount;
    private List<Long> deletedChunkIds;
    private LocalDateTime generatedAt;
}
