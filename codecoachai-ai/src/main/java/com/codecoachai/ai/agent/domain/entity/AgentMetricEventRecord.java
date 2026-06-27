package com.codecoachai.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_metric_event_record")
public class AgentMetricEventRecord extends BaseEntity {

    private String eventId;
    private String eventCode;
    private String idempotencyKey;
    private Long userId;
    private Long taskId;
    private Long runId;
    private LocalDate planDate;
    private Long targetJobId;
    private String requestId;
    private String sourcePage;
    private String targetPath;
    private String notificationId;
    private String bizType;
    private String bizId;
    private LocalDateTime occurredAt;
    private LocalDateTime acceptedAt;
    private String ingestSource;
    private String metadataJson;
}
