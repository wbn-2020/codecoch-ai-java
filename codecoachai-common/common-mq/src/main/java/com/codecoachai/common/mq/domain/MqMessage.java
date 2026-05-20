package com.codecoachai.common.mq.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 MQ 消息载体（所有 Topic 都用这个包装）。
 * <p>
 * 之所以包一层，是为了：
 * 1. 携带 traceId、userId 做链路追踪
 * 2. 统一幂等键 messageId
 * 3. 业务侧只关心 payload，序列化反序列化由框架处理
 *
 * @param <T> 业务负载类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqMessage<T> {

    /** 全局唯一消息 ID（UUID），消费端做幂等键 */
    private String messageId;

    /** 链路追踪 ID */
    private String traceId;

    /** 业务类型（如 resume.parse / interview.report），用于日志和路由 */
    private String bizType;

    /** 业务 ID（如 resumeId、sessionId），主要用于幂等和分布式锁 Key */
    private String bizId;

    /** 触发用户 ID */
    private Long userId;

    /** 业务负载 */
    private T payload;

    /** 已重试次数（业务自管，框架内部不递增） */
    private Integer retryCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
