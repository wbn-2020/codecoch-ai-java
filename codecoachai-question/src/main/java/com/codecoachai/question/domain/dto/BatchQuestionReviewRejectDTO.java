package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class BatchQuestionReviewRejectDTO {

    @NotEmpty(message = "请选择待审核题目")
    @Size(max = 100, message = "单次最多处理 100 道待审核题目")
    private List<Long> reviewIds;

    @NotBlank(message = "请填写驳回原因")
    @Size(max = 500, message = "驳回原因不能超过 500 字")
    private String rejectReason;
}
