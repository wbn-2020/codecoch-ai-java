package com.codecoachai.question.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.payload.QuestionRecommendationGeneratePayload;
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
    public boolean dispatchGenerate(String batchId, Long userId, String topic,
                                    String difficulty, Integer count,
                                    List<String> tags, String targetPosition,
                                    String experienceLevel) {
        return dispatchGenerateWithReceipt(batchId, userId, topic, difficulty, count, tags,
                targetPosition, experienceLevel) != null;
    }

    /**
     * 投递"AI 批量出题"任务，并返回可用于后台任务中心反查的诊断回执。
     */
    public MqDispatchReceipt dispatchGenerateWithReceipt(String batchId, Long userId, String topic,
                                                         String difficulty, Integer count,
                                                         List<String> tags, String targetPosition,
                                                         String experienceLevel) {
        return dispatchGenerateWithReceipt(batchId, userId, topic, difficulty, count, tags,
                targetPosition, null, null, null, null, experienceLevel,
                true, true, true, true, null);
    }

    public MqDispatchReceipt dispatchGenerateWithReceipt(String batchId, Long userId, String topic,
                                                         String difficulty, Integer count,
                                                         List<String> tags, String targetPosition,
                                                         String technologyStack, String knowledgePoint,
                                                         String questionType, Integer experienceYears,
                                                         String experienceLevel,
                                                         Boolean generateReferenceAnswer,
                                                         Boolean generateFollowUps,
                                                         Boolean generateTagSuggestions,
                                                         Boolean generateCategorySuggestion,
                                                         String extraRequirements) {
        if (batchId == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip question generate dispatch batchId={}", batchId);
            return null;
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
                    .technologyStack(technologyStack)
                    .knowledgePoint(knowledgePoint)
                    .questionType(questionType)
                    .experienceYears(experienceYears)
                    .experienceLevel(experienceLevel)
                    .generateReferenceAnswer(generateReferenceAnswer)
                    .generateFollowUps(generateFollowUps)
                    .generateTagSuggestions(generateTagSuggestions)
                    .generateCategorySuggestion(generateCategorySuggestion)
                    .extraRequirements(extraRequirements)
                    .build();
            MqDispatchReceipt receipt = mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_AI_GENERATE),
                    "question.generate",
                    batchId,
                    userId,
                    payload
            );
            log.info("派发批量出题任务 batchId={} topic={} count={} messageId={}",
                    batchId, topic, count, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("派发批量出题任务失败 batchId={}", batchId, ex);
            return null;
        }
    }

    public boolean dispatchQuestionSearchUpsert(Long questionId, Long userId) {
        return dispatchQuestionSearch(questionId, userId, SEARCH_OP_UPSERT);
    }

    public boolean dispatchQuestionSearchDelete(Long questionId, Long userId) {
        return dispatchQuestionSearch(questionId, userId, SEARCH_OP_DELETE);
    }

    public MqDispatchReceipt dispatchRecommendationGenerateWithReceipt(Long batchId, Long userId) {
        if (batchId == null || userId == null) {
            return null;
        }
        if (mqProducer == null) {
            log.warn("MQ producer unavailable, skip question recommendation dispatch batchId={}", batchId);
            return null;
        }
        try {
            QuestionRecommendationGeneratePayload payload = QuestionRecommendationGeneratePayload.builder()
                    .batchId(batchId)
                    .userId(userId)
                    .build();
            MqDispatchReceipt receipt = mqProducer.sendSyncWithReceipt(
                    MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_RECOMMENDATION_GENERATE),
                    "question-recommendation.generate",
                    String.valueOf(batchId),
                    userId,
                    payload
            );
            log.info("Dispatch question recommendation task batchId={} messageId={}",
                    batchId, receipt.getMessageId());
            return receipt;
        } catch (Exception ex) {
            log.error("Dispatch question recommendation task failed batchId={}", batchId, ex);
            return null;
        }
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
