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
 * 面试报告生成消费者。
 * Topic: codecoachai-interview  Tag: report
 *
 * 流程：拉取面试上下文 → 调 ai-service.generateInterviewReport → 回写 interview_report
 */
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

    private final AsyncTaskService asyncTaskService;
    private final AiFeignClient aiFeignClient;
    private final InterviewFeignClient interviewFeignClient;
    private final com.codecoachai.task.service.NotificationService notificationService;

    @Override
    public void onMessage(MqMessage<InterviewReportPayload> envelope) {
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            boolean firstTime = asyncTaskService.acquire(envelope, MAX_RETRY);
            if (!firstTime) return;

            InterviewReportPayload payload = envelope.getPayload();
            if (payload == null || payload.getSessionId() == null) {
                throw new NonRetryableMqException("interview report payload invalid");
            }

            log.info("开始消费面试报告任务 sessionId={} userId={}", payload.getSessionId(), payload.getUserId());

            // 1. 拉取上下文
            Result<InterviewReportContextVO> ctxResp = interviewFeignClient.getReportContext(payload.getSessionId());
            if (ctxResp == null || ctxResp.getCode() != 0 || ctxResp.getData() == null) {
                throw new NonRetryableMqException("拉取面试上下文失败: "
                        + (ctxResp == null ? "null" : ctxResp.getMessage()));
            }
            InterviewReportContextVO ctx = ctxResp.getData();

            // 2. 调用 AI
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
                    throw new NonRetryableMqException("AI 面试报告业务失败: " + aiResp.getMessage());
                }
                throw new RuntimeException("AI 报告返回异常: " + (aiResp == null ? "null" : aiResp.getMessage()));
            }

            // 3. 回写
            CompleteInterviewReportDTO complete = new CompleteInterviewReportDTO();
            complete.setReportJson(aiResp.getData().getReportJson());
            complete.setTotalScore(aiResp.getData().getTotalScore());
            complete.setReportStatus("SUCCESS");
            interviewFeignClient.completeReport(payload.getSessionId(), complete);

            asyncTaskService.markSuccess(envelope.getMessageId(), aiResp.getData().getReportJson());
            log.info("面试报告生成完成 sessionId={}", payload.getSessionId());
            // 通知用户
            notificationService.notifyTaskDone(payload.getUserId(), "INTERVIEW_REPORT",
                    String.valueOf(payload.getSessionId()), "面试报告已生成", "您的模拟面试报告已生成完毕，请查看");
        } catch (NonRetryableMqException nrEx) {
            log.error("面试报告任务不可重试 messageId={}", envelope.getMessageId(), nrEx);
            asyncTaskService.markDead(envelope, nrEx.getMessage());
            tryMarkInterviewFailed(envelope, nrEx.getMessage());
            // 通知用户失败
            if (envelope.getPayload() != null) {
                notificationService.notifyTaskFailed(envelope.getPayload().getUserId(), "INTERVIEW_REPORT",
                        String.valueOf(envelope.getPayload().getSessionId()), "面试报告生成失败", nrEx.getMessage());
            }
        } catch (Exception ex) {
            log.error("面试报告任务失败 messageId={}", envelope.getMessageId(), ex);
            asyncTaskService.markFailed(envelope.getMessageId(), ex.getMessage());
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void tryMarkInterviewFailed(MqMessage<InterviewReportPayload> envelope, String reason) {
        try {
            InterviewReportPayload p = envelope.getPayload();
            if (p == null || p.getSessionId() == null) return;
            CompleteInterviewReportDTO c = new CompleteInterviewReportDTO();
            c.setReportStatus("FAILED");
            c.setErrorMessage(reason);
            interviewFeignClient.completeReport(p.getSessionId(), c);
        } catch (Exception ignored) {
            log.warn("回写 interview FAILED 状态失败 msgId={}", envelope.getMessageId(), ignored);
        }
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode());
    }
}
