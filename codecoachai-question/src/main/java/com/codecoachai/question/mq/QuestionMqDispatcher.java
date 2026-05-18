package com.codecoachai.question.mq;

import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 题库相关 MQ 派发器。
 * 管理员触发"AI 批量出题"时调用此类投递异步任务。
 */
@Slf4j
@Component
@ConditionalOnBean(MqProducer.class)
@RequiredArgsConstructor
public class QuestionMqDispatcher {

    private final MqProducer mqProducer;

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
}
