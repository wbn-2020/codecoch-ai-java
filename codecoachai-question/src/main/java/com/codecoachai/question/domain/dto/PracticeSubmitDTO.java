package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PracticeSubmitDTO {

    @NotBlank(message = "answerContent is required")
    private String answerContent;
}
