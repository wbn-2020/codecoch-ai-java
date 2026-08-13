package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_relation")
public class QuestionRelation extends BaseEntity {

    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String relationType;
    private String relationStatus;
    private String reason;
    private BigDecimal similarityScore;
    private Long createdBy;
}
