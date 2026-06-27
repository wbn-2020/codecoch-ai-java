package com.codecoachai.ai.agent.mq;

import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.AgentDailyPlanPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentMqDispatcher {

    public static final String BIZ_TYPE_DAILY_PLAN_GENERATE = "agent.daily-plan.generate";
    private static final String SEND_OK = "SEND_OK";

    private final MqProducer mqProducer;

    public AgentMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    public MqDispatchReceipt dispatchDailyPlanWithReceipt(Long runId, Long userId, DailyPlanGenerateDTO request) {
        if (runId == null || userId == null || request == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip agent daily plan dispatch runId={}", runId);
            return null;
        }
        try {
            AgentDailyPlanPayload payload = AgentDailyPlanPayload.builder()
                    .runId(runId)
                    .userId(userId)
                    .executionToken(request.getExecutionToken())
                    .targetJobId(request.getTargetJobId())
                    .date(request.getDate())
                    .maxTotalMinutes(request.getMaxTotalMinutes())
                    .taskCount(request.getTaskCount())
                    .forceRegenerate(request.getForceRegenerate())
                    .build();
            MqDispatchReceipt receipt = mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.AGENT, MqTopics.AGENT_TAG_DAILY_PLAN),
                    BIZ_TYPE_DAILY_PLAN_GENERATE,
                    String.valueOf(runId),
                    userId,
                    payload
            );
            if (receipt == null || !SEND_OK.equals(receipt.getSendStatus())) {
                log.warn("Agent daily plan MQ dispatch did not complete runId={} status={}",
                        runId, receipt == null ? null : receipt.getSendStatus());
                return null;
            }
            log.info("Dispatched agent daily plan task runId={} messageId={}", runId, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("Dispatch agent daily plan task failed runId={}", runId, ex);
            throw new IllegalStateException("Dispatch agent daily plan task failed", ex);
        }
    }
}
