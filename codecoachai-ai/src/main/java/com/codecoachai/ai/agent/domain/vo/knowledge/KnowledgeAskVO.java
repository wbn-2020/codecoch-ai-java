package com.codecoachai.ai.agent.domain.vo.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeAskVO {
    private String question;
    private String answer;
    private List<KnowledgeSearchResultVO> references;
    private Integer referenceCount;
    private Double topReferenceScore;
    private Boolean insufficientReferences;
    private Boolean answerGrounded;
    private Boolean citationValid;
    private String citationWarning;
    private List<Integer> citedReferenceNumbers;
    private List<Integer> invalidReferenceNumbers;
    /** 疑似未被任何引用片段支撑的句子（grounding 校验）。 */
    private List<String> unsupportedSentences;
    private Double minReferenceScore;
    private Long aiCallLogId;
    private LocalDateTime generatedAt;
}
