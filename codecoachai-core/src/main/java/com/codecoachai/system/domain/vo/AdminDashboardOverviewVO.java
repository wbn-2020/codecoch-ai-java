package com.codecoachai.system.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AdminDashboardOverviewVO {

    private List<SummaryCardVO> summaryCards;
    private List<TrendStatVO> trendStats;
    private List<PendingItemVO> pendingItems;
    private SystemStatusVO systemStatus;
    private String dataSourceDesc;
    private LocalDateTime generatedAt;

    @Data
    public static class SummaryCardVO {
        private String key;
        private String label;
        private Long value;
        private String sourceTable;
    }

    @Data
    public static class TrendStatVO {
        private LocalDate date;
        private Long interviewCount;
        private Long resumeUploadCount;
        private Long aiCallCount;
        private Long aiCallFailedCount;
        private Long studyPlanGeneratedCount;
        private Long questionReviewGeneratedCount;
    }

    @Data
    public static class PendingItemVO {
        private String key;
        private String label;
        private Long count;
        private String status;
        private String sourceTable;
        private String reason;
    }

    @Data
    public static class SystemStatusVO {
        private String status;
        private List<ServiceStatusVO> services;
        private OpsMetricsVO opsMetrics;
        private LocalDateTime generatedAt;
    }

    @Data
    public static class ServiceStatusVO {
        private String serviceName;
        private String status;
        private String reason;
        private String source;
    }

    @Data
    public static class OpsMetricsVO {
        private Double qps;
        private Double tps;
        private Long rpm;
        private Long tpm;
        private Double processCpuUsage;
        private Double systemCpuUsage;
        private Long heapUsedMb;
        private Long heapMaxMb;
        private Double heapUsage;
        private Double redisHitRate;
        private Long redisKeyspaceHits;
        private Long redisKeyspaceMisses;
        private Integer redisConnectedClients;
        private String metricsSource;
    }
}
