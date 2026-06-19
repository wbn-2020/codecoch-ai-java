package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionDuplicateEvalCaseSaveDTO {

    private Long id;
    private String caseId;
    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String expected;
    private String note;
    private Integer enabled;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
