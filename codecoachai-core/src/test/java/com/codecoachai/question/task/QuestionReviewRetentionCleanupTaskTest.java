package com.codecoachai.question.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

class QuestionReviewRetentionCleanupTaskTest {

    @Test
    void cleanupTaskNullsHistoricalTerminalReviewRawPayloadButLeavesPendingReviewsUntouched() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(List.of(
                Map.of("id", 301L),
                Map.of("id", 302L)
        ));
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(2);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setLong(task, "pendingRetentionDays", 0L);
        setInt(task, "scanLimit", 25);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        LocalDateTime beforeLowerBound = LocalDateTime.now().minusDays(31);
        invoke(task, "cleanupExpiredRawFields");
        LocalDateTime afterUpperBound = LocalDateTime.now().minusDays(29);

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(querySqlCaptor.capture(), queryArgsCaptor.capture(), any(int[].class));
        String querySql = querySqlCaptor.getValue();
        assertTrue(querySql.contains("FROM question_review"));
        assertTrue(querySql.contains("updated_at <= ?"));
        assertTrue(querySql.contains("raw_ai_result_json"));
        assertTrue(querySql.contains("review_status IN"));
        assertFalse(querySql.contains("PENDING"));
        assertTrue(querySql.contains("LIMIT ?"));

        Object[] queryArgs = queryArgsCaptor.getValue();
        assertEquals(5, queryArgs.length);
        Timestamp cutoff = assertInstanceOf(Timestamp.class, queryArgs[0]);
        assertTrue(cutoff.toLocalDateTime().isAfter(beforeLowerBound));
        assertTrue(cutoff.toLocalDateTime().isBefore(afterUpperBound));
        assertEquals("APPROVED", queryArgs[1]);
        assertEquals("REJECTED", queryArgs[2]);
        assertEquals("CANCELLED", queryArgs[3]);
        assertEquals(25, queryArgs[4]);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        String updateSql = updateSqlCaptor.getValue();
        assertTrue(updateSql.contains("UPDATE question_review"));
        assertTrue(updateSql.contains("raw_ai_result_json = NULL"));
        assertFalse(updateSql.contains("question_content = NULL"));
        assertFalse(updateSql.contains("review_status ="));
        assertTrue(updateSql.contains("id IN (?, ?)"));

        Object[] updateArgs = updateArgsCaptor.getValue();
        assertEquals(2, updateArgs.length);
        assertEquals(301L, updateArgs[0]);
        assertEquals(302L, updateArgs[1]);
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

    @Test
    void cleanupTaskAlsoScrubsStalePendingReviewRawPayloadsByCreatedAtAge() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(
                List.of(),
                List.of(Map.of("id", 401L))
        );
        when(jdbcTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);

        Object task = newTask(jdbcTemplate, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "retentionDays", 30L);
        setLong(task, "pendingRetentionDays", 7L);
        setInt(task, "scanLimit", 25);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        LocalDateTime beforePendingLowerBound = LocalDateTime.now().minusDays(8);
        invoke(task, "cleanupExpiredRawFields");
        LocalDateTime afterPendingUpperBound = LocalDateTime.now().minusDays(6);

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).queryForList(querySqlCaptor.capture(), queryArgsCaptor.capture(), any(int[].class));

        List<String> querySqls = querySqlCaptor.getAllValues();
        assertEquals(2, querySqls.size());
        assertTrue(querySqls.get(0).contains("updated_at <= ?"));
        assertTrue(querySqls.get(0).contains("review_status IN"));
        assertTrue(querySqls.get(1).contains("created_at <= ?"));
        assertTrue(querySqls.get(1).contains("review_status = ?"));
        assertTrue(querySqls.get(1).contains("raw_ai_result_json"));

        List<Object[]> queryArgs = queryArgsCaptor.getAllValues();
        assertEquals(3, queryArgs.get(1).length);
        Timestamp pendingCutoff = assertInstanceOf(Timestamp.class, queryArgs.get(1)[0]);
        assertTrue(pendingCutoff.toLocalDateTime().isAfter(beforePendingLowerBound));
        assertTrue(pendingCutoff.toLocalDateTime().isBefore(afterPendingUpperBound));
        assertEquals("PENDING", queryArgs.get(1)[1]);
        assertEquals(25, queryArgs.get(1)[2]);

        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> updateArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(updateSqlCaptor.capture(), updateArgsCaptor.capture(), any(int[].class));
        assertTrue(updateSqlCaptor.getValue().contains("UPDATE question_review"));
        assertTrue(updateSqlCaptor.getValue().contains("raw_ai_result_json = NULL"));
        Object[] updateArgs = updateArgsCaptor.getValue();
        assertEquals(1, updateArgs.length);
        assertEquals(401L, updateArgs[0]);
    }

    private static Object newTask(JdbcTemplate jdbcTemplate,
                                  DistributedLockHelper lockHelper) throws Exception {
        Class<?> type = Class.forName("com.codecoachai.question.task.QuestionReviewRetentionCleanupTask");
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
