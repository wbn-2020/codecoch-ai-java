package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeAskVO {
    private String question;
    private String answer;
    private List<KnowledgeSearchResultVO> references;
    private Long aiCallLogId;
    private LocalDateTime generatedAt;
}
