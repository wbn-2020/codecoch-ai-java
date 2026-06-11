package com.codecoachai.common.mq.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * MQ 投递回执，用于把业务提交结果和后续异步任务诊断串起来。
 */
@Data
@Builder
public class MqDispatchReceipt {

    private String messageId;
    private String traceId;
    private String bizType;
    private String bizId;
    private Long userId;
    private String destination;
    private String sendStatus;
    private LocalDateTime createdAt;
}
