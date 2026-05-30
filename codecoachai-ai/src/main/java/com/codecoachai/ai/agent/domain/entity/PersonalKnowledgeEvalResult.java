package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("personal_knowledge_eval_result")
public class PersonalKnowledgeEvalResult extends BaseEntity {

    private Long userId;
    private Long runId;
    private Long evalCaseId;
    private String caseId;
    private String queryText;
    private Long expectedDocumentId;
    private String expectedDocumentTitle;
    private String expectedDocumentType;
    private Long retrievalDocumentId;
    private String retrievalDocumentType;
    private Integer expectNoAnswer;
    private Integer passed;
    private Long topDocumentId;
    private String topTitle;
    private String topDocumentType;
    private Double topScore;
    private Integer referenceCount;
    private Integer citationValid;
    private Integer answerGrounded;
    private String answerExcerpt;
    private String citationWarning;
    private String failureReason;
    private String note;
    private String referencesJson;
}
