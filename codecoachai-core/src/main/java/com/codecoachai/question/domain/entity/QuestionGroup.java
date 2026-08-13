package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_group")
public class QuestionGroup extends BaseEntity {

    private String groupName;
    private String canonicalTitle;
    private String canonicalAnswer;
    private String mainKnowledgePoint;
    private String difficulty;
    private String description;
    private Long categoryId;
    private Integer status;
}
