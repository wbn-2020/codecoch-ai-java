package com.codecoachai.resume.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 简历相关 MQ 派发器：resume-service 调用此类把"上传完成"事件投递给 task-service 异步解析。
 *
 * 仅当 RocketMQ 启用（MqProducer Bean 存在）时生效；未启用时此 Bean 不会注入，业务侧可注入为 Optional。
 */
@Slf4j
@Component
@ConditionalOnBean(MqProducer.class)
@RequiredArgsConstructor
public class ResumeMqDispatcher {

    private static final String RESUME_INDEX = "cc_resume";
    private static final String SEARCH_OP_UPSERT = "UPSERT";
    private static final String SEARCH_OP_DELETE = "DELETE";

    private final MqProducer mqProducer;

    /**
     * 投递"简历解析"任务。
     *
     * @param payload 任务负载
     * @return 是否投递成功（异常时返回 false，不会阻塞主流程）
     */
    public boolean dispatchParse(ResumeParsePayload payload) {
        if (payload == null || payload.getResumeId() == null) {
            return false;
        }
        try {
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_PARSE),
                    "resume.parse",
                    String.valueOf(payload.getResumeId()),
                    payload.getUserId(),
                    payload
            );
            return true;
        } catch (Exception ex) {
            log.error("派发简历解析任务失败 resumeId={}", payload.getResumeId(), ex);
            return false;
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
