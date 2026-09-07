package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * JD 关键词到知识点的规则映射。
 *
 * <p>用于冷启动推荐：新用户没有能力画像、匹配报告或学习计划时，直接用 JD 文本命中关键词，
 * 再按 categoryId / groupId 从正式题库取题，不依赖 AI 生成、不依赖历史推荐批次。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_knowledge_keyword")
public class QuestionKnowledgeKeyword extends BaseEntity {

    private String keyword;

    private Long categoryId;

    private Long groupId;

    private String knowledgePoint;

    private Integer weight;

    private Integer status;
}
