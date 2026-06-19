package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QuestionReviewRejectDTO {

    @NotBlank(message = "请填写驳回原因")
    @Size(max = 500, message = "驳回原因不能超过 500 字")
    private String rejectReason;
    private Boolean confirm;
    private Boolean dryRun;
    private String idempotencyKey;
}
