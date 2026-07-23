package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeOptimizePayload;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.task.service.AsyncTaskService;
import com.codecoachai.task.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.RESUME,
        selectorExpression = MqTopics.RESUME_TAG_OPTIMIZE,
        consumerGroup = "codecoachai-task-resume-optimize",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class ResumeOptimizeConsumer implements RocketMQListener<MqMessage<ResumeOptimizePayload>> {

    private static final int MAX_RETRY = 3;
    private static final String BIZ_TYPE = "resume.optimize";

    private final AsyncTaskService asyncTaskService;
    private final ResumeFeignClient resumeFeignClient;
    private final NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<ResumeOptimizePayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquire(envelope, MAX_RETRY)) {
                return;
            }
            ResumeOptimizePayload payload = envelope.getPayload();
            if (payload == null || payload.getOptimizeRecordId() == null
                    || payload.getResumeId() == null || payload.getUserId() == null) {
                throw new NonRetryableMqException("resume optimize payload is invalid");
            }

            Result<ResumeOptimizeSubmitVO> response =
                    resumeFeignClient.executeResumeOptimize(payload.getOptimizeRecordId());
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException("resume optimize execute failed: "
                            + response.getMessage());
                }
                throw new RuntimeException("resume optimize execute returned invalid result: "
                        + (response == null ? "null" : response.getMessage()));
            }

            ResumeOptimizeSubmitVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getOptimizeStatus())) {
                String userReason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "resume optimize failed";
                String safeReason = safeFailureReason(result.getErrorMessage(), "resume optimize failed");
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(payload, userReason);
                log.warn("Resume optimize task failed optimizeRecordId={} reason={}",
                        payload.getOptimizeRecordId(), safeReason);
                return;
            }
            if (!"SUCCESS".equalsIgnoreCase(result.getOptimizeStatus())) {
                throw new RuntimeException("resume optimize is still processing: " + result.getOptimizeStatus());
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), BIZ_TYPE,
                    String.valueOf(payload.getOptimizeRecordId()),
                    "简历建议已生成", "你的简历建议已生成，可以回到简历页面查看。");
            log.info("Resume optimize task completed optimizeRecordId={}", payload.getOptimizeRecordId());
        } catch (TerminalTaskFailureException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "resume optimize terminal failure");
            log.warn("Resume optimize task terminal failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (NonRetryableMqException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "resume optimize non-retryable failure");
            log.error("Resume optimize task is not retryable messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markDead(envelope, safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (Exception ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "resume optimize retryable failure");
            log.error("Resume optimize task failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markFailed(envelope.getMessageId(), safeReason);
            throw new RuntimeException("resume optimize retryable failure");
        } finally {
            MDC.remove("traceId");
        }
    }

    private void notifyFailed(ResumeOptimizePayload payload, String reason) {
        if (payload == null || payload.getUserId() == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), BIZ_TYPE,
                String.valueOf(payload.getOptimizeRecordId()), "简历建议生成失败", reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private String safeFailureReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "resume optimize failure";
        if (!StringUtils.hasText(reason)) {
            return base;
        }
        return base + "; reasonLength=" + reason.length() + "; reasonHash=" + shortHash(reason);
    }

    private String shortHash(String value) {
        String hash = TextFingerprintUtils.sha256Hex(value);
        return hash == null ? null : hash.substring(0, Math.min(hash.length(), 12));
    }

    private static class TerminalTaskFailureException extends RuntimeException {
        private TerminalTaskFailureException(String message) {
            super(message);
        }
    }
}
