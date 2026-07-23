package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.QuestionRecommendationGeneratePayload;
import com.codecoachai.task.feign.QuestionFeignClient;
import com.codecoachai.task.feign.dto.ExecuteQuestionRecommendationDTO;
import com.codecoachai.task.feign.vo.QuestionRecommendationGenerateVO;
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
        topic = MqTopics.QUESTION,
        selectorExpression = MqTopics.QUESTION_TAG_RECOMMENDATION_GENERATE,
        consumerGroup = "codecoachai-task-question-recommendation-generate",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class QuestionRecommendationGenerateConsumer
        implements RocketMQListener<MqMessage<QuestionRecommendationGeneratePayload>> {

    private static final int MAX_RETRY = 3;
    private static final String BIZ_TYPE = "QUESTION_RECOMMENDATION_GENERATE";

    private final AsyncTaskService asyncTaskService;
    private final QuestionFeignClient questionFeignClient;
    private final NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<QuestionRecommendationGeneratePayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquire(envelope, MAX_RETRY)) {
                return;
            }
            QuestionRecommendationGeneratePayload payload = envelope.getPayload();
            if (payload == null || payload.getBatchId() == null || payload.getUserId() == null) {
                throw new NonRetryableMqException("question recommendation payload is invalid");
            }

            ExecuteQuestionRecommendationDTO dto = new ExecuteQuestionRecommendationDTO();
            dto.setUserId(payload.getUserId());
            Result<QuestionRecommendationGenerateVO> response =
                    questionFeignClient.executeRecommendation(payload.getBatchId(), dto);
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException("question recommendation execute failed: "
                            + response.getMessage());
                }
                throw new RuntimeException("question recommendation execute returned invalid result: "
                        + (response == null ? "null" : response.getMessage()));
            }

            QuestionRecommendationGenerateVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getStatus())) {
                String userReason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "question recommendation generation failed";
                String safeReason = safeFailureReason(result.getErrorMessage(), "question recommendation generation failed");
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(payload, userReason);
                log.warn("Question recommendation task failed batchId={} reason={}",
                        payload.getBatchId(), safeReason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), BIZ_TYPE,
                    String.valueOf(payload.getBatchId()), "题目推荐已生成",
                    "已根据你的差距报告生成推荐题目，可以回到题目训练查看。");
            log.info("Question recommendation task completed batchId={} count={}",
                    payload.getBatchId(), result.getQuestionCount());
        } catch (TerminalTaskFailureException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "question recommendation terminal failure");
            log.warn("Question recommendation task terminal failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (NonRetryableMqException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "question recommendation non-retryable failure");
            log.error("Question recommendation task is not retryable messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markDead(envelope, safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (Exception ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "question recommendation retryable failure");
            log.error("Question recommendation task failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markFailed(envelope.getMessageId(), safeReason);
            throw new RuntimeException("question recommendation retryable failure");
        } finally {
            MDC.remove("traceId");
        }
    }

    private void notifyFailed(QuestionRecommendationGeneratePayload payload, String reason) {
        if (payload == null || payload.getUserId() == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), BIZ_TYPE,
                String.valueOf(payload.getBatchId()), "题目推荐生成失败", reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private String safeFailureReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "question recommendation failure";
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
