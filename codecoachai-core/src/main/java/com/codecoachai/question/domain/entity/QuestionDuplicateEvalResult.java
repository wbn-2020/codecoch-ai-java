package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_duplicate_eval_result")
public class QuestionDuplicateEvalResult extends BaseEntity {

    private Long runId;
    private Long evalCaseId;
    private String caseId;
    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String expected;
    private String predicted;
    private Integer passed;
    private String matchType;
    private BigDecimal score;
    private String scoreBand;
    private String scorePartsJson;
    private String reason;
    private String note;
}
