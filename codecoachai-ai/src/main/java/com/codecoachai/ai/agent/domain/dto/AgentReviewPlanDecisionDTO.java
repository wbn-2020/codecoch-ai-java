package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentReviewPlanDecisionDTO {

    @Size(max = 128, message = "请求标识不能超过 128 个字符")
    private String requestId;

    @NotBlank(message = "幂等键不能为空")
    @Size(min = 8, max = 128, message = "幂等键长度必须在 8 到 128 个字符之间")
    private String idempotencyKey;

    @NotNull(message = "复盘版本不能为空")
    private Integer expectedReviewVersion;

    @Valid
    @NotEmpty(message = "至少需要提交一项建议决策")
    private List<AgentReviewPlanDecisionItemDTO> decisions = new ArrayList<>();
}
