package com.codecoachai.ai.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

class AiCallLogRetentionCleanupTaskTest {

    @Test
    void cleanupTaskNullsHistoricalRawFieldsButPreservesOperationalMetadata() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(List.of(
                Map.of("id", 101L),
                Map.of("id", 102L)
        ));
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(2);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setLong(task, "hardDeleteDays", 0L);
        setInt(task, "scanLimit", 20);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        LocalDateTime beforeLowerBound = LocalDateTime.now().minusDays(31);
        invoke(task, "cleanupExpiredRawFields");
        LocalDateTime afterUpperBound = LocalDateTime.now().minusDays(29);

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(querySqlCaptor.capture(), queryArgsCaptor.capture(), any(int[].class));
        String querySql = querySqlCaptor.getValue();
        assertTrue(querySql.contains("FROM ai_call_log"));
        assertTrue(querySql.contains("created_at <= ?"));
        assertTrue(querySql.contains("input_variables_json"));
        assertTrue(querySql.contains("model_params_json"));
        assertTrue(querySql.contains("request_prompt"));
        assertTrue(querySql.contains("response_content"));
        assertTrue(querySql.contains("request_body"));
        assertTrue(querySql.contains("response_body"));
        assertTrue(querySql.contains("LIMIT ?"));

        Object[] queryArgs = queryArgsCaptor.getValue();
        assertEquals(2, queryArgs.length);
        Timestamp cutoff = assertInstanceOf(Timestamp.class, queryArgs[0]);
        assertTrue(cutoff.toLocalDateTime().isAfter(beforeLowerBound));
        assertTrue(cutoff.toLocalDateTime().isBefore(afterUpperBound));
        assertEquals(20, queryArgs[1]);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        String updateSql = updateSqlCaptor.getValue();
        assertTrue(updateSql.contains("UPDATE ai_call_log"));
        assertTrue(updateSql.contains("input_variables_json = NULL"));
        assertTrue(updateSql.contains("model_params_json = NULL"));
        assertTrue(updateSql.contains("request_prompt = NULL"));
        assertTrue(updateSql.contains("response_content = NULL"));
        assertTrue(updateSql.contains("request_body = NULL"));
        assertTrue(updateSql.contains("response_body = NULL"));
        assertFalse(updateSql.contains("error_message = NULL"));
        assertFalse(updateSql.contains("route_trace = NULL"));
        assertTrue(updateSql.contains("id IN (?, ?)"));

        Object[] updateArgs = updateArgsCaptor.getValue();
        assertEquals(2, updateArgs.length);
        assertEquals(101L, updateArgs[0]);
        assertEquals(102L, updateArgs[1]);
    }

    @Test
    void cleanupTaskHardDeletesDeeplyExpiredRowsAfterRawFieldsHaveBeenScrubbed() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(
                List.of(Map.of("id", 101L)),
                List.of(Map.of("id", 201L), Map.of("id", 202L))
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1, 2);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setLong(task, "hardDeleteDays", 180L);
        setInt(task, "scanLimit", 20);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        LocalDateTime beforeScrubLowerBound = LocalDateTime.now().minusDays(31);
        LocalDateTime beforeDeleteLowerBound = LocalDateTime.now().minusDays(181);
        invoke(task, "cleanupExpiredRawFields");
        LocalDateTime afterScrubUpperBound = LocalDateTime.now().minusDays(29);
        LocalDateTime afterDeleteUpperBound = LocalDateTime.now().minusDays(179);

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).queryForList(querySqlCaptor.capture(), queryArgsCaptor.capture(), any(int[].class));
        List<String> querySqls = querySqlCaptor.getAllValues();
        assertEquals(2, querySqls.size());
        assertTrue(querySqls.get(0).contains("input_variables_json"));
        assertTrue(querySqls.get(0).contains("response_body"));
        assertTrue(querySqls.get(1).contains("FROM ai_call_log"));
        assertTrue(querySqls.get(1).contains("created_at <= ?"));
        assertTrue(querySqls.get(1).contains("input_variables_json IS NULL"));
        assertTrue(querySqls.get(1).contains("response_body IS NULL"));

        List<Object[]> queryArgs = queryArgsCaptor.getAllValues();
        Timestamp scrubCutoff = assertInstanceOf(Timestamp.class, queryArgs.get(0)[0]);
        assertTrue(scrubCutoff.toLocalDateTime().isAfter(beforeScrubLowerBound));
        assertTrue(scrubCutoff.toLocalDateTime().isBefore(afterScrubUpperBound));
        assertEquals(20, queryArgs.get(0)[1]);

        Timestamp deleteCutoff = assertInstanceOf(Timestamp.class, queryArgs.get(1)[0]);
        assertTrue(deleteCutoff.toLocalDateTime().isAfter(beforeDeleteLowerBound));
        assertTrue(deleteCutoff.toLocalDateTime().isBefore(afterDeleteUpperBound));
        assertEquals(20, queryArgs.get(1)[1]);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        List<String> updateSqls = updateSqlCaptor.getAllValues();
        assertEquals(2, updateSqls.size());
        assertTrue(updateSqls.get(0).contains("UPDATE ai_call_log"));
        assertTrue(updateSqls.get(1).contains("DELETE FROM ai_call_log"));
        assertTrue(updateSqls.get(1).contains("id IN (?, ?)"));

        List<Object[]> updateArgs = updateArgsCaptor.getAllValues();
        Object[] deleteArgs = updateArgs.get(1);
        assertEquals(2, deleteArgs.length);
        assertEquals(201L, deleteArgs[0]);
        assertEquals(202L, deleteArgs[1]);
    }

    @Test
    void cleanupTaskDoesNotQueryOrUpdateWhenLockIsNotAcquired() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);

        invoke(task, "cleanupExpiredRawFields");

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class), any(int[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
        verifyNoInteractions(jdbcTemplate);
    }

    private static Object newTask(JdbcTemplate jdbcTemplate,
                                  DistributedLockHelper lockHelper) throws Exception {
        Class<?> type = Class.forName("com.codecoachai.ai.task.AiCallLogRetentionCleanupTask");
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
