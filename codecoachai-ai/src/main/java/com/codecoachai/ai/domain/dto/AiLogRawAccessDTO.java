package com.codecoachai.ai.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiLogRawAccessDTO {

    @NotBlank(message = "请填写本次查看 AI 原文的访问原因")
    @Size(max = 300, message = "访问原因不能超过 300 个字符")
    private String accessReason;

    @AssertTrue(message = "请先确认本次敏感原文访问")
    private boolean confirmSensitiveAccess;

    private Boolean dryRun;

    @NotBlank(message = "请提供本次敏感原文访问的幂等键")
    @Size(min = 8, max = 128, message = "幂等键长度必须为 8-128 个字符")
    private String idempotencyKey;
}
