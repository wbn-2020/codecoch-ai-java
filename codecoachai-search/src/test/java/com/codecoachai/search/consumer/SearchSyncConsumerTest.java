package com.codecoachai.search.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.consumer.RetryableMqException;
import com.codecoachai.search.constant.IndexNames;
import com.codecoachai.search.feign.QuestionFeignClient;
import com.codecoachai.search.feign.ResumeFeignClient;
import com.codecoachai.search.feign.InterviewFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchSyncConsumerTest {
    private final ElasticsearchClient es = mock(ElasticsearchClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final QuestionFeignClient questions = mock(QuestionFeignClient.class);
    private final SearchSyncConsumer consumer = new SearchSyncConsumer(es, redis, new ObjectMapper(),
            questions, mock(ResumeFeignClient.class), mock(InterviewFeignClient.class));

    private MqMessage<SearchSyncPayload> message() {
        when(redis.opsForValue()).thenReturn(values);
        return MqMessage.<SearchSyncPayload>builder().messageId("review-test")
                .payload(SearchSyncPayload.builder().indexName(IndexNames.QUESTION).docId("1").op("UPSERT").build())
                .build();
    }

    @Test
    void marksCompletedOnlyAfterEsWriteAndReprocessesLegacyPlaceholder() throws Exception {
        var message = message();
        when(values.get(anyString())).thenReturn("1");
        when(questions.getSearchDoc(1L)).thenReturn(Result.success(Map.of("id", 1)));
        consumer.onMessage(message);
        var order = inOrder(es, values);
        order.verify(es).index(any(IndexRequest.class));
        order.verify(values).set(anyString(), eq("DONE"), any(Duration.class));
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void interruptionBeforeWriteDoesNotLeaveConsumedMarker() throws Exception {
        var message = message();
        when(questions.getSearchDoc(1L)).thenThrow(new AssertionError("simulated abrupt interruption"))
                .thenReturn(Result.success(Map.of("id", 1)));
        assertThrows(AssertionError.class, () -> consumer.onMessage(message));
        verify(values, never()).set(anyString(), eq("DONE"), any(Duration.class));
        consumer.onMessage(message);
        verify(es).index(any(IndexRequest.class));
        verify(values).set(anyString(), eq("DONE"), any(Duration.class));
    }

    @Test
    void completedMessageIsSkipped() {
        var message = message();
        when(values.get(anyString())).thenReturn("DONE");
        consumer.onMessage(message);
        verifyNoInteractions(es, questions);
    }

    @Test
    void failedWriteNeverMarksCompletedAndCanRetry() throws Exception {
        var message = message();
        when(questions.getSearchDoc(1L)).thenReturn(Result.success(Map.of("id", 1)));
        when(es.index(any(IndexRequest.class))).thenThrow(new IllegalStateException("unavailable")).thenReturn(null);
        assertThrows(RetryableMqException.class, () -> consumer.onMessage(message));
        verify(values, never()).set(anyString(), eq("DONE"), any(Duration.class));
        consumer.onMessage(message);
        verify(es, times(2)).index(any(IndexRequest.class));
        verify(values).set(anyString(), eq("DONE"), any(Duration.class));
    }
}
