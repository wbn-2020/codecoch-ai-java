package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.dto.CompleteResumeParseDTO;
import com.codecoachai.task.feign.dto.ParseResumeDTO;
import com.codecoachai.task.feign.vo.ParseResumeVO;
import com.codecoachai.task.feign.vo.ResumeAnalysisRawVO;
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
 * 简历解析任务消费者。
 *
 * 流程：
 *   1. 幂等检查 + async_task 落库 RUNNING
 *   2. Feign 调 resume-service 拉取 rawText
 *   3. Feign 调 ai-service.parseResume
 *   4. Feign 调 resume-service 回写结果
 *   5. 标记 SUCCESS / FAILED
 *
 * 失败抛 RuntimeException -> RocketMQ 自动重试；耗尽后由 onMaxRetry 钩子转死信。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
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
    private final AiFeignClient aiFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final com.codecoachai.task.service.NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<ResumeParsePayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            boolean firstTime = asyncTaskService.acquire(envelope, MAX_RETRY);
            if (!firstTime) {
                return;
            }

            ResumeParsePayload payload = envelope.getPayload();
            if (payload == null || payload.getResumeId() == null) {
                throw new NonRetryableMqException("resume parse payload is invalid");
            }

            log.info("开始消费简历解析 resumeId={} fileId={} userId={}",
                    payload.getResumeId(), payload.getFileId(), payload.getUserId());

            // 1. 通过 resume-service 拉取 analysis 记录的 rawText
            //    约定：payload.resumeId 既可以是简历ID也可以承担 analysisRecordId，
            //    上游 dispatcher 应传 analysisRecordId（更准确）；这里以 resumeId 作为兜底标识。
            Long analysisRecordId = payload.getResumeId();
            Result<ResumeAnalysisRawVO> rawResp = resumeFeignClient.getAnalysisRaw(analysisRecordId);
            if (rawResp == null || rawResp.getCode() != 0 || rawResp.getData() == null) {
                throw new NonRetryableMqException("拉取简历解析记录失败：" + (rawResp == null ? "null" : rawResp.getMessage()));
            }
            ResumeAnalysisRawVO raw = rawResp.getData();

            // 已成功则跳过（防重）
            if ("SUCCESS".equals(raw.getParseStatus())) {
                log.info("解析记录已是 SUCCESS，跳过 id={}", analysisRecordId);
                asyncTaskService.markSuccess(envelope.getMessageId(), "ALREADY_DONE");
                return;
            }

            // 2. 调用 AI 解析
            ParseResumeDTO aiDto = new ParseResumeDTO();
            aiDto.setAnalysisRecordId(analysisRecordId);
            aiDto.setUserId(payload.getUserId());
            aiDto.setRawText(raw.getRawText());
            aiDto.setOriginalFilename(raw.getOriginalFilename());
            aiDto.setFileExt(raw.getFileExt());

            Result<ParseResumeVO> aiResp = aiFeignClient.parseResume(aiDto);
            if (aiResp == null || aiResp.getCode() != 0 || aiResp.getData() == null) {
                throw new RuntimeException("AI 解析返回异常: " + (aiResp == null ? "null" : aiResp.getMessage()));
            }
            String structured = aiResp.getData().getStructuredJson();

            // 3. 回写
            CompleteResumeParseDTO complete = new CompleteResumeParseDTO();
            complete.setParseStatus("SUCCESS");
            complete.setStructuredJson(structured);
            complete.setModelTrace("deepseek");
            resumeFeignClient.completeParse(analysisRecordId, complete);

            // 4. 标记成功
            asyncTaskService.markSuccess(envelope.getMessageId(), structured);
            log.info("简历解析任务完成 analysisId={}", analysisRecordId);
            // 5. 通知用户
            notificationService.notifyTaskDone(payload.getUserId(), "RESUME_PARSE",
                    String.valueOf(analysisRecordId), "简历解析完成", "您的简历已解析完成，请查看解析结果");
        } catch (NonRetryableMqException nrEx) {
            log.error("简历解析任务不可重试 messageId={}", envelope.getMessageId(), nrEx);
            asyncTaskService.markDead(envelope, nrEx.getMessage());
            // 失败回写 resume 侧 FAILED
            tryMarkResumeFailed(envelope, nrEx.getMessage());
            // 通知用户失败
            if (envelope.getPayload() != null) {
                notificationService.notifyTaskFailed(envelope.getPayload().getUserId(), "RESUME_PARSE",
                        String.valueOf(envelope.getPayload().getResumeId()), "简历解析失败", nrEx.getMessage());
            }
            // 不抛出，避免 MQ 无意义重试
        } catch (Exception ex) {
            log.error("简历解析任务失败 messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            // 抛出让 MQ 重试；若超过 maxReconsumeTimes，RocketMQ 进入死信队列
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void tryMarkResumeFailed(MqMessage<ResumeParsePayload> envelope, String reason) {
        try {
            ResumeParsePayload payload = envelope.getPayload();
            if (payload == null || payload.getResumeId() == null) return;
            CompleteResumeParseDTO complete = new CompleteResumeParseDTO();
            complete.setParseStatus("FAILED");
            complete.setErrorMessage(reason);
            resumeFeignClient.completeParse(payload.getResumeId(), complete);
        } catch (Exception ignore) {
            log.warn("回写 resume FAILED 状态失败 msgId={}", envelope.getMessageId(), ignore);
        }
    }
}
