package com.codecoachai.ai.agent.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentDailyPlanTimeoutRecoveryTaskTest {

    @Test
    void recoveryTaskMarksTimedOutRunningRunsWithAsyncTaskMarkerAsFailed() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(List.of(Map.of(
                "runId", 88L,
                "userId", 100L,
                "startedAt", Timestamp.valueOf(LocalDateTime.of(2026, 6, 23, 8, 30)),
                "executionToken", "token-88"
        )));
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "staleMinutes", 15L);
        setInt(task, "scanLimit", 20);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        invoke(task, "recoverTimedOutRuns");

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(querySqlCaptor.capture(), any(Object[].class), any(int[].class));
        assertTrue(querySqlCaptor.getValue().contains("EXISTS"));
        assertTrue(querySqlCaptor.getValue().contains("async_task"));
        assertTrue(querySqlCaptor.getValue().contains("agent.daily-plan.generate"));

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        assertTrue(updateSqlCaptor.getValue().contains("UPDATE agent_run"));
        assertTrue(updateSqlCaptor.getValue().contains("execution_token"));
        assertTrue(updateSqlCaptor.getValue().contains("RUN_TIMEOUT"));
        assertTrue(updateSqlCaptor.getValue().contains("RUNNING"));

        Object[] args = updateArgsCaptor.getValue();
        assertEquals("计划生成超时，请重新生成今日计划。", args[0]);
        assertEquals(88L, args[4]);
        assertEquals(100L, args[5]);
        assertEquals("token-88", args[6]);
    }

    @Test
    void recoveryTaskDoesNotQueryOrUpdateWhenLockIsNotAcquired() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);

        invoke(task, "recoverTimedOutRuns");

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class), any(int[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
        verifyNoInteractions(jdbcTemplate);
    }

    private static Object newTask(JdbcTemplate jdbcTemplate,
                                  DistributedLockHelper lockHelper) throws Exception {
        Class<?> type = Class.forName("com.codecoachai.ai.agent.task.AgentDailyPlanTimeoutRecoveryTask");
        return type.getDeclaredConstructor(JdbcTemplate.class, DistributedLockHelper.class)
                .newInstance(jdbcTemplate, lockHelper);
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void setBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setLong(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static void setInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
