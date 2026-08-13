package com.codecoachai.interview.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.InterviewReportPayload;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 面试相关 MQ 分发器。
 * interview-service 在面试结束后通过此类投递报告生成任务。
 */
@Slf4j
@Component
public class InterviewMqDispatcher {

    private static final String INTERVIEW_INDEX = "cc_interview";
    private static final String SEARCH_OP_UPSERT = "UPSERT";

    private final MqProducer mqProducer;

    public InterviewMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    public boolean dispatchReport(Long sessionId, Long userId) {
        return dispatchReportWithReceipt(sessionId, userId, null) != null;
    }

    public boolean dispatchReport(Long sessionId, Long userId, Long reportId) {
        return dispatchReportWithReceipt(sessionId, userId, reportId) != null;
    }

    public boolean dispatchReport(Long sessionId, Long userId, Long reportId, String generationToken) {
        return dispatchReportWithReceipt(sessionId, userId, reportId, generationToken) != null;
    }

    public MqDispatchReceipt dispatchReportWithReceipt(Long sessionId, Long userId) {
        return dispatchReportWithReceipt(sessionId, userId, null);
    }

    public MqDispatchReceipt dispatchReportWithReceipt(Long sessionId, Long userId, Long reportId) {
        return dispatchReportWithReceipt(sessionId, userId, reportId, null);
    }

    public MqDispatchReceipt dispatchReportWithReceipt(Long sessionId, Long userId,
                                                       Long reportId, String generationToken) {
        if (sessionId == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip interview report dispatch sessionId={} reportId={}",
                    sessionId, reportId);
            return null;
        }
        try {
            InterviewReportPayload payload = InterviewReportPayload.builder()
                    .sessionId(sessionId)
                    .reportId(reportId)
                    .userId(userId)
                    .generationToken(generationToken)
                    .build();
            MqDispatchReceipt receipt = mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.INTERVIEW, MqTopics.INTERVIEW_TAG_REPORT),
                    "interview.report",
                    String.valueOf(reportId == null ? sessionId : reportId),
                    userId,
                    payload
            );
            log.info("分发面试报告任务 sessionId={} reportId={} messageId={}",
                    sessionId, reportId, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("分发面试报告任务失败 sessionId={} reportId={}", sessionId, reportId, ex);
            return null;
        }
    }

    public boolean dispatchInterviewSearchUpsert(Long sessionId, Long userId) {
        if (sessionId == null) {
            return false;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip interview search sync sessionId={}", sessionId);
            return false;
        }
        try {
            SearchSyncPayload payload = SearchSyncPayload.builder()
                    .indexName(INTERVIEW_INDEX)
                    .docId(String.valueOf(sessionId))
                    .op(SEARCH_OP_UPSERT)
                    .build();
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.SEARCH, MqTopics.SEARCH_TAG_INTERVIEW),
                    "search.sync",
                    String.valueOf(sessionId),
                    userId,
                    payload
            );
            log.info("分发面试搜索同步 sessionId={} op={}", sessionId, SEARCH_OP_UPSERT);
            return true;
        } catch (Exception ex) {
            log.error("分发面试搜索同步失败 sessionId={}", sessionId, ex);
            return false;
        }
    }
}
