package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
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
                String reason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "question recommendation generation failed";
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), reason);
                notifyFailed(payload, reason);
                log.warn("Question recommendation task failed batchId={} reason={}",
                        payload.getBatchId(), reason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), BIZ_TYPE,
                    String.valueOf(payload.getBatchId()), "题目推荐已生成",
                    "已根据你的差距报告生成推荐题目，可以回到题目训练查看。");
            log.info("Question recommendation task completed batchId={} count={}",
                    payload.getBatchId(), result.getQuestionCount());
        } catch (TerminalTaskFailureException ex) {
            log.warn("Question recommendation task terminal failed messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), ex.getMessage());
            notifyFailed(envelope.getPayload(), ex.getMessage());
        } catch (NonRetryableMqException ex) {
            log.error("Question recommendation task is not retryable messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markDead(envelope, ex.getMessage());
            notifyFailed(envelope.getPayload(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Question recommendation task failed messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
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

    private static class TerminalTaskFailureException extends RuntimeException {
        private TerminalTaskFailureException(String message) {
            super(message);
        }
    }
}
