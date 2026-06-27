package com.codecoachai.ai.agent.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentDailyPlanScheduleTaskTest {

    @Test
    void runDailyPlanBatchSkipsConcurrentSchedulerInvocation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean locked = new AtomicBoolean(false);
        AgentV4OpsService service = mock(AgentV4OpsService.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            if (!locked.compareAndSet(false, true)) {
                return false;
            }
            try {
                Runnable task = invocation.getArgument(3);
                task.run();
                return true;
            } finally {
                locked.set(false);
            }
        });
        when(service.runDailyPlanBatch(any(AnalyticsJobRunDTO.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return new AnalyticsJobLogVO();
        });
        AgentDailyPlanScheduleTask task = new AgentDailyPlanScheduleTask(service, lockHelper);
        setEnabled(task, true);

        Thread first = new Thread(task::runDailyPlanBatch);
        Thread second = new Thread(task::runDailyPlanBatch);
        first.start();
        assertEquals(true, entered.await(3, TimeUnit.SECONDS), "first scheduler invocation should start");
        second.start();
        Thread.sleep(200L);
        release.countDown();
        first.join(3000L);
        second.join(3000L);

        assertEquals(1, calls.get(), "concurrent scheduler invocation must be skipped");
    }

    @Test
    void runDailyPlanBatchUsesConfiguredLockAndUserLimit() throws Exception {
        AgentV4OpsService service = mock(AgentV4OpsService.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(3);
            task.run();
            return true;
        });
        when(service.runDailyPlanBatch(any(AnalyticsJobRunDTO.class))).thenReturn(new AnalyticsJobLogVO());
        AgentDailyPlanScheduleTask task = new AgentDailyPlanScheduleTask(service, lockHelper);
        setEnabled(task, true);
        setLong(task, "lockWaitSeconds", 2L);
        setLong(task, "lockLeaseSeconds", 900L);
        setInt(task, "batchUserLimit", 120);

        task.runDailyPlanBatch();

        verify(lockHelper).tryLockAndRun(anyString(), eq(2L), eq(900L), any(Runnable.class));
        ArgumentCaptor<AnalyticsJobRunDTO> captor = ArgumentCaptor.forClass(AnalyticsJobRunDTO.class);
        verify(service).runDailyPlanBatch(captor.capture());
        Assertions.assertEquals(120, captor.getValue().getUserLimit());
    }

    @Test
    void runDailyPlanBatchDoesNotRunServiceWhenLockIsNotAcquired() throws Exception {
        AgentV4OpsService service = mock(AgentV4OpsService.class);
        DistributedLockHelper lockHelper = mock(DistributedLockHelper.class);
        when(lockHelper.tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class))).thenReturn(false);
        AgentDailyPlanScheduleTask task = new AgentDailyPlanScheduleTask(service, lockHelper);
        setEnabled(task, true);

        task.runDailyPlanBatch();

        verify(lockHelper).tryLockAndRun(anyString(), anyLong(), anyLong(), any(Runnable.class));
        verify(service, org.mockito.Mockito.never()).runDailyPlanBatch(any(AnalyticsJobRunDTO.class));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void setEnabled(AgentDailyPlanScheduleTask task, boolean enabled) throws Exception {
        var field = AgentDailyPlanScheduleTask.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.setBoolean(task, enabled);
    }

    private static void setLong(AgentDailyPlanScheduleTask task, String fieldName, long value) throws Exception {
        var field = AgentDailyPlanScheduleTask.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(task, value);
    }

    private static void setInt(AgentDailyPlanScheduleTask task, String fieldName, int value) throws Exception {
        var field = AgentDailyPlanScheduleTask.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(task, value);
    }
}
