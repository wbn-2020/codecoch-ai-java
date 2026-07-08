package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.StudyPlanGeneratePayload;
import com.codecoachai.task.feign.InterviewFeignClient;
import com.codecoachai.task.feign.vo.StudyPlanGenerateVO;
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
        topic = MqTopics.STUDY_PLAN,
        selectorExpression = MqTopics.STUDY_PLAN_TAG_GENERATE,
        consumerGroup = "codecoachai-task-study-plan-generate",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class StudyPlanGenerateConsumer implements RocketMQListener<MqMessage<StudyPlanGeneratePayload>> {

    private static final int MAX_RETRY = 3;
    private static final String BIZ_TYPE = "study-plan.generate";

    private final AsyncTaskService asyncTaskService;
    private final InterviewFeignClient interviewFeignClient;
    private final NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<StudyPlanGeneratePayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquire(envelope, MAX_RETRY)) {
                return;
            }
            StudyPlanGeneratePayload payload = envelope.getPayload();
            if (payload == null || payload.getPlanId() == null || payload.getUserId() == null) {
                throw new NonRetryableMqException("study plan payload is invalid");
            }

            Result<StudyPlanGenerateVO> response = interviewFeignClient.executeStudyPlan(
                    payload.getUserId(), payload.getPlanId());
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException(safeFailureReason(response.getMessage(),
                            "study plan execute failed"));
                }
                throw new RuntimeException("study plan execute returned invalid result: "
                        + safeFailureReason(response == null ? null : response.getMessage(), "no response"));
            }

            StudyPlanGenerateVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getPlanStatus())) {
                String reason = StringUtils.hasText(result.getFailureReason())
                        ? result.getFailureReason()
                        : "学习计划生成失败";
                String safeReason = safeFailureReason(reason, "study plan generation failed");
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(payload, safeReason);
                log.warn("Study plan generation failed planId={} reason={}", payload.getPlanId(), safeReason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), BIZ_TYPE,
                    String.valueOf(payload.getPlanId()), "学习计划已生成",
                    "你的学习计划已生成，可以回到学习计划继续训练。");
            log.info("Study plan generation task completed planId={} taskCount={}",
                    payload.getPlanId(), result.getTaskCount());
        } catch (TerminalTaskFailureException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "study plan terminal failure");
            log.warn("Study plan generation task terminal failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (NonRetryableMqException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "study plan non-retryable failure");
            log.error("Study plan generation task is not retryable messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markDead(envelope, safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (Exception ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "study plan retryable failure");
            log.error("Study plan generation task failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markFailed(envelope.getMessageId(), safeReason);
            throw new RuntimeException("study plan retryable failure");
        } finally {
            MDC.remove("traceId");
        }
    }

    private void notifyFailed(StudyPlanGeneratePayload payload, String reason) {
        if (payload == null || payload.getUserId() == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), BIZ_TYPE,
                String.valueOf(payload.getPlanId()), "学习计划生成失败", reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private String safeFailureReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "study plan failure";
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
