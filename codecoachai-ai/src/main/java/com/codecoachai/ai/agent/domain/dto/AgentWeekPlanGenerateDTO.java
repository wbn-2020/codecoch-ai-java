package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AgentWeekPlanGenerateDTO {
    private Long targetJobId;
    private LocalDate date;
    private Boolean forceRegenerate;
    private String requestId;
    private String idempotencyKey;
}
