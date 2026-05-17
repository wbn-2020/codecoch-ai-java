package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class EvaluateAnswerVO {

    private Long aiCallLogId;
    private Integer score;
    private String comment;
    private String nextAction;
    private String knowledgePoints;
    private String followUpQuestion;
    private String followUpReason;
    private Boolean followUpValid;
}
