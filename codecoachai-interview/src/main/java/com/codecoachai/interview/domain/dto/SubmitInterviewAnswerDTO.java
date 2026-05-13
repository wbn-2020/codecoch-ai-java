package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitInterviewAnswerDTO {

    @NotBlank(message = "answerContent is required")
    private String answerContent;
}
