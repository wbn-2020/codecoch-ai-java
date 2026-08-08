package com.codecoachai.resume.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.ResumeOptimizePayload;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 简历相关 MQ 派发器：resume-service 调用此类把"上传完成"事件投递给 task-service 异步解析。
 *
 * RocketMQ 不可用时保留 Bean，并安全跳过异步投递。
 */
@Slf4j
@Component
public class ResumeMqDispatcher {

    private static final String RESUME_INDEX = "cc_resume";
    private static final String SEARCH_OP_UPSERT = "UPSERT";
    private static final String SEARCH_OP_DELETE = "DELETE";

    private final MqProducer mqProducer;

    public ResumeMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    /**
     * 投递"简历解析"任务。
     *
     * @param payload 任务负载
     * @return 是否投递成功（异常时返回 false，不会阻塞主流程）
     */
    public boolean dispatchParse(ResumeParsePayload payload) {
        return dispatchParseWithReceipt(payload) != null;
    }

    public MqDispatchReceipt dispatchParseWithReceipt(ResumeParsePayload payload) {
        if (payload == null || payload.getResumeId() == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip resume parse dispatch resumeId={}",
                    payload.getResumeId());
            return null;
        }
        try {
            return mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_PARSE),
                    "resume.parse",
                    String.valueOf(payload.getResumeId()),
                    payload.getUserId(),
                    payload
            );
        } catch (Exception ex) {
            log.error("派发简历解析任务失败 resumeId={}", payload.getResumeId(), ex);
            return null;
        }
    }

    public MqDispatchReceipt dispatchOptimizeWithReceipt(ResumeOptimizePayload payload) {
        if (payload == null || payload.getOptimizeRecordId() == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip resume optimize dispatch optimizeRecordId={}",
                    payload.getOptimizeRecordId());
            return null;
        }
        try {
            return mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_OPTIMIZE),
                    "resume.optimize",
                    String.valueOf(payload.getOptimizeRecordId()),
                    payload.getUserId(),
                    payload
            );
        } catch (Exception ex) {
            log.error("Dispatch resume optimize task failed optimizeRecordId={}",
                    payload.getOptimizeRecordId(), ex);
            return null;
        }
    }

    public boolean dispatchResumeSearchUpsert(Long resumeId, Long userId) {
        return dispatchResumeSearch(resumeId, userId, SEARCH_OP_UPSERT);
    }

    public boolean dispatchResumeSearchDelete(Long resumeId, Long userId) {
        return dispatchResumeSearch(resumeId, userId, SEARCH_OP_DELETE);
    }

    private boolean dispatchResumeSearch(Long resumeId, Long userId, String op) {
        if (resumeId == null) {
            return false;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip resume search sync resumeId={} op={}",
                    resumeId, op);
            return false;
        }
        try {
            SearchSyncPayload payload = SearchSyncPayload.builder()
                    .indexName(RESUME_INDEX)
                    .docId(String.valueOf(resumeId))
                    .op(op)
                    .build();
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.SEARCH, MqTopics.SEARCH_TAG_RESUME),
                    "search.sync",
                    String.valueOf(resumeId),
                    userId,
                    payload
            );
            log.info("派发简历搜索同步 resumeId={} op={}", resumeId, op);
            return true;
        } catch (Exception ex) {
            log.error("派发简历搜索同步失败 resumeId={} op={}", resumeId, op, ex);
            return false;
        }
    }
}
