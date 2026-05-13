package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question")
public class Question extends BaseEntity {

    private String title;
    private String content;
    private String referenceAnswer;
    private String analysis;
    private Long categoryId;
    private Long groupId;
    private String difficulty;
    private Integer status;
}
