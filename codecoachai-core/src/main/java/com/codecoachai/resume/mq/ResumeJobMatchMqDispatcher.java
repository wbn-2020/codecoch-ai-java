package com.codecoachai.resume.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeJobMatchPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ResumeJobMatchMqDispatcher {

    private final MqProducer mqProducer;

    public ResumeJobMatchMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    public boolean dispatchAnalyze(Long reportId, Long userId) {
        return dispatchAnalyzeWithReceipt(reportId, userId) != null;
    }

    public MqDispatchReceipt dispatchAnalyzeWithReceipt(Long reportId, Long userId) {
        return dispatchAnalyzeWithReceipt(reportId, userId, null, null);
    }

    /**
     * Dispatches with the pre-registered async-task correlation identifiers when supplied.
     * The task row and broker envelope must share the same message ID so consumers can claim
     * and complete the task that users see in the task center.
     */
    public MqDispatchReceipt dispatchAnalyzeWithReceipt(
            Long reportId, Long userId, String messageId, String traceId) {
        if (reportId == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip resume job match dispatch reportId={}", reportId);
            return null;
        }
        try {
            String resolvedMessageId = StringUtils.hasText(messageId)
                    ? messageId
                    : java.util.UUID.randomUUID().toString().replace("-", "");
            String resolvedTraceId = StringUtils.hasText(traceId)
                    ? traceId
                    : StringUtils.hasText(MDC.get("traceId"))
                    ? MDC.get("traceId")
                    : resolvedMessageId;
            ResumeJobMatchPayload payload = ResumeJobMatchPayload.builder()
                    .reportId(reportId)
                    .userId(userId)
                    .build();
            String destination = MqTopics.dest(MqTopics.JOB_MATCH, MqTopics.JOB_MATCH_TAG_ANALYZE);
            MqMessage<ResumeJobMatchPayload> envelope = MqMessage.<ResumeJobMatchPayload>builder()
                    .messageId(resolvedMessageId)
                    .traceId(resolvedTraceId)
                    .bizType("resume-job-match.analyze")
                    .bizId(String.valueOf(reportId))
                    .userId(userId)
                    .payload(payload)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            SendResult result = mqProducer.sendEnvelopeSync(destination, envelope);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                log.error("Resume job match MQ dispatch was not accepted reportId={} sendStatus={}",
                        reportId, result == null ? null : result.getSendStatus());
                return null;
            }
            log.info("Dispatched resume job match report task reportId={}", reportId);
            return MqDispatchReceipt.builder()
                    .messageId(resolvedMessageId)
                    .traceId(resolvedTraceId)
                    .bizType(envelope.getBizType())
                    .bizId(envelope.getBizId())
                    .userId(userId)
                    .destination(destination)
                    .sendStatus(result.getSendStatus().name())
                    .createdAt(envelope.getCreatedAt())
                    .build();
        } catch (Exception ex) {
            log.error("Dispatch resume job match report task failed reportId={}", reportId, ex);
            return null;
        }
    }
}
