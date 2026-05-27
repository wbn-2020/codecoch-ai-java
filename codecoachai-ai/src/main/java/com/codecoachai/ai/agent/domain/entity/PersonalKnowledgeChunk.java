package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("personal_knowledge_chunk")
public class PersonalKnowledgeChunk extends BaseEntity {
    private Long userId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String chunkHash;
    private String sourceRef;
}
