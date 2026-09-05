package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveQuestionTagDTO {

    @NotBlank(message = "请填写标签名称")
    private String tagName;

    private String keyword;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private Integer status;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
