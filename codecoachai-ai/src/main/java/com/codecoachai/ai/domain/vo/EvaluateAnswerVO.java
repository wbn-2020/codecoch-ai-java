package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class EvaluateAnswerVO {

    private Integer score;
    private String comment;
    private String nextAction;
    private String knowledgePoints;
}
