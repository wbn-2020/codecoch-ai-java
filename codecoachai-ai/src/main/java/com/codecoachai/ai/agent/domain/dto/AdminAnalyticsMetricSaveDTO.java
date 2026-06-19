package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class AdminAnalyticsMetricSaveDTO {
    private Long id;
    private String metricCode;
    private String metricName;
    private String category;
    private String definition;
    private String dataSource;
    private String refreshFrequency;
    private Integer enabled;
    private Boolean confirm;
    private Boolean dryRun;
    private String reason;
    private String idempotencyKey;
}
