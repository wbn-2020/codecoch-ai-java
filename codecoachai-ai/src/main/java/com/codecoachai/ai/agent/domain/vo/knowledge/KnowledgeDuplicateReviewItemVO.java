package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDuplicateReviewItemVO {
    private Long documentId;
    private Long chunkId;
    private String title;
    private String documentType;
    private Integer chunkIndex;
    private String sourceRef;
    private String snippet;
    private Double topScore;
    private List<KnowledgeSearchResultVO> matches;
}
