package com.codecoachai.common.mq.payload;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JobCoach 今日计划生成任务负载。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDailyPlanPayload {

    private Long runId;
    private Long userId;
    private String executionId;
    private String parentExecutionId;
    private String idempotencyKey;
    private Integer attemptNo;
    private String executionToken;
    private Long targetJobId;
    private LocalDate date;
    private Integer maxTotalMinutes;
    private Integer taskCount;
    private Boolean forceRegenerate;
}
