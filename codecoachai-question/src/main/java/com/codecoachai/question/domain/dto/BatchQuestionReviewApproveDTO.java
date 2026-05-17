package com.codecoachai.question.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class BatchQuestionReviewApproveDTO {

    @NotEmpty(message = "reviewIds is required")
    @Size(max = 100, message = "reviewIds must be at most 100")
    private List<Long> reviewIds;

    @Valid
    private QuestionReviewApproveDTO approveData;
}
