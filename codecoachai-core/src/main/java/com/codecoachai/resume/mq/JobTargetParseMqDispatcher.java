package com.codecoachai.resume.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.JobTargetParsePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.resume.domain.vo.JobDescriptionAnalysisVO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.service.AsyncTaskService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class JobTargetParseMqDispatcher {

    public static final String BIZ_TYPE = "job-target.parse";
    private static final int MAX_RETRY = 3;

    private final MqProducer mqProducer;
    private final AsyncTaskService asyncTaskService;

    public JobTargetParseMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider,
                                      AsyncTaskService asyncTaskService) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
        this.asyncTaskService = asyncTaskService;
    }

    public MqDispatchReceipt dispatchParseWithReceipt(Long targetJobId, Long userId,
                                                      Boolean forceRefresh, String userTargetDirection) {
        if (targetJobId == null) {
            return null;
        }
        String executionId = UUID.randomUUID().toString().replace("-", "");
        String messageId = executionId;
        String traceId = StringUtils.hasText(MDC.get("traceId")) ? MDC.get("traceId") : executionId;
        JobTargetParsePayload payload = JobTargetParsePayload.builder()
                .targetJobId(targetJobId)
                .userId(userId)
                .forceRefresh(forceRefresh)
                .userTargetDirection(userTargetDirection)
                .build();
        asyncTaskService.registerPending(
                messageId,
                BIZ_TYPE,
                String.valueOf(targetJobId),
                userId,
                traceId,
                executionId,
                payload,
                MAX_RETRY);
        String destination = MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_JOB_TARGET_PARSE);
        MqDispatchReceipt fallbackReceipt = MqDispatchReceipt.builder()
                .messageId(messageId)
                .traceId(traceId)
                .bizType(BIZ_TYPE)
                .bizId(String.valueOf(targetJobId))
                .userId(userId)
                .destination(destination)
                .sendStatus("FALLBACK_REQUIRED")
                .createdAt(LocalDateTime.now())
                .build();
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, execute job target parse synchronously targetJobId={}", targetJobId);
            return fallbackReceipt;
        }
        try {
            MqMessage<JobTargetParsePayload> envelope = MqMessage.<JobTargetParsePayload>builder()
                    .messageId(messageId)
                    .traceId(traceId)
                    .bizType(BIZ_TYPE)
                    .bizId(String.valueOf(targetJobId))
                    .userId(userId)
                    .payload(payload)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            SendResult result = mqProducer.sendEnvelopeSync(destination, envelope);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("Job target parse MQ dispatch was not accepted");
            }
            log.info("Dispatched job target parse task targetJobId={} executionId={}",
                    targetJobId, executionId);
            return MqDispatchReceipt.builder()
                    .messageId(messageId)
                    .traceId(traceId)
                    .bizType(BIZ_TYPE)
                    .bizId(String.valueOf(targetJobId))
                    .userId(userId)
                    .destination(destination)
                    .sendStatus(result.getSendStatus().name())
                    .createdAt(envelope.getCreatedAt())
                    .build();
        } catch (Exception ex) {
            log.warn("Dispatch job target parse task failed; execute synchronously targetJobId={} executionId={}",
                    targetJobId, executionId, ex);
            return fallbackReceipt;
        }
    }

    public void completeSynchronousFallback(
            MqDispatchReceipt receipt, boolean successful, Object result, String failureReason) {
        if (receipt == null || !StringUtils.hasText(receipt.getMessageId())) {
            return;
        }
        asyncTaskService.completePending(
                receipt.getMessageId(), successful, result, failureReason);
    }

    public JobDescriptionAnalysisVO attachLatestTaskReceipt(JobDescriptionAnalysisVO vo,
                                                            Long targetJobId,
                                                            Long userId) {
        if (vo == null || targetJobId == null || userId == null) {
            return vo;
        }
        AsyncTask task = asyncTaskService.findLatestForUser(BIZ_TYPE, String.valueOf(targetJobId), userId);
        if (task == null) {
            return vo;
        }
        vo.setExecutionId(task.getExecutionId());
        vo.setAsyncMessageId(task.getMessageId());
        vo.setAsyncTraceId(task.getTraceId());
        vo.setAsyncBizType(task.getBizType());
        vo.setAsyncBizId(task.getBizId());
        vo.setAsyncSendStatus(task.getStatus());
        return vo;
    }
}
