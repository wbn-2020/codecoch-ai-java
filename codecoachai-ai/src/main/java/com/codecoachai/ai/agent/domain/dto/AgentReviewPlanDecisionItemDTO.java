package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentReviewPlanDecisionItemDTO {

    @NotNull(message = "建议 ID 不能为空")
    private Long suggestionId;

    @NotNull(message = "建议决策不能为空")
    private String decision;

    @NotNull(message = "建议决策版本不能为空")
    private Integer expectedDecisionVersion;

    @Size(max = 500, message = "忽略原因不能超过 500 个字符")
    private String reason;
}
