package com.codecoachai.ai.agent.domain.dto.weekly;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentWeeklyReportRefreshDTO {

    @Size(max = 128)
    private String requestId;

    @Size(max = 128)
    private String idempotencyKey;
}
