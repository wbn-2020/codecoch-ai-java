package com.codecoachai.task.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

    @Mock
    private AsyncTaskMapper asyncTaskMapper;
    @Mock
    private MessageDeadLetterMapper deadLetterMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AsyncTaskService service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(AsyncTask.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AsyncTask.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new AsyncTaskService(asyncTaskMapper, deadLetterMapper, redisTemplate, new ObjectMapper());
    }

    @Test
    void acquirePersistsTaskBeforeSettingRedisConsumedKey() {
        MqMessage<String> message = message("msg-1");
        when(asyncTaskMapper.selectOne(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.mqConsumedKey("msg-1")),
                eq("1"), eq(Duration.ofDays(7)))).thenReturn(true);

        assertTrue(service.acquire(message, 3));

        InOrder order = inOrder(asyncTaskMapper, valueOperations);
        order.verify(asyncTaskMapper).insert(any(AsyncTask.class));
        order.verify(valueOperations).setIfAbsent(eq(RedisKeyConstants.mqConsumedKey("msg-1")),
                eq("1"), eq(Duration.ofDays(7)));
    }

    @Test
    void acquireContinuesAfterInsertWhenRedisConsumedKeyIsStale() {
        MqMessage<String> message = message("msg-stale-redis");
        when(asyncTaskMapper.selectOne(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.mqConsumedKey("msg-stale-redis")),
                eq("1"), eq(Duration.ofDays(7)))).thenReturn(false);

        assertTrue(service.acquire(message, 3));

        verify(asyncTaskMapper).insert(any(AsyncTask.class));
        verify(valueOperations).setIfAbsent(eq(RedisKeyConstants.mqConsumedKey("msg-stale-redis")),
                eq("1"), eq(Duration.ofDays(7)));
    }

    @Test
    void acquireSkipsAlreadySuccessfulTaskEvenWhenRedisKeyExpired() {
        AsyncTask existing = new AsyncTask();
        existing.setId(100L);
        existing.setMessageId("msg-success");
        existing.setStatus("SUCCESS");
        when(asyncTaskMapper.selectOne(any())).thenReturn(existing);

        assertFalse(service.acquire(message("msg-success"), 3));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void acquireDoesNotSetRedisKeyWhenDatabaseInsertFails() {
        MqMessage<String> message = message("msg-db-fails");
        when(asyncTaskMapper.selectOne(any())).thenReturn(null);
        doThrow(new RuntimeException("db unavailable")).when(asyncTaskMapper).insert(any(AsyncTask.class));

        assertThrows(RuntimeException.class, () -> service.acquire(message, 3));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void markFailedReleasesRedisConsumedKeyForRetry() {
        service.markFailed("msg-failed", "upstream timeout");

        verify(redisTemplate).delete(RedisKeyConstants.mqConsumedKey("msg-failed"));
    }

    private MqMessage<String> message(String messageId) {
        MqMessage<String> message = new MqMessage<>();
        message.setMessageId(messageId);
        message.setBizType("resume.parse");
        message.setBizId("resume-1");
        message.setUserId(10L);
        message.setTraceId("trace-1");
        message.setPayload("payload");
        message.setRetryCount(0);
        return message;
    }
}
