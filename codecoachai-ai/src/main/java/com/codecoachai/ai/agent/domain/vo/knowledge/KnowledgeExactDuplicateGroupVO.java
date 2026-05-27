package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeExactDuplicateGroupVO {
    private String chunkHash;
    private Integer duplicateCount;
    private List<KnowledgeChunkVO> chunks;
}
