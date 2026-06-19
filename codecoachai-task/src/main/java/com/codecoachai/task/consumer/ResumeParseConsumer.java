package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
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

import java.util.function.Supplier;

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

    // ==================== 对 Feign 调用的本地重试 ====================

    /**
     * 带指数退避本地重试的 Feign 调用封装。
     * 最多重试 maxRetries 次，间隔依次为 initialIntervalMs * 2^(attempt-1)。
     * 所有重试均耗尽后抛 {@link NonRetryableMqException}，由外层 catch
     * 转入死信处理（markDead），避免触发 MQ 级重试。
     */
    private <T> T retryableFeignCall(String description, int maxRetries, long initialIntervalMs,
                                     Supplier<T> call) {
        RuntimeException lastEx = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException ex) {
                lastEx = ex;
                // 业务失败（参数错误/权限等）不重试
                if (ex instanceof TerminalTaskFailureException) {
                    throw ex;
                }
                if (attempt < maxRetries) {
                    long interval = initialIntervalMs * (1L << (attempt - 1)); // 1s, 2s, 4s
                    log.warn("{} 失败 (第{}/{}次)，{}ms 后重试: {}",
                            description, attempt, maxRetries, interval, ex.getMessage());
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new NonRetryableMqException(description + " 重试被中断", ie);
                    }
                }
            }
        }
        // 全部重试耗尽 -> 转入死信，不触发 MQ 级重试
        log.error("{} 重试 {}/{} 次全部失败，转入死信队列", description, maxRetries, maxRetries);
        throw new NonRetryableMqException(description + " 本地重试 " + maxRetries + " 次耗尽: "
                + (lastEx != null ? lastEx.getMessage() : "unknown"));
    }

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

            // 1. 通过 resume-service 拉取 analysis 记录的 rawText（带本地重试）
            //    约定：payload.resumeId 既可以是简历ID也可以承担 analysisRecordId，
            //    上游 dispatcher 应传 analysisRecordId（更准确）；这里以 resumeId 作为兜底标识。
            Long analysisRecordId = payload.getResumeId();
            Result<ResumeAnalysisRawVO> rawResp = retryableFeignCall(
                    "拉取简历解析记录 analysisId=" + analysisRecordId, 3, 1000L,
                    () -> resumeFeignClient.getAnalysisRaw(analysisRecordId)
            );
            if (rawResp == null) {
                throw new RuntimeException("拉取简历解析记录失败：null");
            }
            if (rawResp.getCode() != 0 || rawResp.getData() == null) {
                String reason = "拉取简历解析记录失败：" + rawResp.getMessage();
                if (isBusinessFailure(rawResp.getCode())) {
                    throw new TerminalTaskFailureException(reason);
                }
                throw new RuntimeException(reason);
            }
            ResumeAnalysisRawVO raw = rawResp.getData();

            // 已成功则跳过（防重）
            if ("SUCCESS".equals(raw.getParseStatus())) {
                log.info("解析记录已是 SUCCESS，跳过 id={}", analysisRecordId);
                asyncTaskService.markSuccess(envelope.getMessageId(), "ALREADY_DONE");
                return;
            }

            // 2. 调用 AI 解析（带本地重试）
            ParseResumeDTO aiDto = new ParseResumeDTO();
            aiDto.setAnalysisRecordId(analysisRecordId);
            aiDto.setUserId(payload.getUserId());
            aiDto.setRawText(raw.getRawText());
            aiDto.setOriginalFilename(raw.getOriginalFilename());
            aiDto.setFileExt(raw.getFileExt());

            Result<ParseResumeVO> aiResp = retryableFeignCall(
                    "AI 简历解析 analysisId=" + analysisRecordId, 3, 1000L,
                    () -> aiFeignClient.parseResume(aiDto)
            );
            if (aiResp == null || aiResp.getCode() != 0 || aiResp.getData() == null) {
                if (aiResp != null && isBusinessFailure(aiResp.getCode())) {
                    throw new TerminalTaskFailureException("AI 简历解析业务失败: " + aiResp.getMessage());
                }
                throw new RuntimeException("AI 解析返回异常: " + (aiResp == null ? "null" : aiResp.getMessage()));
            }
            String structured = aiResp.getData().getStructuredJson();

            // 3. 回写（带本地重试）
            CompleteResumeParseDTO complete = new CompleteResumeParseDTO();
            complete.setParseStatus("WAIT_CONFIRM");
            complete.setStructuredJson(structured);
            complete.setRawText(raw.getRawText());
            complete.setModelTrace("deepseek");
            Result<Void> completeResp = retryableFeignCall(
                    "回写简历解析结果 analysisId=" + analysisRecordId, 3, 1000L,
                    () -> resumeFeignClient.completeParse(analysisRecordId, complete)
            );
            if (completeResp == null || completeResp.getCode() != 0) {
                if (completeResp != null && isBusinessFailure(completeResp.getCode())) {
                    throw new TerminalTaskFailureException("回写简历解析结果失败: " + completeResp.getMessage());
                }
                throw new RuntimeException("回写简历解析结果异常: "
                        + (completeResp == null ? "null" : completeResp.getMessage()));
            }

            // 4. 标记成功
            asyncTaskService.markSuccess(envelope.getMessageId(), structured);
            log.info("简历解析任务完成 analysisId={}", analysisRecordId);
            // 5. 通知用户
            notificationService.notifyTaskDone(payload.getUserId(), "RESUME_PARSE",
                    String.valueOf(analysisRecordId), "简历解析完成", "您的简历已解析完成，请查看解析结果");
        } catch (TerminalTaskFailureException terminalEx) {
            log.warn("简历解析任务业务终态失败 messageId={}", envelope.getMessageId(), terminalEx);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), terminalEx.getMessage());
            tryMarkResumeFailed(envelope, terminalEx.getMessage());
            if (envelope.getPayload() != null) {
                notificationService.notifyTaskFailed(envelope.getPayload().getUserId(), "RESUME_PARSE",
                        String.valueOf(envelope.getPayload().getResumeId()), "简历解析失败", terminalEx.getMessage());
            }
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
