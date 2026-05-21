package com.codecoachai.ai.agent.domain.vo.ops;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AnalyticsMetricDefinitionVO {
    private Long id;
    private String metricCode;
    private String metricName;
    private String category;
    private String definition;
    private String dataSource;
    private String refreshFrequency;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
