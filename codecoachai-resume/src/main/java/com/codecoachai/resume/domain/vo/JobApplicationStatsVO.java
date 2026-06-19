package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class JobApplicationStatsVO {
    private Long total = 0L;
    private Long activeCount = 0L;
    private Long overdueFollowUpCount = 0L;
    private Long dueTodayFollowUpCount = 0L;
    private Long noFollowUpCount = 0L;
    private Long staleActiveCount = 0L;
    private Long interviewCount = 0L;
    private Long offerCount = 0L;
    private Long rejectedCount = 0L;
    private Long closedCount = 0L;
    private Map<String, Long> statusCounts = new LinkedHashMap<>();
    private LocalDateTime generatedAt;
}
