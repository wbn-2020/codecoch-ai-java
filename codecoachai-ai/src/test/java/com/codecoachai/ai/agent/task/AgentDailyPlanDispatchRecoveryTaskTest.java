package com.codecoachai.ai.agent.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentDailyPlanDispatchRecoveryTaskTest {

    @Test
    void recoveryTaskDispatchesRunningRunsWithoutAsyncTaskMarker() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JobCoachAgentService jobCoachAgentService = mock(JobCoachAgentService.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return true;
        });
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class), any(int[].class))).thenReturn(List.of(Map.of(
                "runId", 88L,
                "userId", 100L,
                "targetJobId", 501L,
                "planDate", Date.valueOf(LocalDate.of(2026, 6, 23)),
                "executionToken", "token-88"
        )));

        Object task = newTask(jdbcTemplate, jobCoachAgentService, lockHelper);
        setBoolean(task, "enabled", true);
        setLong(task, "graceMinutes", 2L);
        setInt(task, "scanLimit", 20);
        setLong(task, "lockWaitSeconds", 0L);
        setLong(task, "lockLeaseSeconds", 300L);

        invoke(task, "recoverMissingDispatches");

        ArgumentCaptor<DailyPlanGenerateDTO> dtoCaptor = ArgumentCaptor.forClass(DailyPlanGenerateDTO.class);
        verify(jobCoachAgentService).executeDailyPlan(eq(100L), eq(88L), dtoCaptor.capture());
        assertEquals(LocalDate.of(2026, 6, 23), dtoCaptor.getValue().getDate());
        assertEquals(501L, dtoCaptor.getValue().getTargetJobId());
        assertEquals("token-88", dtoCaptor.getValue().getExecutionToken());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class), any(int[].class));
        assertTrue(sqlCaptor.getValue().contains("async_task"));
        assertTrue(sqlCaptor.getValue().contains("agent.daily-plan.generate"));
        assertTrue(sqlCaptor.getValue().contains("NOT EXISTS"));
    }

    @Test
    void recoveryTaskDoesNotQueryOrDispatchWhenLockIsNotAcquired() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JobCoachAgentService jobCoachAgentService = mock(JobCoachAgentService.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);

        Object task = newTask(jdbcTemplate, jobCoachAgentService, lockHelper);
        setBoolean(task, "enabled", true);

        invoke(task, "recoverMissingDispatches");

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class), any(int[].class));
        verifyNoInteractions(jobCoachAgentService);
    }

    private static Object newTask(JdbcTemplate jdbcTemplate,
                                  JobCoachAgentService jobCoachAgentService,
                                  DistributedLockHelper lockHelper) throws Exception {
        Class<?> type = Class.forName("com.codecoachai.ai.agent.task.AgentDailyPlanDispatchRecoveryTask");
        return type.getDeclaredConstructor(JdbcTemplate.class, JobCoachAgentService.class, DistributedLockHelper.class)
                .newInstance(jdbcTemplate, jobCoachAgentService, lockHelper);
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
