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
 * 闈㈣瘯鐩稿叧 MQ 娲惧彂鍣ㄣ€?
 * interview-service 闈㈣瘯缁撴潫鍚庤皟鐢ㄦ绫绘姇閫?鎶ュ憡鐢熸垚"浠诲姟銆?
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
            log.info("娲惧彂闈㈣瘯鎶ュ憡浠诲姟 sessionId={} reportId={} messageId={}",
                    sessionId, reportId, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("娲惧彂闈㈣瘯鎶ュ憡浠诲姟澶辫触 sessionId={} reportId={}", sessionId, reportId, ex);
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
            log.info("娲惧彂闈㈣瘯鎼滅储鍚屾 sessionId={} op={}", sessionId, SEARCH_OP_UPSERT);
            return true;
        } catch (Exception ex) {
            log.error("娲惧彂闈㈣瘯鎼滅储鍚屾澶辫触 sessionId={}", sessionId, ex);
            return false;
        }
    }
}
