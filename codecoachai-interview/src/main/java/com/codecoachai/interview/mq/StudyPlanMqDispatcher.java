package com.codecoachai.interview.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.StudyPlanGeneratePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StudyPlanMqDispatcher {

    public static final String BIZ_TYPE_GENERATE = "study-plan.generate";

    private final MqProducer mqProducer;

    public StudyPlanMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    public MqDispatchReceipt dispatchGenerateWithReceipt(Long planId, Long userId) {
        if (planId == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip study plan dispatch planId={}", planId);
            return null;
        }
        try {
            StudyPlanGeneratePayload payload = StudyPlanGeneratePayload.builder()
                    .planId(planId)
                    .userId(userId)
                    .build();
            MqDispatchReceipt receipt = mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.STUDY_PLAN, MqTopics.STUDY_PLAN_TAG_GENERATE),
                    BIZ_TYPE_GENERATE,
                    String.valueOf(planId),
                    userId,
                    payload
            );
            log.info("Dispatched study plan generation task planId={} messageId={}",
                    planId, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("Dispatch study plan generation task failed planId={}", planId, ex);
            return null;
        }
    }
}
