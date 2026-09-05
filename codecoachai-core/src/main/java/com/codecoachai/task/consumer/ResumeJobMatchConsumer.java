package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeJobMatchPayload;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.mapper.AsyncTaskMapper;
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
@ConditionalOnProperty(
        prefix = "codecoachai.task.consumers",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.JOB_MATCH,
        selectorExpression = MqTopics.JOB_MATCH_TAG_ANALYZE,
        consumerGroup = "codecoachai-task-resume-job-match",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class ResumeJobMatchConsumer implements RocketMQListener<MqMessage<ResumeJobMatchPayload>> {

    private static final int MAX_RETRY = 3;

    private final AsyncTaskService asyncTaskService;
    private final ResumeFeignClient resumeFeignClient;
    private final NotificationService notificationService;
    private final AsyncTaskMapper asyncTaskMapper;

    @Override
    public void onMessage(MqMessage<ResumeJobMatchPayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquireRegistered(envelope, MAX_RETRY)) {
                return;
            }
            ResumeJobMatchPayload payload = envelope.getPayload();
            if (payload == null || payload.getReportId() == null) {
                throw new NonRetryableMqException("resume job match payload is invalid");
            }

            Result<ResumeJobMatchSubmitVO> response = resumeFeignClient.executeJobMatchReport(payload.getReportId());
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException("resume job match execute failed: " + response.getMessage());
                }
                throw new RuntimeException("resume job match execute returned invalid result: "
                        + (response == null ? "null" : response.getMessage()));
            }

            ResumeJobMatchSubmitVO result = response.getData();
            if (!isConsumableSuccess(result)) {
                String userReason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "resume job match report did not produce a trusted result";
                String safeReason = safeFailureReason(result.getErrorMessage(),
                        "resume job match report did not produce a trusted result");
                markReportExecutionFailed(payload, safeReason);
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(payload, userReason);
                log.warn("Resume job match report failed reportId={} reason={}", payload.getReportId(), safeReason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), "RESUME_JOB_MATCH",
                    String.valueOf(payload.getReportId()), "简历匹配报告已生成", "您的简历岗位匹配报告已生成完毕，请查看");
            log.info("Resume job match task completed reportId={}", payload.getReportId());
        } catch (TerminalTaskFailureException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "resume job match terminal failure");
            log.warn("Resume job match task terminal failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            try {
                markReportExecutionFailed(envelope.getPayload(), safeReason);
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(envelope.getPayload(), safeReason);
            } catch (Exception callbackError) {
                throw retryableFailure(envelope, callbackError);
            }
        } catch (NonRetryableMqException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "resume job match non-retryable failure");
            log.error("Resume job match task is not retryable messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            try {
                markReportExecutionFailed(envelope.getPayload(), safeReason);
                asyncTaskService.markDead(envelope, safeReason);
                notifyFailed(envelope.getPayload(), safeReason);
            } catch (Exception callbackError) {
                throw retryableFailure(envelope, callbackError);
            }
        } catch (Exception ex) {
            throw retryableFailure(envelope, ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void notifyFailed(ResumeJobMatchPayload payload, String reason) {
        if (payload == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), "RESUME_JOB_MATCH",
                String.valueOf(payload.getReportId()), "简历匹配报告生成失败", reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private void markReportExecutionFailed(ResumeJobMatchPayload payload, String reason) {
        if (payload == null || payload.getReportId() == null) {
            return;
        }
        Result<ResumeJobMatchSubmitVO> response =
                resumeFeignClient.failJobMatchReport(payload.getReportId(), reason);
        if (response == null
                || !Integer.valueOf(0).equals(response.getCode())
                || response.getData() == null
                || !"FAILED".equalsIgnoreCase(response.getData().getStatus())) {
            throw new RuntimeException("resume job match failure callback returned invalid result: "
                    + (response == null ? "null" : response.getMessage()));
        }
    }

    private RuntimeException retryableFailure(MqMessage<ResumeJobMatchPayload> envelope, Exception ex) {
        String safeReason = safeFailureReason(ex.getMessage(), "resume job match retryable failure");
        log.error("Resume job match task failed messageId={} failureType={} reason={}",
                envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
        boolean terminalExpected = nextFailureWillBeTerminal(envelope.getMessageId());
        if (terminalExpected) {
            try {
                markReportExecutionFailed(envelope.getPayload(), safeReason);
            } catch (Exception callbackError) {
                return restoreAfterPreTerminalWritebackFailure(envelope, safeReason, callbackError);
            }
        }

        boolean terminal = asyncTaskService.markFailed(envelope.getMessageId(), safeReason);
        if (terminal) {
            if (terminalExpected) {
                notifyFailed(envelope.getPayload(), safeReason);
            }
            if (!terminalExpected) {
                restoreTerminalTaskForRetry(envelope, new IllegalStateException(
                        "async task became terminal without a pre-terminal report failure writeback"));
                return new RuntimeException("resume job match terminal state changed during retry handling");
            }
        }
        return new RuntimeException("resume job match retryable failure");
    }

    private RuntimeException restoreAfterPreTerminalWritebackFailure(
            MqMessage<ResumeJobMatchPayload> envelope,
            String reason,
            Exception callbackError) {
        boolean terminal = asyncTaskService.markFailed(envelope.getMessageId(), reason);
        if (terminal) {
            restoreTerminalTaskForRetry(envelope, callbackError);
        }
        return new RuntimeException("resume job match failure writeback is retryable", callbackError);
    }

    private boolean nextFailureWillBeTerminal(String messageId) {
        AsyncTask task = findAsyncTask(messageId);
        if (task == null || !"RUNNING".equalsIgnoreCase(task.getStatus())) {
            return false;
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? MAX_RETRY : task.getMaxRetry();
        return retryCount >= maxRetry;
    }

    private void restoreTerminalTaskForRetry(MqMessage<ResumeJobMatchPayload> envelope, Exception callbackError) {
        AsyncTask task = findAsyncTask(envelope.getMessageId());
        if (task == null || task.getId() == null
                || (!"FAILED".equalsIgnoreCase(task.getStatus()) && !"DEAD".equalsIgnoreCase(task.getStatus()))) {
            throw new IllegalStateException("resume job match failure writeback compensation cannot verify terminal task"
                    + " messageId=" + envelope.getMessageId(), callbackError);
        }
        asyncTaskService.prepareManualRetry(task.getId(), envelope.getMessageId());
        log.warn("Restored terminal resume job match task for failure writeback retry messageId={} taskId={}",
                envelope.getMessageId(), task.getId());
    }

    private AsyncTask findAsyncTask(String messageId) {
        return asyncTaskMapper.selectOne(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getMessageId, messageId)
                .eq(AsyncTask::getDeleted, 0));
    }

    private boolean isConsumableSuccess(ResumeJobMatchSubmitVO result) {
        return result != null
                && "SUCCESS".equalsIgnoreCase(result.getStatus())
                && "VERIFIED".equalsIgnoreCase(result.getTrustStatus())
                && !Boolean.TRUE.equals(result.getFallback())
                && Integer.valueOf(0).equals(result.getSchemaWarningCount())
                && result.getAiCallLogId() != null;
    }

    private String safeFailureReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "resume job match failure";
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
