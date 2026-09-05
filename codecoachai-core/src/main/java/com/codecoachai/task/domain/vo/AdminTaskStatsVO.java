package com.codecoachai.task.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class AdminTaskStatsVO {

    private Long total;
    private List<StatusCountVO> statusCounts;
    private List<String> statuses;
    private String statusFilter;
    private String windowType;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private LocalDateTime generatedAt;
    private String businessTimezone;
    private String scopeDescription;
    private String navigationPath;
    private Map<String, String> navigationQuery;

    @Data
    public static class StatusCountVO {
        private String status;
        private Long count;
    }
}
