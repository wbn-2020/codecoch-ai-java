package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
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

            Result<StudyPlanGenerateVO> response = interviewFeignClient.executeStudyPlan(payload.getPlanId());
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException("study plan execute failed: " + response.getMessage());
                }
                throw new RuntimeException("study plan execute returned invalid result: "
                        + (response == null ? "null" : response.getMessage()));
            }

            StudyPlanGenerateVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getPlanStatus())) {
                String reason = StringUtils.hasText(result.getFailureReason())
                        ? result.getFailureReason()
                        : "学习计划生成失败";
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), reason);
                notifyFailed(payload, reason);
                log.warn("Study plan generation failed planId={} reason={}", payload.getPlanId(), reason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), BIZ_TYPE,
                    String.valueOf(payload.getPlanId()), "学习计划已生成",
                    "你的学习计划已生成，可以回到学习计划继续训练。");
            log.info("Study plan generation task completed planId={} taskCount={}",
                    payload.getPlanId(), result.getTaskCount());
        } catch (TerminalTaskFailureException ex) {
            log.warn("Study plan generation task terminal failed messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), ex.getMessage());
            notifyFailed(envelope.getPayload(), ex.getMessage());
        } catch (NonRetryableMqException ex) {
            log.error("Study plan generation task is not retryable messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markDead(envelope, ex.getMessage());
            notifyFailed(envelope.getPayload(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Study plan generation task failed messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
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

    private static class TerminalTaskFailureException extends RuntimeException {
        private TerminalTaskFailureException(String message) {
            super(message);
        }
    }
}
