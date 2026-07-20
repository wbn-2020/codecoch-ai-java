package com.codecoachai.ai.agent.domain.dto.weekly;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AgentWeeklyReportGenerateDTO {

    private LocalDate weekStartDate;
    private Long targetJobId;

    @Size(max = 64)
    private String timezone;

    private Boolean forceRefresh;

    @Size(max = 128)
    private String requestId;

    @Size(max = 128)
    private String idempotencyKey;
}
