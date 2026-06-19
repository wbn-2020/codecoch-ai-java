package com.codecoachai.question.domain.vo;

import lombok.Data;

@Data
public class SubmitQuestionAnswerVO {

    private Long recordId;
    private Long questionId;
    private String referenceAnswer;
    private String analysis;
    private String masteryStatus;
    private Boolean wrong;
    private Boolean agentTaskCompleted;
    private Long agentTaskId;
    private String agentTaskTitle;
    private String agentTaskStatus;
    private String agentReviewSummary;
}
