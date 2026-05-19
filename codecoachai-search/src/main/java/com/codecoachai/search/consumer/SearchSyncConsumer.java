package com.codecoachai.search.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.search.constant.IndexNames;
import com.codecoachai.search.feign.InterviewFeignClient;
import com.codecoachai.search.feign.QuestionFeignClient;
import com.codecoachai.search.feign.ResumeFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ES 索引同步消费者。
 *
 * Topic: codecoachai-search
 * Tag: question / resume / interview
 *
 * 消费 SearchSyncPayload：
 *   - op=UPSERT → 从对应服务拉取最新数据写入 ES
 *   - op=DELETE → 从 ES 删除文档
 *
 * UPSERT 时通过内部 Feign 拉取完整文档数据；拉取失败不写占位文档，
 * 让消息进入重试/死信以保持索引内容和业务数据一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.SEARCH,
        selectorExpression = "*",
        consumerGroup = "codecoachai-search-sync",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = 8
)
public class SearchSyncConsumer implements RocketMQListener<MqMessage<SearchSyncPayload>> {

    private static final String CONSUMED_PREFIX = "codecoachai:search:consumed:";

    private final ElasticsearchClient esClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final QuestionFeignClient questionFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final InterviewFeignClient interviewFeignClient;

    @Override
    public void onMessage(MqMessage<SearchSyncPayload> envelope) {
        String idempotentKey = null;
        if (StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            // 幂等
            idempotentKey = CONSUMED_PREFIX + envelope.getMessageId();
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofDays(3));
            if (!Boolean.TRUE.equals(ok)) {
                log.debug("搜索同步重复消息 messageId={}", envelope.getMessageId());
                return;
            }

            SearchSyncPayload payload = envelope.getPayload();
            if (payload == null || !StringUtils.hasText(payload.getIndexName()) || !StringUtils.hasText(payload.getDocId())) {
                log.warn("搜索同步 payload 无效 messageId={}", envelope.getMessageId());
                return;
            }

            String op = payload.getOp();
            if ("DELETE".equalsIgnoreCase(op)) {
                handleDelete(payload);
            } else {
                handleUpsert(payload);
            }
        } catch (Exception ex) {
            if (StringUtils.hasText(idempotentKey)) {
                redisTemplate.delete(idempotentKey);
            }
            log.error("搜索同步失败 messageId={}", envelope.getMessageId(), ex);
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void handleUpsert(SearchSyncPayload payload) throws Exception {
        // 通过 Feign 拉取完整文档数据
        Map<String, Object> doc = fetchDocument(payload.getIndexName(), payload.getDocId());
        if (doc == null || doc.isEmpty()) {
            throw new IllegalStateException("搜索同步文档为空: index=" + payload.getIndexName()
                    + ", docId=" + payload.getDocId());
        }

        String docJson = objectMapper.writeValueAsString(doc);
        esClient.index(IndexRequest.of(i -> i
                .index(payload.getIndexName())
                .id(payload.getDocId())
                .withJson(new StringReader(docJson))
        ));
        log.info("ES UPSERT index={} docId={}", payload.getIndexName(), payload.getDocId());
    }

    private Map<String, Object> fetchDocument(String indexName, String docId) {
        try {
            Long id = Long.parseLong(docId);
            Result<Map<String, Object>> result;
            if (IndexNames.QUESTION.equals(indexName)) {
                result = questionFeignClient.getSearchDoc(id);
            } else if (IndexNames.RESUME.equals(indexName)) {
                result = resumeFeignClient.getSearchDoc(id);
            } else if (IndexNames.INTERVIEW.equals(indexName)) {
                result = interviewFeignClient.getSearchDoc(id);
            } else {
                log.warn("未知索引类型 index={}", indexName);
                return null;
            }
            if (result != null && result.isSuccess()) {
                return result.getData();
            }
            log.warn("Feign 拉取文档失败 index={} docId={} msg={}", indexName, docId,
                    result != null ? result.getMessage() : "null");
            return null;
        } catch (NumberFormatException e) {
            log.warn("docId 非数字 index={} docId={}", indexName, docId);
            return null;
        } catch (Exception e) {
            log.warn("Feign 调用异常 index={} docId={}", indexName, docId, e);
            return null;
        }
    }

    private void handleDelete(SearchSyncPayload payload) throws Exception {
        esClient.delete(DeleteRequest.of(d -> d
                .index(payload.getIndexName())
                .id(payload.getDocId())
        ));
        log.info("ES DELETE index={} docId={}", payload.getIndexName(), payload.getDocId());
    }
}
