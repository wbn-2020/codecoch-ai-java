package com.codecoachai.ai.agent.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.Data;

@Data
public class DailyPlanGenerateDTO {

    private Long targetJobId;
    private LocalDate date;
    @Min(15)
    @Max(480)
    private Integer maxTotalMinutes;
    @Min(1)
    @Max(5)
    private Integer taskCount;
    private Boolean forceRegenerate;
}
