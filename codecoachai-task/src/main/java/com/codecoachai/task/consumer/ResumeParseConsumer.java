package com.codecoachai.task.consumer;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.task.service.AsyncTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 简历解析任务消费者。
 *
 * Topic: codecoachai-resume
 * Tag:   parse
 * 上游：resume-service 上传后投递
 * 处理：调用 ai 服务的 parseResume（通过 Feign）
 *
 * 失败策略：
 *   - 抛 RuntimeException → RocketMQ 自动重试（指数退避，最多 16 次）
 *   - 抛 NonRetryableMqException → 直接进死信
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.RESUME,
        selectorExpression = MqTopics.RESUME_TAG_PARSE,
        consumerGroup = "codecoachai-task-resume-parse",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 8
)
public class ResumeParseConsumer implements RocketMQListener<MqMessage<ResumeParsePayload>> {

    private static final int MAX_RETRY = 3;

    private final AsyncTaskService asyncTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MqMessage<ResumeParsePayload> envelope) {
        // 注入 traceId 到 MDC
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            // 1. 幂等检查 + 落库 RUNNING
            boolean firstTime = asyncTaskService.acquire(envelope, MAX_RETRY);
            if (!firstTime) {
                return;
            }

            ResumeParsePayload payload = envelope.getPayload();
            log.info("开始消费简历解析任务 resumeId={} fileId={} ossKey={}",
                    payload.getResumeId(), payload.getFileId(), payload.getOssKey());

            // 2. TODO: 通过 Feign 调用 ai-service 的 parseResume，再回写 resume_analysis_record
            //    这里先打日志走通链路，AI 接通在下一步骤完成
            log.info("[模拟] 调用 AI 解析简历 ... resumeId={} userId={}",
                    payload.getResumeId(), payload.getUserId());

            // 3. 标记成功
            asyncTaskService.markSuccess(envelope.getMessageId(),
                    "Stub: parse skeleton ran for resumeId=" + payload.getResumeId());
        } catch (NonRetryableMqException nrEx) {
            log.error("简历解析任务不可重试 messageId={}", envelope.getMessageId(), nrEx);
            asyncTaskService.markDead(envelope, nrEx.getMessage());
            // 不抛出，让 MQ 认为消费成功，避免无意义重试
        } catch (Exception ex) {
            log.error("简历解析任务失败 messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            // 抛出，触发 RocketMQ 自动重试
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }
}
