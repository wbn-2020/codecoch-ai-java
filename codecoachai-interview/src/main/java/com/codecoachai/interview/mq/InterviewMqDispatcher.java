package com.codecoachai.interview.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.payload.InterviewReportPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 面试相关 MQ 派发器。
 * interview-service 面试结束后调用此类投递"报告生成"任务。
 */
@Slf4j
@Component
@ConditionalOnBean(MqProducer.class)
@RequiredArgsConstructor
public class InterviewMqDispatcher {

    private final MqProducer mqProducer;

    /**
     * 投递"面试报告生成"任务。
     */
    public boolean dispatchReport(Long sessionId, Long userId) {
        if (sessionId == null) {
            return false;
        }
        try {
            InterviewReportPayload payload = InterviewReportPayload.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .build();
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.INTERVIEW, MqTopics.INTERVIEW_TAG_REPORT),
                    "interview.report",
                    String.valueOf(sessionId),
                    userId,
                    payload
            );
            log.info("派发面试报告任务 sessionId={}", sessionId);
            return true;
        } catch (Exception ex) {
            log.error("派发面试报告任务失败 sessionId={}", sessionId, ex);
            return false;
        }
    }
}
