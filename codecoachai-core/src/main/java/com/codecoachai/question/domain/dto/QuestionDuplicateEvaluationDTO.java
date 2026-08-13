package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateEvaluationDTO {

    private List<Sample> samples;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;

    @Data
    public static class Sample {
        private String caseId;
        private Long sourceQuestionId;
        private Long targetQuestionId;
        /** DUPLICATE / REVIEW / NOT_DUPLICATE */
        private String expected;
        private String note;
    }
}
