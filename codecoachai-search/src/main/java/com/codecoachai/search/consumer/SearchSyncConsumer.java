package com.codecoachai.search.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.search.constant.IndexNames;
import com.codecoachai.search.feign.InterviewFeignClient;
import com.codecoachai.search.feign.QuestionFeignClient;
import com.codecoachai.search.feign.ResumeFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 * Consumes search sync messages and applies them to Elasticsearch.
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

    private static final Duration CONSUMED_TTL = Duration.ofDays(3);
    private static final Duration FAILURE_TTL = Duration.ofDays(3);

    private final ElasticsearchClient esClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final QuestionFeignClient questionFeignClient;
    private final ResumeFeignClient resumeFeignClient;
    private final InterviewFeignClient interviewFeignClient;

    @Override
    public void onMessage(MqMessage<SearchSyncPayload> envelope) {
        String idempotentKey = null;
        if (envelope != null && StringUtils.hasText(envelope.getTraceId())) {
            MDC.put("traceId", envelope.getTraceId());
        }

        try {
            if (envelope == null || !StringUtils.hasText(envelope.getMessageId())) {
                throw new IllegalArgumentException("search sync envelope is invalid");
            }

            idempotentKey = RedisKeyConstants.searchConsumedKey(envelope.getMessageId());
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", CONSUMED_TTL);
            if (!Boolean.TRUE.equals(ok)) {
                log.debug("Duplicate search sync message skipped messageId={}", envelope.getMessageId());
                return;
            }

            SearchSyncPayload payload = envelope.getPayload();
            if (payload == null || !StringUtils.hasText(payload.getIndexName()) || !StringUtils.hasText(payload.getDocId())) {
                throw new IllegalArgumentException("search sync payload is invalid");
            }

            String op = payload.getOp();
            if ("DELETE".equalsIgnoreCase(op)) {
                handleDelete(payload);
            } else {
                handleUpsert(payload);
            }
        } catch (Exception ex) {
            releaseIdempotentKey(idempotentKey);
            recordFailure(envelope, ex);
            log.error("Search sync failed messageId={}", envelope == null ? null : envelope.getMessageId(), ex);
            throw new RuntimeException(ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void handleUpsert(SearchSyncPayload payload) throws Exception {
        Map<String, Object> doc = fetchDocument(payload.getIndexName(), payload.getDocId());
        if (doc == null || doc.isEmpty()) {
            throw new IllegalStateException("search sync document is empty: index=" + payload.getIndexName()
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
                log.warn("Unknown search index index={}", indexName);
                return null;
            }
            if (result != null && result.isSuccess()) {
                return result.getData();
            }
            log.warn("Fetch search document failed index={} docId={} msg={}", indexName, docId,
                    result != null ? result.getMessage() : "null");
            return null;
        } catch (NumberFormatException e) {
            log.warn("Search docId is not numeric index={} docId={}", indexName, docId);
            return null;
        } catch (Exception e) {
            log.warn("Fetch search document exception index={} docId={}", indexName, docId, e);
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

    private void releaseIdempotentKey(String idempotentKey) {
        if (!StringUtils.hasText(idempotentKey)) {
            return;
        }
        try {
            redisTemplate.delete(idempotentKey);
        } catch (Exception ex) {
            log.warn("Release search sync idempotent key failed key={}", idempotentKey, ex);
        }
    }

    private void recordFailure(MqMessage<SearchSyncPayload> envelope, Exception ex) {
        if (envelope == null || !StringUtils.hasText(envelope.getMessageId())) {
            return;
        }
        try {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("messageId", envelope.getMessageId());
            failure.put("bizType", envelope.getBizType());
            failure.put("bizId", envelope.getBizId());
            failure.put("traceId", envelope.getTraceId());
            SearchSyncPayload payload = envelope.getPayload();
            if (payload != null) {
                failure.put("indexName", payload.getIndexName());
                failure.put("docId", payload.getDocId());
                failure.put("op", payload.getOp());
            }
            failure.put("error", ex.getMessage());
            failure.put("failedAt", Instant.now().toString());
            redisTemplate.opsForValue().set(
                    RedisKeyConstants.searchSyncFailureKey(envelope.getMessageId()),
                    objectMapper.writeValueAsString(failure),
                    FAILURE_TTL);
        } catch (Exception recordEx) {
            log.warn("Record search sync failure failed messageId={}", envelope.getMessageId(), recordEx);
        }
    }
}
