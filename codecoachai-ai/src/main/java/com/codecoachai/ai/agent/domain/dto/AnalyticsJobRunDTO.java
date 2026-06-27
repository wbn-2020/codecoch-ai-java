package com.codecoachai.ai.agent.domain.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AnalyticsJobRunDTO {
    private String jobCode;
    private String jobName;
    private LocalDate statDate;
    private List<Long> userIds = new ArrayList<>();
    private Long targetJobId;
    private Integer taskCount;
    private Integer maxTotalMinutes;
    private Integer userLimit;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
