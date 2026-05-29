package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("personal_knowledge_eval_case")
public class PersonalKnowledgeEvalCase extends BaseEntity {

    private Long userId;
    private String caseId;
    private String queryText;
    private Long expectedDocumentId;
    private String expectedDocumentTitle;
    private String expectedDocumentType;
    private Long retrievalDocumentId;
    private String retrievalDocumentType;
    private Integer expectNoAnswer;
    private String note;
    private Integer enabled;
}
