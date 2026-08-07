package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionDuplicateIgnoreDTO {

    private String ignoredReason;
    private Boolean confirm;
    private Boolean dryRun;
    private String idempotencyKey;
}
