package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.dto.GenerateQuestionDraftDTO;
import com.codecoachai.task.feign.vo.GenerateQuestionDraftVO;
import com.codecoachai.task.service.AsyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 批量 AI 出题消费者。
 * Topic: codecoachai-question  Tag: ai-generate
 *
 * 流程：调 ai-service.generateQuestionDrafts → 结果回写（TODO: 通过 question-service inner 接口）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.QUESTION,
        selectorExpression = MqTopics.QUESTION_TAG_AI_GENERATE,
        consumerGroup = "codecoachai-task-question-generate",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class QuestionGenerateConsumer implements RocketMQListener<MqMessage<QuestionGeneratePayload>> {

    private static final int MAX_RETRY = 3;

    private final AsyncTaskService asyncTaskService;
    private final AiFeignClient aiFeignClient;

    @Override
    public void onMessage(MqMessage<QuestionGeneratePayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            boolean firstTime = asyncTaskService.acquire(envelope, MAX_RETRY);
            if (!firstTime) return;

            QuestionGeneratePayload payload = envelope.getPayload();
            if (payload == null || payload.getBatchId() == null) {
                throw new NonRetryableMqException("question generate payload invalid");
            }

            log.info("开始消费批量出题任务 batchId={} topic={} count={}",
                    payload.getBatchId(), payload.getTopic(), payload.getCount());

            // 调用 AI
            GenerateQuestionDraftDTO dto = new GenerateQuestionDraftDTO();
            dto.setTopic(payload.getTopic());
            dto.setDifficulty(payload.getDifficulty());
            dto.setCount(payload.getCount());
            dto.setTags(payload.getTags());
            dto.setTargetPosition(payload.getTargetPosition());
            dto.setExperienceLevel(payload.getExperienceLevel());
            dto.setBatchId(payload.getBatchId());

            Result<GenerateQuestionDraftVO> aiResp = aiFeignClient.generateQuestionDrafts(dto);
            if (aiResp == null || aiResp.getCode() != 0 || aiResp.getData() == null) {
                throw new RuntimeException("AI 出题返回异常: " + (aiResp == null ? "null" : aiResp.getMessage()));
            }

            // TODO 第 4 周：通过 question-service inner 接口批量写入 question_draft 表
            int draftCount = aiResp.getData().getDrafts() == null ? 0 : aiResp.getData().getDrafts().size();
            log.info("AI 出题完成 batchId={} 生成 {} 道题", payload.getBatchId(), draftCount);

            asyncTaskService.markSuccess(envelope.getMessageId(),
                    "generated " + draftCount + " drafts for batch " + payload.getBatchId());
        } catch (NonRetryableMqException nrEx) {
            log.error("批量出题任务不可重试 messageId={}", envelope.getMessageId(), nrEx);
            asyncTaskService.markDead(envelope, nrEx.getMessage());
        } catch (Exception ex) {
            log.error("批量出题任务失败 messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }
}
