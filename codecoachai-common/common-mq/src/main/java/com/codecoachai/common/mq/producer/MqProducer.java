package com.codecoachai.common.mq.producer;

import com.codecoachai.common.mq.domain.MqMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一 MQ 生产者：
 * <ul>
 *   <li>自动包装 MqMessage（生成 messageId、traceId、createdAt）</li>
 *   <li>自动注入 KEYS Header（messageId）便于 RocketMQ 控制台查询</li>
 *   <li>同步发送（核心业务）/ 异步发送（次要业务）两种接口</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发送（用于关键链路：报告生成、ES 同步等）。
     *
     * @param destination topic 或 topic:tag
     * @param bizType     业务类型字符串，用于日志
     * @param bizId       业务 ID
     * @param userId      用户 ID（可空）
     * @param payload     业务负载对象
     * @return SendResult
     */
    public <T> SendResult sendSync(String destination, String bizType, String bizId, Long userId, T payload) {
        MqMessage<T> envelope = buildEnvelope(bizType, bizId, userId, payload);
        Message<MqMessage<T>> msg = MessageBuilder.withPayload(envelope)
                .setHeader("KEYS", envelope.getMessageId())
                .build();
        SendResult result = rocketMQTemplate.syncSend(destination, msg);
        log.info("MQ sync sent dest={} bizType={} msgId={} status={}",
                destination, bizType, envelope.getMessageId(), result.getSendStatus());
        return result;
    }

    /**
     * 异步发送（用于次要业务：通知、操作日志等）。
     */
    public <T> void sendAsync(String destination, String bizType, String bizId, Long userId, T payload) {
        MqMessage<T> envelope = buildEnvelope(bizType, bizId, userId, payload);
        Message<MqMessage<T>> msg = MessageBuilder.withPayload(envelope)
                .setHeader("KEYS", envelope.getMessageId())
                .build();
        rocketMQTemplate.asyncSend(destination, msg, new org.apache.rocketmq.client.producer.SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("MQ async ok dest={} msgId={}", destination, envelope.getMessageId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("MQ async fail dest={} msgId={} err={}", destination, envelope.getMessageId(), e.getMessage());
            }
        });
    }

    /**
     * 延迟消息（RocketMQ 5.x 支持 1s-1d 任意延迟，4.x 仅支持 18 个固定 level）。
     * delayLevel 1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m, 9=5m, 10=6m, 11=7m, 12=8m, 13=9m, 14=10m,
     *            15=20m, 16=30m, 17=1h, 18=2h
     */
    public <T> SendResult sendDelay(String destination, String bizType, String bizId, Long userId,
                                    T payload, int delayLevel) {
        MqMessage<T> envelope = buildEnvelope(bizType, bizId, userId, payload);
        Message<MqMessage<T>> msg = MessageBuilder.withPayload(envelope)
                .setHeader("KEYS", envelope.getMessageId())
                .build();
        SendResult result = rocketMQTemplate.syncSend(destination, msg, 3000L, delayLevel);
        log.info("MQ delay sent dest={} bizType={} msgId={} delayLevel={}",
                destination, bizType, envelope.getMessageId(), delayLevel);
        return result;
    }

    private <T> MqMessage<T> buildEnvelope(String bizType, String bizId, Long userId, T payload) {
        String traceId = MDC.get("traceId");
        return MqMessage.<T>builder()
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .traceId(StringUtils.hasText(traceId) ? traceId : null)
                .bizType(bizType)
                .bizId(bizId)
                .userId(userId)
                .payload(payload)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
