package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentPlanChangePreviewDTO {

    @Size(max = 128, message = "请求标识不能超过 128 个字符")
    private String requestId;

    @NotBlank(message = "幂等键不能为空")
    @Size(min = 8, max = 128, message = "幂等键长度必须在 8 到 128 个字符之间")
    private String idempotencyKey;

    @NotNull(message = "复盘版本不能为空")
    private Integer expectedReviewVersion;

    @NotEmpty(message = "至少需要选择一项已采纳建议")
    private List<Long> acceptedSuggestionIds = new ArrayList<>();

    @NotNull(message = "目标日期不能为空")
    private LocalDate targetDate;

    @Min(value = 15, message = "时间预算不能少于 15 分钟")
    @Max(value = 480, message = "时间预算不能超过 480 分钟")
    private Integer maxTotalMinutes = 120;
}
