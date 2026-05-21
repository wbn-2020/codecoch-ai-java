package com.codecoachai.ai.agent.domain.vo.ops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AnalyticsJobLogVO {
    private Long id;
    private String jobCode;
    private String jobName;
    private String status;
    private LocalDate statDate;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String errorMessage;
    private String outputJson;
    private LocalDateTime createdAt;
}
