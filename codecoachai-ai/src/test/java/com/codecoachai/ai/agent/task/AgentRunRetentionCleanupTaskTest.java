package com.codecoachai.ai.agent.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class AgentRunRetentionCleanupTaskTest {

    @Test
    void cleanupTaskScrubsHistoricalAgentRunDiagnosticsButPreservesSummaryProjection() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(List.of(Map.of(
                "runId", 88L,
                "outputJson", """
                        {"summary":"今日计划","focusSkills":[{"code":"JAVA","name":"Java"}],"tasks":[{"title":"联系张三","description":"联系 13812345678","reason":"发送到 zhangsan@example.com"}]}
                        """
        )));
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setInt(task, "scanLimit", 20);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        LocalDateTime beforeLowerBound = LocalDateTime.now().minusDays(31);
        invoke(task, "cleanupExpiredDiagnostics");
        LocalDateTime afterUpperBound = LocalDateTime.now().minusDays(29);

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(querySqlCaptor.capture(), queryArgsCaptor.capture(), any(int[].class));
        String querySql = querySqlCaptor.getValue();
        assertTrue(querySql.contains("FROM agent_run"));
        assertTrue(querySql.contains("finished_at <= ?"));
        assertTrue(querySql.contains("status IN ('SUCCESS', 'FAILED', 'CANCELED')"));
        assertTrue(querySql.contains("input_snapshot_json"));
        assertTrue(querySql.contains("output_json"));
        assertTrue(querySql.contains("raw_output_text"));
        assertTrue(querySql.contains("LIMIT ?"));

        Object[] queryArgs = queryArgsCaptor.getValue();
        assertEquals(2, queryArgs.length);
        Timestamp cutoff = (Timestamp) queryArgs[0];
        assertTrue(cutoff.toLocalDateTime().isAfter(beforeLowerBound));
        assertTrue(cutoff.toLocalDateTime().isBefore(afterUpperBound));
        assertEquals(20, queryArgs[1]);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        String updateSql = updateSqlCaptor.getValue();
        assertTrue(updateSql.contains("UPDATE agent_run"));
        assertTrue(updateSql.contains("input_snapshot_json = NULL"));
        assertTrue(updateSql.contains("output_json = ?"));
        assertTrue(updateSql.contains("raw_output_text = NULL"));
        assertFalse(updateSql.contains("error_message = NULL"));
        assertFalse(updateSql.contains("trace_id = NULL"));
        assertTrue(updateSql.contains("status IN ('SUCCESS', 'FAILED', 'CANCELED')"));

        Object[] updateArgs = updateArgsCaptor.getValue();
        assertEquals(3, updateArgs.length);
        String minimizedOutputJson = (String) updateArgs[0];
        assertTrue(minimizedOutputJson.contains("\"summary\":\"今日计划\""));
        assertTrue(minimizedOutputJson.contains("\"focusSkills\""));
        assertTrue(minimizedOutputJson.contains("\"tasks\":[]"));
        assertFalse(minimizedOutputJson.contains("13812345678"));
        assertFalse(minimizedOutputJson.contains("zhangsan@example.com"));
        assertFalse(minimizedOutputJson.contains("联系张三"));
        assertEquals(88L, updateArgs[2]);
    }

    @Test
    void cleanupTaskDoesNotQueryOrUpdateWhenLockIsNotAcquired() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);

        invoke(task, "cleanupExpiredDiagnostics");

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class), any(int[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
        verifyNoInteractions(jdbcTemplate);
    }

    private static Object newTask(JdbcTemplate jdbcTemplate,
                                  DistributedLockHelper lockHelper) throws Exception {
        Class<?> type = Class.forName("com.codecoachai.ai.agent.task.AgentRunRetentionCleanupTask");
        return type.getDeclaredConstructor(JdbcTemplate.class, DistributedLockHelper.class, ObjectMapper.class)
                .newInstance(jdbcTemplate, lockHelper, new ObjectMapper());
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
