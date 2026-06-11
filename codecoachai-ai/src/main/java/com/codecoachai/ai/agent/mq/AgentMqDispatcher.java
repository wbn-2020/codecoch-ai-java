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
            log.info("Dispatched agent daily plan task runId={} messageId={}", runId, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("Dispatch agent daily plan task failed runId={}", runId, ex);
            return null;
        }
    }
}
