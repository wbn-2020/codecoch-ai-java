package com.codecoachai.ai.agent.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AgentMetricAckVO {

    private String eventId;
    private String eventCode;
    private String idempotencyKey;
    private Boolean duplicate;
    private LocalDateTime acceptedAt;
}
