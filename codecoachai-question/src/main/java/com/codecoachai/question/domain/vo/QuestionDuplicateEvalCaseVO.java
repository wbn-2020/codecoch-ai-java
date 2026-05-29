package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionDuplicateEvalCaseVO {

    private Long id;
    private String caseId;
    private Long sourceQuestionId;
    private Long targetQuestionId;
    private String sourceTitle;
    private String targetTitle;
    private String expected;
    private String note;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
