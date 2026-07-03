package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class CurrentInterviewVO {

    private Long id;
    private Long applicationId;
    private Long targetJobId;
    private String status;
    private String reportStatus;
    private Integer currentQuestionIndex;
    private Integer totalQuestionCount;
    private Integer answeredQuestionCount;
    private String overallProgress;
    private InterviewStageVO currentStage;
    private CurrentQuestionVO currentQuestion;
}
