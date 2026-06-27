package com.codecoachai.task.consumer;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.InterviewReportPayload;
import com.codecoachai.task.feign.AiFeignClient;
import com.codecoachai.task.feign.InterviewFeignClient;
import com.codecoachai.task.feign.dto.CompleteInterviewReportDTO;
import com.codecoachai.task.feign.dto.GenerateReportDTO;
import com.codecoachai.task.feign.vo.GenerateReportVO;
import com.codecoachai.task.feign.vo.InterviewReportContextVO;
import com.codecoachai.task.service.AsyncTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        topic = MqTopics.INTERVIEW,
        selectorExpression = MqTopics.INTERVIEW_TAG_REPORT,
        consumerGroup = "codecoachai-task-interview-report",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 6
)
public class InterviewReportConsumer implements RocketMQListener<MqMessage<InterviewReportPayload>> {

    private static final int MAX_RETRY = 3;
    private static final String INTERVIEW_REPORT_BIZ_TYPE = "INTERVIEW_REPORT";
    private static final String REPORT_READY_TITLE = "\u9762\u8bd5\u62a5\u544a\u5df2\u751f\u6210";
    private static final String REPORT_READY_CONTENT =
            "\u60a8\u7684\u6a21\u62df\u9762\u8bd5\u62a5\u544a\u5df2\u751f\u6210\u5b8c\u6210\uff0c\u8bf7\u67e5\u770b";
    private static final String REPORT_FAILED_TITLE = "\u9762\u8bd5\u62a5\u544a\u751f\u6210\u5931\u8d25";

    private final AsyncTaskService asyncTaskService;
    private final AiFeignClient aiFeignClient;
    private final InterviewFeignClient interviewFeignClient;
    private final com.codecoachai.task.service.NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MqMessage<InterviewReportPayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            boolean firstTime = asyncTaskService.acquire(envelope, MAX_RETRY);
            if (!firstTime) {
                return;
            }

            InterviewReportPayload payload = envelope.getPayload();
            if (payload == null || payload.getSessionId() == null) {
                throw new NonRetryableMqException("interview report payload invalid");
            }

            log.info("Start interview report task sessionId={} reportId={} userId={}",
                    payload.getSessionId(), payload.getReportId(), payload.getUserId());

            Result<InterviewReportContextVO> ctxResp = interviewFeignClient.getReportContext(payload.getSessionId());
            if (ctxResp == null) {
                throw new RuntimeException("Load interview report context failed: null");
            }
            if (ctxResp.getCode() != 0 || ctxResp.getData() == null) {
                String reason = "Load interview report context failed: " + ctxResp.getMessage();
                if (isBusinessFailure(ctxResp.getCode())) {
                    throw new TerminalTaskFailureException(reason);
                }
                throw new RuntimeException(reason);
            }
            InterviewReportContextVO ctx = ctxResp.getData();

            GenerateReportDTO aiDto = new GenerateReportDTO();
            aiDto.setInterviewId(ctx.getSessionId());
            aiDto.setUserId(ctx.getUserId());
            aiDto.setMode(ctx.getMode());
            aiDto.setTargetPosition(ctx.getTargetPosition());
            aiDto.setExperienceLevel(ctx.getExperienceLevel());
            aiDto.setIndustryDirection(ctx.getIndustryDirection());
            aiDto.setIndustryContext(ctx.getIndustryContext());
            aiDto.setDifficulty(ctx.getDifficulty());
            aiDto.setResumeContent(ctx.getResumeContent());
            aiDto.setProjectContent(ctx.getProjectContent());
            aiDto.setMessages(ctx.getMessages());

            Result<GenerateReportVO> aiResp = aiFeignClient.generateInterviewReport(aiDto);
            if (aiResp == null || aiResp.getCode() != 0 || aiResp.getData() == null) {
                if (aiResp != null && isBusinessFailure(aiResp.getCode())) {
                    throw new TerminalTaskFailureException("AI interview report failed: " + aiResp.getMessage());
                }
                throw new RuntimeException("AI interview report response invalid: "
                        + (aiResp == null ? "null" : aiResp.getMessage()));
            }

