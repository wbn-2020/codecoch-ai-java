package com.codecoachai.resume.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.JobTargetParsePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobTargetParseMqDispatcher {

    public static final String BIZ_TYPE = "job-target.parse";

    private final MqProducer mqProducer;

    public JobTargetParseMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    public MqDispatchReceipt dispatchParseWithReceipt(Long targetJobId, Long userId,
                                                      Boolean forceRefresh, String userTargetDirection) {
        if (targetJobId == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip job target parse dispatch targetJobId={}", targetJobId);
            return null;
        }
        try {
            JobTargetParsePayload payload = JobTargetParsePayload.builder()
                    .targetJobId(targetJobId)
                    .userId(userId)
                    .forceRefresh(forceRefresh)
                    .userTargetDirection(userTargetDirection)
                    .build();
            MqDispatchReceipt receipt = mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_JOB_TARGET_PARSE),
                    BIZ_TYPE,
                    String.valueOf(targetJobId),
                    userId,
                    payload
            );
            log.info("Dispatched job target parse task targetJobId={}", targetJobId);
            return receipt;
        } catch (Exception ex) {
            log.error("Dispatch job target parse task failed targetJobId={}", targetJobId, ex);
            return null;
        }
    }
}
