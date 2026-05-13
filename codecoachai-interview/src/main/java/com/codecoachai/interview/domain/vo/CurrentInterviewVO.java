package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class CurrentInterviewVO {

    private Long id;
    private String status;
    private String reportStatus;
    private InterviewStageVO currentStage;
    private CurrentQuestionVO currentQuestion;
}
