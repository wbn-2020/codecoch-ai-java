package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.ResumeJobMatchPayload;
import com.codecoachai.task.feign.ResumeFeignClient;
import com.codecoachai.task.feign.vo.ResumeJobMatchSubmitVO;
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
        topic = MqTopics.JOB_MATCH,
        selectorExpression = MqTopics.JOB_MATCH_TAG_ANALYZE,
        consumerGroup = "codecoachai-task-resume-job-match",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class ResumeJobMatchConsumer implements RocketMQListener<MqMessage<ResumeJobMatchPayload>> {

    private static final int MAX_RETRY = 3;

    private final AsyncTaskService asyncTaskService;
    private final ResumeFeignClient resumeFeignClient;
    private final NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<ResumeJobMatchPayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }
        try {
            if (!asyncTaskService.acquire(envelope, MAX_RETRY)) {
                return;
            }
            ResumeJobMatchPayload payload = envelope.getPayload();
            if (payload == null || payload.getReportId() == null) {
                throw new NonRetryableMqException("resume job match payload is invalid");
            }

            Result<ResumeJobMatchSubmitVO> response = resumeFeignClient.executeJobMatchReport(payload.getReportId());
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                if (response != null && isBusinessFailure(response.getCode())) {
                    throw new NonRetryableMqException("resume job match execute failed: " + response.getMessage());
                }
                throw new RuntimeException("resume job match execute returned invalid result: "
                        + (response == null ? "null" : response.getMessage()));
            }

            ResumeJobMatchSubmitVO result = response.getData();
            if ("FAILED".equalsIgnoreCase(result.getStatus())) {
                String reason = StringUtils.hasText(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "resume job match report failed";
                asyncTaskService.markFailed(envelope.getMessageId(), reason);
                notifyFailed(payload, reason);
                log.warn("Resume job match report failed reportId={} reason={}", payload.getReportId(), reason);
                return;
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), result);
            notificationService.notifyTaskDone(payload.getUserId(), "RESUME_JOB_MATCH",
                    String.valueOf(payload.getReportId()), "简历匹配报告已生成", "您的简历岗位匹配报告已生成完毕，请查看");
            log.info("Resume job match task completed reportId={}", payload.getReportId());
        } catch (NonRetryableMqException ex) {
            log.error("Resume job match task is not retryable messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markDead(envelope, ex.getMessage());
            notifyFailed(envelope.getPayload(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Resume job match task failed messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void notifyFailed(ResumeJobMatchPayload payload, String reason) {
        if (payload == null) {
            return;
        }
        notificationService.notifyTaskFailed(payload.getUserId(), "RESUME_JOB_MATCH",
                String.valueOf(payload.getReportId()), "简历匹配报告生成失败", reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }
}
