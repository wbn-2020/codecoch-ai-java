package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_duplicate_eval_case")
public class QuestionDuplicateEvalCase extends BaseEntity {

    private String caseId;
    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String expected;
    private String note;
    private Integer enabled;
    private Long createdBy;
}
