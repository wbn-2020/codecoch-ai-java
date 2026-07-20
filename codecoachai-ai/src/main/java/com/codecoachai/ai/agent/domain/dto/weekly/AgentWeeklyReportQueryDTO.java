package com.codecoachai.ai.agent.domain.dto.weekly;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AgentWeeklyReportQueryDTO {

    private LocalDate weekStartDate;
    private LocalDate fromWeekStart;
    private LocalDate toWeekStart;
    private Long targetJobId;

    @Size(max = 64)
    private String timezone;

    @Min(1)
    @Max(50)
    private Integer limit;
}
