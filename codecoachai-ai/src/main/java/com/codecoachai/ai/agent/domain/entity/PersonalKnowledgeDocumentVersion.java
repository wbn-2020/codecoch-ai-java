package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("personal_knowledge_document_version")
public class PersonalKnowledgeDocumentVersion extends BaseEntity {
    private Long userId;
    private Long documentId;
    private Integer versionNo;
    private String title;
    private String documentType;
    private String content;
    private String contentHash;
    private String normalizationVersion;
    private Integer chunkCount;
}
