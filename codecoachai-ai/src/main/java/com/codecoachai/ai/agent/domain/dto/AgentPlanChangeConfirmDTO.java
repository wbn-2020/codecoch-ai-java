package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentPlanChangeConfirmDTO {

    @Size(max = 128, message = "请求标识不能超过 128 个字符")
    private String requestId;

    @NotBlank(message = "幂等键不能为空")
    @Size(min = 8, max = 128, message = "幂等键长度必须在 8 到 128 个字符之间")
    private String idempotencyKey;

    @NotNull(message = "预览版本不能为空")
    private Integer previewVersion;

    @NotBlank(message = "预览哈希不能为空")
    private String previewHash;

    private List<String> acknowledgedWarningCodes = new ArrayList<>();
}
