package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class SubmitInterviewAnswerVO {

    private Integer score;
    private String comment;
    private String nextAction;
    private CurrentQuestionVO nextQuestion;
}
