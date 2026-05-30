package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDuplicateReviewVO {
    private Boolean vectorEnabled;
    private Double threshold;
    private Integer scannedChunkCount;
    private Integer candidateCount;
    private Integer limit;
    private List<KnowledgeDuplicateReviewItemVO> items;
    private LocalDateTime generatedAt;
}
