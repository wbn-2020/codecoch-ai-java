package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class CurrentQuestionVO {

    private Long sessionId;
    private Long stageId;
    private String stageName;
    private Long messageId;
    private Long questionId;
    private Long questionGroupId;
    private String questionContent;
    private String questionText;
    private Boolean isFollowUp;
    private Long parentMessageId;
    private Integer followUpCount;
    private String stageProgress;
    private String interviewStatus;
}
