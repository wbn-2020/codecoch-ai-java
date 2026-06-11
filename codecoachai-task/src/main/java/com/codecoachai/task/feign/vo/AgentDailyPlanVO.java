package com.codecoachai.task.feign.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentDailyPlanVO {

    private Long runId;
    private Long targetJobId;
    private LocalDate date;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
