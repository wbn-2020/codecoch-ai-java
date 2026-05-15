package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionReviewRejectDTO {

    @NotBlank(message = "rejectReason is required")
    @Size(max = 500, message = "rejectReason must be at most 500 characters")
    private String rejectReason;
}
