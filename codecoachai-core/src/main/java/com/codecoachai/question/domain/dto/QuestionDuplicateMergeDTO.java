package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionDuplicateMergeDTO {

    private String relationType;
    private String reason;
    private Boolean confirm;
    private Boolean dryRun;
    private String idempotencyKey;
}
