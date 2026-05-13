package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class CurrentQuestionVO {

    private Long questionId;
    private Long questionGroupId;
    private String questionText;
}
