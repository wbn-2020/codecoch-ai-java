package com.codecoachai.resume.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.payload.ResumeJobMatchPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResumeJobMatchMqDispatcher {

    private final MqProducer mqProducer;

    public ResumeJobMatchMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    public boolean dispatchAnalyze(Long reportId, Long userId) {
        if (reportId == null) {
            return false;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip resume job match dispatch reportId={}", reportId);
            return false;
        }
        try {
            ResumeJobMatchPayload payload = ResumeJobMatchPayload.builder()
                    .reportId(reportId)
                    .userId(userId)
                    .build();
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.JOB_MATCH, MqTopics.JOB_MATCH_TAG_ANALYZE),
                    "resume-job-match.analyze",
                    String.valueOf(reportId),
                    userId,
                    payload
            );
            log.info("Dispatched resume job match report task reportId={}", reportId);
            return true;
        } catch (Exception ex) {
            log.error("Dispatch resume job match report task failed reportId={}", reportId, ex);
            return false;
        }
    }
}
