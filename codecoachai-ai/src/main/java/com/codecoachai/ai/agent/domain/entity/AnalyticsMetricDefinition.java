package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analytics_metric_definition")
public class AnalyticsMetricDefinition extends BaseEntity {
    private String metricCode;
    private String metricName;
    private String category;
    private String definition;
    private String dataSource;
    private String refreshFrequency;
    private Integer enabled;
}
