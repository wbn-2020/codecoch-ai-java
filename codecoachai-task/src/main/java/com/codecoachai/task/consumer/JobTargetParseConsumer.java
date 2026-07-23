package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.JobTargetParsePayload;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.dto.JobDescriptionParseDTO;
import com.codecoachai.task.feign.vo.JobDescriptionAnalysisVO;
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
        selectorExpression = MqTopics.RESUME_TAG_JOB_TARGET_PARSE,
        consumerGroup = "codecoachai-task-job-target-parse",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class JobTargetParseConsumer implements RocketMQListener<MqMessage<JobTargetParsePayload>> {

    private static final int MAX_RETRY = 3;

    private final AsyncTaskService asyncTaskService;
    private final ResumeFeignClient resumeFeignClient;
    private final NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<JobTargetParsePayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquire(envelope, MAX_RETRY)) {
                return;
            }
            JobTargetParsePayload payload = envelope.getPayload();
            if (payload == null || payload.getTargetJobId() == null || payload.getUserId() == null) {
                throw new NonRetryableMqException("job target parse payload is invalid");
            }

            JobDescriptionParseDTO dto = new JobDescriptionParseDTO();
            dto.setForceRefresh(payload.getForceRefresh());
            dto.setUserTargetDirection(payload.getUserTargetDirection());
            Result<JobDescriptionAnalysisVO> response = resumeFeignClient.executeJobDescriptionParse(
                    payload.getUserId(), payload.getTargetJobId(), dto);
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException("job target parse failed: " + response.getMessage());
                }
                throw new RuntimeException("job target parse returned invalid result: "
                        + (response == null ? "null" : response.getMessage()));
            }

            JobDescriptionAnalysisVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getParseStatus())) {
                String userReason = StringUtils.hasText(result.getParseErrorMessage())
                        ? result.getParseErrorMessage()
                        : "岗位分析生成失败";
                String safeReason = safeFailureReason(result.getParseErrorMessage(), "job target parse failed");
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(payload, userReason);
                log.warn("Job target parse failed targetJobId={} reason={}", payload.getTargetJobId(), safeReason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), "JOB_TARGET_PARSE",
                    String.valueOf(payload.getTargetJobId()), "岗位分析已完成", "目标岗位已分析完成，请查看结构化结果");
            log.info("Job target parse task completed targetJobId={}", payload.getTargetJobId());
        } catch (TerminalTaskFailureException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "job target parse terminal failure");
            log.warn("Job target parse task terminal failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (NonRetryableMqException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "job target parse non-retryable failure");
            log.error("Job target parse task is not retryable messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markDead(envelope, safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (Exception ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "job target parse retryable failure");
            log.error("Job target parse task failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markFailed(envelope.getMessageId(), safeReason);
            throw new RuntimeException("job target parse retryable failure");
        } finally {
            MDC.remove("traceId");
        }
    }

    private void notifyFailed(JobTargetParsePayload payload, String reason) {
        if (payload == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), "JOB_TARGET_PARSE",
                String.valueOf(payload.getTargetJobId()), "岗位分析失败", reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private String safeFailureReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "job target parse failure";
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
