package com.codecoachai.search.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.mq.consumer.NonRetryableMqException;
import com.codecoachai.common.mq.consumer.RetryableMqException;
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
 * 搜索同步消费者。
 * 接收业务服务投递的变更消息，从业务服务拉取搜索文档后写入 Elasticsearch。
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
                throw new NonRetryableMqException("search sync envelope is invalid");
            }

            idempotentKey = RedisKeyConstants.searchConsumedKey(envelope.getMessageId());
            // RocketMQ 至少一次投递，先用 messageId 做幂等占位；可重试异常会释放占位，允许后续重投。
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", CONSUMED_TTL);
            if (!Boolean.TRUE.equals(ok)) {
                log.debug("Duplicate search sync message skipped messageId={}", envelope.getMessageId());
                return;
            }

            SearchSyncPayload payload = envelope.getPayload();
            if (payload == null || !StringUtils.hasText(payload.getIndexName()) || !StringUtils.hasText(payload.getDocId())) {
                throw new NonRetryableMqException("search sync payload is invalid");
            }

            String op = payload.getOp();
            if ("DELETE".equalsIgnoreCase(op)) {
                handleDelete(payload);
            } else {
                handleUpsert(payload);
            }
        } catch (NonRetryableMqException ex) {
            // 参数错误、未知索引等确定性失败不重试，避免消息反复消费占用队列。
            recordFailure(envelope, ex);
            log.warn("Search sync skipped non-retryable messageId={} reason={}",
                    envelope == null ? null : envelope.getMessageId(), ex.getMessage());
        } catch (Exception ex) {
            // Feign/ES 临时异常交给 RocketMQ 重试，释放幂等键保证下一次投递可重新执行。
            releaseIdempotentKey(idempotentKey);
            recordFailure(envelope, ex);
            log.error("Search sync failed messageId={}", envelope == null ? null : envelope.getMessageId(), ex);
            throw new RetryableMqException("search sync retryable failure", ex);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void handleUpsert(SearchSyncPayload payload) throws Exception {
        Map<String, Object> doc = fetchDocument(payload.getIndexName(), payload.getDocId());
        if (doc == null || doc.isEmpty()) {
            log.info("Search source document missing, delete stale ES doc index={} docId={}",
                    payload.getIndexName(), payload.getDocId());
            handleDelete(payload);
            return;
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
            // 搜索服务不直接读业务库，通过各业务服务 inner 接口拿裁剪后的搜索文档，避免跨库耦合。
            if (IndexNames.QUESTION.equals(indexName)) {
                result = questionFeignClient.getSearchDoc(id);
            } else if (IndexNames.RESUME.equals(indexName)) {
                result = resumeFeignClient.getSearchDoc(id);
            } else if (IndexNames.INTERVIEW.equals(indexName)) {
                result = interviewFeignClient.getSearchDoc(id);
            } else {
                throw new NonRetryableMqException("unknown search index: " + indexName);
            }
            if (result != null && result.isSuccess()) {
                return result.getData();
            }
            if (result == null) {
                throw new RetryableMqException("fetch search document no response");
            }
            if (isBusinessFailure(result.getCode())) {
                throw new NonRetryableMqException("fetch search document business failed: " + result.getMessage());
            }
            throw new RetryableMqException("fetch search document failed: " + result.getMessage());
        } catch (NumberFormatException e) {
            throw new NonRetryableMqException("search docId is not numeric: " + docId, e);
        } catch (NonRetryableMqException | RetryableMqException e) {
            throw e;
        } catch (Exception e) {
            throw new RetryableMqException("fetch search document exception index=" + indexName + ", docId=" + docId, e);
        }
    }

    private boolean isBusinessFailure(Integer code) {
        return code != null && (code == ErrorCode.PARAM_ERROR.getCode()
                || code == ErrorCode.VALIDATION_ERROR.getCode()
                || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.FORBIDDEN.getCode()
                || code == ErrorCode.USER_ERROR.getCode()
                || code == ErrorCode.USER_NOT_FOUND.getCode());
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
            // 失败快照保留 3 天，供后台排查消息、业务 ID、索引和错误原因。
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
