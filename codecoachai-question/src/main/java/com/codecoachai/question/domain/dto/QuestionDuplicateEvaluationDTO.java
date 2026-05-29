package com.codecoachai.question.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class QuestionDuplicateEvaluationDTO {

    private List<Sample> samples;

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
