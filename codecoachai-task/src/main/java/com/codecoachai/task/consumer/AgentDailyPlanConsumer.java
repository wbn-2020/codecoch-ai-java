package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.AgentDailyPlanPayload;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.dto.AgentRunFailureDTO;
import com.codecoachai.task.feign.dto.ExecuteAgentDailyPlanDTO;
import com.codecoachai.task.feign.vo.AgentDailyPlanVO;
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
        topic = MqTopics.AGENT,
        selectorExpression = MqTopics.AGENT_TAG_DAILY_PLAN,
        consumerGroup = "codecoachai-task-agent-daily-plan",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 3
)
public class AgentDailyPlanConsumer implements RocketMQListener<MqMessage<AgentDailyPlanPayload>> {

    private static final int MAX_RETRY = 3;
    private static final String NOTIFY_BIZ_TYPE = "AGENT_DAILY_PLAN";
    private static final String AGENT_ASYNC_TASK_FAILED = "AGENT_ASYNC_TASK_FAILED";

    private final AsyncTaskService asyncTaskService;
    private final AiFeignClient aiFeignClient;
    private final NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<AgentDailyPlanPayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquire(envelope, MAX_RETRY)) {
                return;
            }
            AgentDailyPlanPayload payload = envelope.getPayload();
            if (payload == null || payload.getRunId() == null || payload.getUserId() == null) {
                throw new NonRetryableMqException("agent daily plan payload is invalid");
            }

            Result<AgentDailyPlanVO> response = aiFeignClient.executeAgentDailyPlan(payload.getRunId(), toExecuteDto(payload));
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new TerminalTaskFailureException(safeFailureReason(response.getMessage(),
                            "agent daily plan execute failed"));
                }
                throw new RuntimeException("agent daily plan execute returned invalid result: "
                        + safeFailureReason(response == null ? null : response.getMessage(), "no response"));
            }

            AgentDailyPlanVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getStatus())) {
                String reason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "agent daily plan failed";
                String safeReason = safeFailureReason(reason, "agent daily plan failed");
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                notifyFailed(payload, safeReason);
                log.warn("Agent daily plan failed runId={} reason={}", payload.getRunId(), safeReason);
                return;
            }
            if ("CANCELED".equalsIgnoreCase(result.getStatus())) {
                String reason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "agent daily plan canceled";
                String safeReason = safeFailureReason(reason, "agent daily plan canceled");
                asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
                log.info("Agent daily plan canceled runId={} reason={}", payload.getRunId(), safeReason);
                return;
            }
            if (!"SUCCESS".equalsIgnoreCase(result.getStatus())) {
                throw new RuntimeException("agent daily plan execute returned non-success status: "
                        + result.getStatus());
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), NOTIFY_BIZ_TYPE,
                    String.valueOf(payload.getRunId()), "今日计划已生成", "智能教练已完成今日训练计划，请回到今日计划页查看");
            log.info("Agent daily plan completed runId={}", payload.getRunId());
        } catch (TerminalTaskFailureException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "agent daily plan terminal failed");
            log.warn("Agent daily plan task terminal failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), safeReason);
            failAgentRun(envelope.getPayload(), safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (NonRetryableMqException ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "agent daily plan non-retryable failure");
            log.error("Agent daily plan task is not retryable messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markDead(envelope, safeReason);
            failAgentRun(envelope.getPayload(), safeReason);
            notifyFailed(envelope.getPayload(), safeReason);
        } catch (Exception ex) {
            String safeReason = safeFailureReason(ex.getMessage(), "agent daily plan retryable failure");
            log.error("Agent daily plan task failed messageId={} failureType={} reason={}",
                    envelope.getMessageId(), ex.getClass().getSimpleName(), safeReason);
            asyncTaskService.markFailed(envelope.getMessageId(), safeReason);
            throw new RuntimeException("agent daily plan retryable failure");
        } finally {
            MDC.remove("traceId");
        }
    }

    private ExecuteAgentDailyPlanDTO toExecuteDto(AgentDailyPlanPayload payload) {
        ExecuteAgentDailyPlanDTO dto = new ExecuteAgentDailyPlanDTO();
        dto.setUserId(payload.getUserId());
        dto.setExecutionToken(payload.getExecutionToken());
        dto.setTargetJobId(payload.getTargetJobId());
        dto.setDate(payload.getDate());
        dto.setMaxTotalMinutes(payload.getMaxTotalMinutes());
        dto.setTaskCount(payload.getTaskCount());
        dto.setForceRegenerate(payload.getForceRegenerate());
        return dto;
    }

    private void notifyFailed(AgentDailyPlanPayload payload, String reason) {
        if (payload == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), NOTIFY_BIZ_TYPE,
                String.valueOf(payload.getRunId()), "今日计划生成失败", reason);
    }

    private void failAgentRun(AgentDailyPlanPayload payload, String reason) {
        if (payload == null || payload.getRunId() == null || payload.getUserId() == null) {
            return;
        }
        AgentRunFailureDTO dto = new AgentRunFailureDTO();
        dto.setUserId(payload.getUserId());
        dto.setExecutionToken(payload.getExecutionToken());
        dto.setErrorCode(AGENT_ASYNC_TASK_FAILED);
        dto.setErrorMessage(reason);
        try {
            aiFeignClient.failAgentDailyPlan(payload.getRunId(), dto);
        } catch (RuntimeException ex) {
            log.warn("Agent daily plan terminal failure writeback failed runId={}", payload.getRunId(), ex);
        }
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private String safeFailureReason(String reason, String fallback) {
        String base = StringUtils.hasText(fallback) ? fallback : "agent daily plan failure";
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
