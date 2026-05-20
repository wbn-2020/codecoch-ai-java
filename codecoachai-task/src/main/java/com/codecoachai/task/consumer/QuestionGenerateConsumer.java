package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.QuestionFeignClient;
import com.codecoachai.task.feign.dto.GenerateQuestionDraftDTO;
import com.codecoachai.task.feign.dto.SaveQuestionDraftsDTO;
import com.codecoachai.task.feign.vo.GenerateQuestionDraftVO;
import com.codecoachai.task.feign.vo.SaveQuestionDraftsVO;
import com.codecoachai.task.service.AsyncTaskService;
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

/**
 * 批量 AI 出题消费者。
 * Topic: codecoachai-question  Tag: ai-generate
 *
 * 流程：调 ai-service.generateQuestionDrafts → 回写 question-service 审核池。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
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
    private final QuestionFeignClient questionFeignClient;
    private final com.codecoachai.task.service.NotificationService notificationService;

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

            GenerateQuestionDraftVO aiData = aiResp.getData();
            int draftCount = aiData.getQuestions() == null ? 0 : aiData.getQuestions().size();
            if (draftCount <= 0) {
                throw new RuntimeException("AI 出题返回空题目列表");
            }

            SaveQuestionDraftsDTO saveDto = new SaveQuestionDraftsDTO();
            saveDto.setBatchId(String.valueOf(payload.getBatchId()));
            saveDto.setCreatedBy(payload.getUserId());
            saveDto.setAiCallLogId(aiData.getAiCallLogId());
            saveDto.setTargetPosition(payload.getTargetPosition());
            saveDto.setTechnologyStack(payload.getTags() == null ? null : String.join(",", payload.getTags()));
            saveDto.setQuestionType("SHORT_ANSWER");
            saveDto.setDifficulty(payload.getDifficulty());
            saveDto.setRawAiResultJson(aiData.getRawResponse());
            saveDto.setQuestions(aiData.getQuestions());
            Result<SaveQuestionDraftsVO> saveResp = questionFeignClient.saveDrafts(saveDto);
            if (saveResp == null || saveResp.getCode() != 0 || saveResp.getData() == null
                    || saveResp.getData().getSavedCount() == null
                    || saveResp.getData().getSavedCount() != draftCount) {
                throw new RuntimeException("题目草稿落库失败: " + (saveResp == null ? "null" : saveResp.getMessage()));
            }
            log.info("AI 出题完成 batchId={} 生成并落库 {} 道题", payload.getBatchId(), draftCount);

            asyncTaskService.markSuccess(envelope.getMessageId(),
                    "generated " + draftCount + " drafts for batch " + payload.getBatchId());
            // 通知用户（如果有 userId）
            if (payload.getUserId() != null) {
                notificationService.notifyTaskDone(payload.getUserId(), "QUESTION_GENERATE",
                        String.valueOf(payload.getBatchId()), "题目生成完成",
                        "已为您生成 " + draftCount + " 道题目，请前往审核");
            }
        } catch (NonRetryableMqException nrEx) {
            log.error("批量出题任务不可重试 messageId={}", envelope.getMessageId(), nrEx);
            asyncTaskService.markDead(envelope, nrEx.getMessage());
            if (envelope.getPayload() != null && envelope.getPayload().getUserId() != null) {
                notificationService.notifyTaskFailed(envelope.getPayload().getUserId(), "QUESTION_GENERATE",
                        String.valueOf(envelope.getPayload().getBatchId()), "题目生成失败", nrEx.getMessage());
            }
        } catch (Exception ex) {
            log.error("批量出题任务失败 messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }
}
