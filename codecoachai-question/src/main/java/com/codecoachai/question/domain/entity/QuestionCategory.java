package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_category")
public class QuestionCategory extends BaseEntity {

    private Long parentId;
    private String categoryName;
    private Integer sort;
    private Integer sortOrder;
    private Integer status;
}
