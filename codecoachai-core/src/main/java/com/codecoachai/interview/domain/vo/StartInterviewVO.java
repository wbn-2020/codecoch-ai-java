package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class StartInterviewVO {

    private Long id;
    private String status;
    private InterviewStageVO currentStage;
    private CurrentQuestionVO currentQuestion;
}
