package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class BatchQuestionReviewRejectDTO {

    @NotEmpty(message = "reviewIds is required")
    @Size(max = 100, message = "reviewIds must be at most 100")
    private List<Long> reviewIds;

    @NotBlank(message = "rejectReason is required")
    @Size(max = 500, message = "rejectReason must be at most 500 characters")
    private String rejectReason;
}
