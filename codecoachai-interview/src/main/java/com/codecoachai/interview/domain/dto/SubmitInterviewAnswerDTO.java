package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitInterviewAnswerDTO {

    @NotBlank(message = "请填写面试回答")
    @Size(max = 5000, message = "面试回答不能超过 5000 字")
    private String answerContent;

    private Long questionId;
    private Integer answerDurationSeconds;
    private Boolean needFollowUp = true;
}
