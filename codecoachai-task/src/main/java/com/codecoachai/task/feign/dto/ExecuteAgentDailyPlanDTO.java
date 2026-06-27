package com.codecoachai.task.feign.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ExecuteAgentDailyPlanDTO {

    private Long userId;
    private String executionToken;
    private Long targetJobId;
    private LocalDate date;
    private Integer maxTotalMinutes;
    private Integer taskCount;
    private Boolean forceRegenerate;
}
