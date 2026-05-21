package com.codecoachai.question.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 题库相关 MQ 派发器。
 * 管理员触发"AI 批量出题"时调用此类投递异步任务。
 */
@Slf4j
@Component
public class QuestionMqDispatcher {

    private static final String QUESTION_INDEX = "cc_question";
    private static final String SEARCH_OP_UPSERT = "UPSERT";
    private static final String SEARCH_OP_DELETE = "DELETE";

    private final MqProducer mqProducer;

    public QuestionMqDispatcher(ObjectProvider<MqProducer> mqProducerProvider) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
    }

    /**
     * 投递"AI 批量出题"任务。
     */
    public boolean dispatchGenerate(Long batchId, Long userId, String topic,
                                    String difficulty, Integer count,
                                    List<String> tags, String targetPosition,
                                    String experienceLevel) {
        if (batchId == null) {
            return false;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip question generate dispatch batchId={}", batchId);
            return false;
        }
        try {
            QuestionGeneratePayload payload = QuestionGeneratePayload.builder()
                    .batchId(batchId)
                    .userId(userId)
                    .topic(topic)
                    .difficulty(difficulty)
                    .count(count)
                    .tags(tags)
                    .targetPosition(targetPosition)
                    .experienceLevel(experienceLevel)
                    .build();
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_AI_GENERATE),
                    "question.ai-generate",
                    String.valueOf(batchId),
                    userId,
                    payload
            );
            log.info("派发批量出题任务 batchId={} topic={} count={}", batchId, topic, count);
            return true;
        } catch (Exception ex) {
            log.error("派发批量出题任务失败 batchId={}", batchId, ex);
            return false;
        }
    }

    public boolean dispatchQuestionSearchUpsert(Long questionId, Long userId) {
        return dispatchQuestionSearch(questionId, userId, SEARCH_OP_UPSERT);
    }

    public boolean dispatchQuestionSearchDelete(Long questionId, Long userId) {
        return dispatchQuestionSearch(questionId, userId, SEARCH_OP_DELETE);
    }

    private boolean dispatchQuestionSearch(Long questionId, Long userId, String op) {
        if (questionId == null) {
            return false;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip question search sync questionId={} op={}", questionId, op);
            return false;
        }
        try {
            SearchSyncPayload payload = SearchSyncPayload.builder()
                    .indexName(QUESTION_INDEX)
                    .docId(String.valueOf(questionId))
                    .op(op)
                    .build();
            mqProducer.sendSync(
                    MqTopics.dest(MqTopics.SEARCH, MqTopics.SEARCH_TAG_QUESTION),
                    "search.sync",
                    String.valueOf(questionId),
                    userId,
                    payload
            );
            log.info("派发题库搜索同步 questionId={} op={}", questionId, op);
            return true;
        } catch (Exception ex) {
            log.error("派发题库搜索同步失败 questionId={} op={}", questionId, op, ex);
            return false;
        }
    }
}
