package com.codecoachai.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.task.config.AsyncTaskLeaseProperties;
import com.codecoachai.task.config.AsyncTaskLeaseRuntime;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 26, 12, 30, 15);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    @Mock
    private AsyncTaskMapper asyncTaskMapper;
    @Mock
    private MessageDeadLetterMapper deadLetterMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private TaskScheduler leaseScheduler;
    @Mock
    private ScheduledFuture<?> heartbeat;

    private AsyncTaskLeaseProperties leaseProperties;
    private TestLeaseRuntime leaseRuntime;
    private AsyncTaskService service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        if (TableInfoHelper.getTableInfo(AsyncTask.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    AsyncTask.class);
        }
    }

    @BeforeEach
    void setUp() {
        leaseProperties = new AsyncTaskLeaseProperties();
        leaseProperties.setDuration(LEASE_DURATION);
        leaseProperties.setHeartbeatInterval(HEARTBEAT_INTERVAL);
        leaseRuntime = new TestLeaseRuntime(NOW, "token-default");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(asyncTaskMapper.verifyLeaseOwner(anyString(), anyString())).thenReturn(1);
        lenient().doReturn(heartbeat)
                .when(leaseScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        service = newService(leaseRuntime);
    }

    @Test
    void acquireAtomicallyPersistsRunningTaskAndLeaseTokenBeforeRedis() {
        MqMessage<String> message = message("msg-1");
        when(asyncTaskMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-1"),
                "token-default",
                LEASE_DURATION)).thenReturn(true);

        assertTrue(service.acquire(message, 3));

        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        InOrder order = inOrder(asyncTaskMapper, valueOperations);
        order.verify(asyncTaskMapper).insert(taskCaptor.capture());
        order.verify(valueOperations).setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-1"),
                "token-default",
                LEASE_DURATION);
        order.verify(asyncTaskMapper).verifyLeaseOwner("msg-1", "token-default");
        AsyncTask inserted = taskCaptor.getValue();
        assertEquals("RUNNING", inserted.getStatus());
        assertEquals("token-default", inserted.getLeaseToken());
        assertEquals(NOW, inserted.getStartedAt());
        assertEquals(NOW, inserted.getUpdatedAt());
        verify(leaseScheduler).scheduleAtFixedRate(
                any(Runnable.class), eq(HEARTBEAT_INTERVAL));
    }

    @Test
    void acquireReadyTaskUsesNullToNewTokenCas() {
        AsyncTask pending = task("msg-pending", "PENDING");
        pending.setUpdatedAt(NOW.minusSeconds(10));
        when(asyncTaskMapper.selectOne(any())).thenReturn(pending);
        when(asyncTaskMapper.claimReadyTask(
                pending.getId(),
                "PENDING",
                null,
                "token-default",
                NOW,
                3,
                NOW)).thenReturn(1);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-pending"),
                "token-default",
                LEASE_DURATION)).thenReturn(true);

        assertTrue(service.acquire(message("msg-pending"), 3));
    }

    @Test
    void acquireReturnsFalseWhenAnotherConsumerWinsReadyTaskCas() {
        AsyncTask pending = task("msg-race", "PENDING");
        pending.setUpdatedAt(NOW.minusSeconds(10));
        when(asyncTaskMapper.selectOne(any())).thenReturn(pending);
        when(asyncTaskMapper.claimReadyTask(
                pending.getId(),
                "PENDING",
                null,
                "token-default",
                NOW,
                3,
                NOW)).thenReturn(0);

        assertFalse(service.acquire(message("msg-race"), 3));

        verify(valueOperations, never()).setIfAbsent(
                anyString(), anyString(), any(Duration.class));
        verify(leaseScheduler, never()).scheduleAtFixedRate(
                any(Runnable.class), any(Duration.class));
    }

    @Test
    void missingRedisKeyCannotStealUnexpiredDatabaseLease() {
        AsyncTask running = task("msg-active", "RUNNING");
        running.setLeaseToken("token-worker-a");
        running.setStartedAt(NOW.minusSeconds(5));
        when(asyncTaskMapper.selectOne(any())).thenReturn(running);

        assertFalse(service.acquire(message("msg-active"), 3));

        verify(asyncTaskMapper, never()).stealRunningTask(
                any(), anyString(), anyString(), any(LocalDateTime.class),
                any(LocalDateTime.class), anyInt(), any(LocalDateTime.class));
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).setIfAbsent(
                anyString(), anyString(), any(Duration.class));
    }

    @Test
    void acquireStealsExpiredRunningTaskWithOldToNewTokenCas() {
        AsyncTask running = task("msg-expired", "RUNNING");
        running.setLeaseToken("token-worker-a");
        running.setStartedAt(NOW.minusMinutes(6));
        when(asyncTaskMapper.selectOne(any())).thenReturn(running);
        when(asyncTaskMapper.stealRunningTask(
                running.getId(),
                "token-worker-a",
                "token-default",
                NOW,
                NOW.minus(LEASE_DURATION),
                3,
                NOW)).thenReturn(1);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-expired"),
                "token-default",
                LEASE_DURATION)).thenReturn(true);

        assertTrue(service.acquire(message("msg-expired"), 3));
    }

    @Test
    void expiredClaimReturnsFalseWhenOldTokenCasLoses() {
        AsyncTask running = task("msg-expired-race", "RUNNING");
        running.setLeaseToken("token-worker-a");
        running.setStartedAt(NOW.minusMinutes(6));
        when(asyncTaskMapper.selectOne(any())).thenReturn(running);
        when(asyncTaskMapper.stealRunningTask(
                running.getId(),
                "token-worker-a",
                "token-default",
                NOW,
                NOW.minus(LEASE_DURATION),
                3,
                NOW)).thenReturn(0);

        assertFalse(service.acquire(message("msg-expired-race"), 3));

        verify(valueOperations, never()).setIfAbsent(
                anyString(), anyString(), any(Duration.class));
    }

    @Test
    void sameSecondExpiredClaimsUseDistinctTokensAndOnlyOneWins() {
        AsyncTask firstSnapshot = task("msg-same-second", "RUNNING");
        firstSnapshot.setLeaseToken("token-old");
        firstSnapshot.setStartedAt(NOW.minusMinutes(6));
        AsyncTask secondSnapshot = copy(firstSnapshot);
        when(asyncTaskMapper.selectOne(any())).thenReturn(firstSnapshot, secondSnapshot);

        AtomicReference<String> databaseToken = new AtomicReference<>("token-old");
        List<String> proposedTokens = new ArrayList<>();
        List<LocalDateTime> proposedStarts = new ArrayList<>();
        when(asyncTaskMapper.stealRunningTask(
                eq(firstSnapshot.getId()),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                eq(NOW.minus(LEASE_DURATION)),
                eq(3),
                any(LocalDateTime.class))).thenAnswer(invocation -> {
                    String expectedToken = invocation.getArgument(1);
                    String newToken = invocation.getArgument(2);
                    proposedTokens.add(newToken);
                    proposedStarts.add(invocation.getArgument(3));
                    if (!databaseToken.get().equals(expectedToken)) {
                        return 0;
                    }
                    databaseToken.set(newToken);
                    return 1;
                });
        when(asyncTaskMapper.verifyLeaseOwner(anyString(), anyString()))
                .thenAnswer(invocation ->
                        databaseToken.get().equals(invocation.getArgument(1)) ? 1 : 0);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-same-second"),
                "token-worker-a",
                LEASE_DURATION)).thenReturn(true);

        AsyncTaskService workerA = newService(
                new TestLeaseRuntime(NOW, "token-worker-a"));
        AsyncTaskService workerB = newService(
                new TestLeaseRuntime(NOW, "token-worker-b"));

        assertTrue(workerA.acquire(message("msg-same-second"), 3));
        assertFalse(workerB.acquire(message("msg-same-second"), 3));

        assertEquals(List.of(NOW, NOW), proposedStarts);
        assertEquals(List.of("token-worker-a", "token-worker-b"), proposedTokens);
        assertNotEquals(proposedTokens.get(0), proposedTokens.get(1));
        assertEquals("token-worker-a", databaseToken.get());
    }

    @Test
    void redisClaimIsCompareDeletedWhenDatabaseTokenVerificationFails() {
        leaseRuntime = new TestLeaseRuntime(NOW, "token-verification-race");
        service = newService(leaseRuntime);
        when(asyncTaskMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-verification-race"),
                "token-verification-race",
                LEASE_DURATION)).thenReturn(true);
        when(asyncTaskMapper.verifyLeaseOwner(
                "msg-verification-race", "token-verification-race")).thenReturn(0);

        assertFalse(service.acquire(message("msg-verification-race"), 3));

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(Collections.singletonList(
                        RedisKeyConstants.mqConsumedKey("msg-verification-race"))),
                eq("token-verification-race"));
        verify(leaseScheduler, never()).scheduleAtFixedRate(
                any(Runnable.class), any(Duration.class));
    }

    @Test
    void oldWorkerCannotCompleteAfterNewTokenOwnsDatabaseRow() {
        leaseRuntime = new TestLeaseRuntime(NOW, "token-worker-a");
        service = newService(leaseRuntime);
        acquireNew("msg-stale-completion", 3, "token-worker-a");
        AtomicReference<String> databaseToken = new AtomicReference<>("token-worker-b");
        when(asyncTaskMapper.markSuccess(
                eq("msg-stale-completion"),
                anyString(),
                anyString(),
                eq(NOW))).thenAnswer(invocation ->
                        databaseToken.get().equals(invocation.getArgument(1)) ? 1 : 0);

        assertThrows(
                AsyncTaskService.TaskLeaseLostException.class,
                () -> service.markSuccess("msg-stale-completion", "result"));

        verify(asyncTaskMapper).markSuccess(
                "msg-stale-completion", "token-worker-a", "\"result\"", NOW);
        verify(heartbeat).cancel(false);
    }

    @Test
    void oldWorkerCannotRenewAfterNewTokenOwnsDatabaseRow() {
        leaseRuntime = new TestLeaseRuntime(NOW, "token-worker-a");
        service = newService(leaseRuntime);
        ArgumentCaptor<Runnable> heartbeatCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(heartbeat)
                .when(leaseScheduler)
                .scheduleAtFixedRate(heartbeatCaptor.capture(), eq(HEARTBEAT_INTERVAL));
        acquireNew("msg-heartbeat-race", 3, "token-worker-a");
        AtomicReference<String> databaseToken = new AtomicReference<>("token-worker-b");
        when(asyncTaskMapper.renewLease(
                eq("msg-heartbeat-race"),
                anyString(),
                eq(NOW))).thenAnswer(invocation ->
                        databaseToken.get().equals(invocation.getArgument(1)) ? 1 : 0);

        heartbeatCaptor.getValue().run();

        verify(asyncTaskMapper).renewLease(
                "msg-heartbeat-race", "token-worker-a", NOW);
        verify(heartbeat).cancel(false);
    }

    @Test
    void staleFailureCannotMoveTaskOrCreateDeadLetter() {
        acquireNew("msg-stale-failure", 3, "token-default");
        when(asyncTaskMapper.markRetryableFailed(
                eq("msg-stale-failure"),
                eq("token-default"),
                anyString(),
                eq(NOW))).thenReturn(0);
        when(asyncTaskMapper.markRetryExhaustedDead(
                eq("msg-stale-failure"),
                eq("token-default"),
                anyString(),
                eq(NOW))).thenReturn(0);

        assertThrows(
                AsyncTaskService.TaskLeaseLostException.class,
                () -> service.markFailed("msg-stale-failure", "upstream timeout"));

        verify(deadLetterMapper, never()).upsert(any(MessageDeadLetter.class));
    }

    @Test
    void retryableFailureReturnsTaskToPendingWithoutDeadLetter() {
        acquireNew("msg-retryable", 3, "token-default");
        when(asyncTaskMapper.markRetryableFailed(
                eq("msg-retryable"),
                eq("token-default"),
                anyString(),
                eq(NOW))).thenReturn(1);

        service.markFailed("msg-retryable", "upstream timeout");

        verify(asyncTaskMapper, never()).markRetryExhaustedDead(
                anyString(), anyString(), anyString(), any(LocalDateTime.class));
        verify(deadLetterMapper, never()).upsert(any(MessageDeadLetter.class));
        verify(heartbeat).cancel(false);
    }

    @Test
    void exhaustedRetryAtomicallyMovesTaskToDeadAndUpsertsDeadLetter() {
        AsyncTask pending = task("msg-exhausted", "PENDING");
        pending.setRetryCount(3);
        pending.setMaxRetry(3);
        pending.setUpdatedAt(NOW.minusSeconds(10));
        when(asyncTaskMapper.selectOne(any())).thenReturn(pending);
        when(asyncTaskMapper.claimReadyTask(
                pending.getId(),
                "PENDING",
                null,
                "token-default",
                NOW,
                3,
                NOW)).thenReturn(1);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey("msg-exhausted"),
                "token-default",
                LEASE_DURATION)).thenReturn(true);
        assertTrue(service.acquire(message("msg-exhausted"), 3));
        when(asyncTaskMapper.markRetryableFailed(
                eq("msg-exhausted"),
                eq("token-default"),
                anyString(),
                eq(NOW))).thenReturn(0);
        when(asyncTaskMapper.markRetryExhaustedDead(
                eq("msg-exhausted"),
                eq("token-default"),
                anyString(),
                eq(NOW))).thenReturn(1);
        when(deadLetterMapper.upsert(any(MessageDeadLetter.class))).thenReturn(1);

        service.markFailed("msg-exhausted", "still unavailable");

        ArgumentCaptor<MessageDeadLetter> deadLetterCaptor =
                ArgumentCaptor.forClass(MessageDeadLetter.class);
        verify(deadLetterMapper).upsert(deadLetterCaptor.capture());
        assertEquals("msg-exhausted", deadLetterCaptor.getValue().getMessageId());
        assertEquals(3, deadLetterCaptor.getValue().getTotalRetry());
        assertEquals("UNHANDLED", deadLetterCaptor.getValue().getHandleStatus());
    }

    @Test
    void deadLetterWriteFailureAbandonsCurrentToken() {
        MqMessage<String> message = message("msg-dead-write-fails");
        acquireNew(message.getMessageId(), 3, "token-default");
        when(asyncTaskMapper.markDead(
                eq(message.getMessageId()),
                eq("token-default"),
                anyString(),
                eq(NOW))).thenReturn(1);
        when(deadLetterMapper.upsert(any(MessageDeadLetter.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThrows(RuntimeException.class, () -> service.markDead(message, "invalid payload"));

        verify(heartbeat).cancel(false);
    }

    @Test
    void manualRetryPreparationRequiresNullLeaseToken() {
        when(asyncTaskMapper.prepareManualRetry(10L, null, NOW)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.prepareManualRetry(10L, "msg-manual"));

        verify(asyncTaskMapper).prepareManualRetry(10L, null, NOW);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void manualDispatchFailureRequiresPendingTaskWithNullLeaseToken() {
        when(asyncTaskMapper.markManualRetryDispatchFailed(
                eq(10L), isNull(), anyString(), eq(NOW))).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> service.markManualRetryDispatchFailed(10L, "send failed"));

        verify(asyncTaskMapper).markManualRetryDispatchFailed(
                eq(10L), isNull(), anyString(), eq(NOW));
    }

    private AsyncTaskService newService(AsyncTaskLeaseRuntime runtime) {
        return new AsyncTaskService(
                asyncTaskMapper,
                deadLetterMapper,
                redisTemplate,
                new ObjectMapper(),
                leaseScheduler,
                leaseProperties,
                runtime);
    }

    private void acquireNew(String messageId, int maxRetry, String token) {
        when(asyncTaskMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.setIfAbsent(
                RedisKeyConstants.mqConsumedKey(messageId),
                token,
                LEASE_DURATION)).thenReturn(true);
        assertTrue(service.acquire(message(messageId), maxRetry));
    }

    private AsyncTask task(String messageId, String status) {
        AsyncTask task = new AsyncTask();
        task.setId(100L);
        task.setMessageId(messageId);
        task.setBizType("resume.parse");
        task.setBizId("resume-1");
        task.setUserId(10L);
        task.setTraceId("trace-1");
        task.setPayload("\"payload\"");
        task.setStatus(status);
        task.setRetryCount(0);
        task.setMaxRetry(3);
        if ("RUNNING".equals(status)) {
            task.setLeaseToken("token-existing");
        }
        return task;
    }

    private AsyncTask copy(AsyncTask source) {
        AsyncTask copy = task(source.getMessageId(), source.getStatus());
        copy.setId(source.getId());
        copy.setLeaseToken(source.getLeaseToken());
        copy.setStartedAt(source.getStartedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setRetryCount(source.getRetryCount());
        copy.setMaxRetry(source.getMaxRetry());
        return copy;
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

    private static final class TestLeaseRuntime implements AsyncTaskLeaseRuntime {

        private LocalDateTime now;
        private final Deque<String> tokens = new ArrayDeque<>();

        private TestLeaseRuntime(LocalDateTime now, String... tokens) {
            this.now = now;
            Collections.addAll(this.tokens, tokens);
        }

        @Override
        public LocalDateTime now() {
            return now;
        }

        @Override
        public String newLeaseToken() {
            String token = tokens.pollFirst();
            assertNotNull(token, "No deterministic lease token remains");
            return token;
        }
    }
}
