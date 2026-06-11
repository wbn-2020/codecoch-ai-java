package com.codecoachai.question.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class BatchQuestionReviewApproveDTO {

    @NotEmpty(message = "请选择待审核题目")
    @Size(max = 100, message = "单次最多处理 100 道待审核题目")
    private List<Long> reviewIds;

    @Valid
    private QuestionReviewApproveDTO approveData;
}