            String serializedReport = serializeReportPayload(aiResp.getData());

            CompleteInterviewReportDTO complete = new CompleteInterviewReportDTO();
            complete.setReportId(payload.getReportId());
            complete.setGenerationToken(payload.getGenerationToken());
            complete.setReportJson(serializedReport);
            complete.setTotalScore(aiResp.getData().getTotalScore());
            complete.setReportStatus("SUCCESS");
            Result<Void> completeResp = interviewFeignClient.completeReport(payload.getSessionId(), complete);
            if (completeResp == null || completeResp.getCode() != 0) {
                if (completeResp != null && isBusinessFailure(completeResp.getCode())) {
                    throw new TerminalTaskFailureException("Persist interview report failed: "
                            + completeResp.getMessage());
                }
                throw new RuntimeException("Persist interview report response invalid: "
                        + (completeResp == null ? "null" : completeResp.getMessage()));
            }

            asyncTaskService.markSuccess(envelope.getMessageId(), serializedReport);
            log.info("Interview report completed sessionId={} reportId={}",
                    payload.getSessionId(), payload.getReportId());
            notificationService.notifyTaskDone(
                    payload.getUserId(),
                    INTERVIEW_REPORT_BIZ_TYPE,
                    String.valueOf(payload.getSessionId()),
                    REPORT_READY_TITLE,
                    REPORT_READY_CONTENT);
        } catch (TerminalTaskFailureException terminalEx) {
            log.warn("Interview report terminal failure messageId={}", envelope.getMessageId(), terminalEx);
            asyncTaskService.markTerminalFailed(envelope.getMessageId(), terminalEx.getMessage());
            tryMarkInterviewFailed(envelope, terminalEx.getMessage());
            notifyTaskFailed(envelope, terminalEx.getMessage());
        } catch (NonRetryableMqException nrEx) {
            log.error("Interview report non-retryable messageId={}", envelope.getMessageId(), nrEx);
            asyncTaskService.markDead(envelope, nrEx.getMessage());
            tryMarkInterviewFailed(envelope, nrEx.getMessage());
            notifyTaskFailed(envelope, nrEx.getMessage());
        } catch (Exception ex) {
            log.error("Interview report failed messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void tryMarkInterviewFailed(MqMessage<InterviewReportPayload> envelope, String reason) {
        try {
            InterviewReportPayload payload = envelope.getPayload();
            if (payload == null || payload.getSessionId() == null) {
                return;
            }
            CompleteInterviewReportDTO complete = new CompleteInterviewReportDTO();
            complete.setReportId(payload.getReportId());
            complete.setGenerationToken(payload.getGenerationToken());
            complete.setReportStatus("FAILED");
            complete.setErrorMessage(reason);
            interviewFeignClient.completeReport(payload.getSessionId(), complete);
        } catch (Exception ignored) {
            log.warn("Persist interview FAILED status failed msgId={}", envelope.getMessageId(), ignored);
        }
    }

    private void notifyTaskFailed(MqMessage<InterviewReportPayload> envelope, String reason) {
        InterviewReportPayload payload = envelope.getPayload();
        if (payload == null) {
            return;
        }
        notificationService.notifyTaskFailed(
                payload.getUserId(),
                INTERVIEW_REPORT_BIZ_TYPE,
                String.valueOf(payload.getSessionId()),
                REPORT_FAILED_TITLE,
                reason);
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }

    private String serializeReportPayload(GenerateReportVO report) {
        if (report == null) {
            return null;
        }
        if (StringUtils.hasText(report.getReportJson())) {
            return report.getReportJson();
        }
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            log.warn("Serialize interview report payload failed, fallback to reportContent only", ex);
            return report.getReportContent();
        }
    }

    private static class TerminalTaskFailureException extends RuntimeException {
        private TerminalTaskFailureException(String message) {
            super(message);
        }
    }
}
