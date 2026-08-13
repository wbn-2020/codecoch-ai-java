package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.core.config.CoreAsyncConfig;
import com.codecoachai.core.config.CoreSchedulingConfig;
import com.codecoachai.task.config.AsyncTaskLeaseConfig;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class CoreAsyncSchedulingContractTest {

    @Test
    void commonAsyncExecutorIsBoundedAndNamedForLoginLogs() throws Exception {
        Method method = CoreAsyncConfig.class.getDeclaredMethod("commonAsyncExecutor");
        Bean bean = method.getAnnotation(Bean.class);
        assertNotNull(bean);
        assertEquals("commonAsyncExecutor", bean.name()[0]);

        ThreadPoolTaskExecutor executor = new CoreAsyncConfig().commonAsyncExecutor();
        try {
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(4, executor.getMaxPoolSize());
            assertEquals(100, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertTrue(executor.getThreadNamePrefix().startsWith("common-async-"));
            assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                    instanceof ThreadPoolExecutor.CallerRunsPolicy);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void ordinarySchedulerIsSeparateFromLeaseScheduler() throws Exception {
        Method schedulerMethod = CoreSchedulingConfig.class.getDeclaredMethod("taskScheduler");
        Bean schedulerBean = schedulerMethod.getAnnotation(Bean.class);
        assertNotNull(schedulerBean);
        assertEquals("taskScheduler", schedulerBean.name()[0]);

        ThreadPoolTaskScheduler scheduler = new CoreSchedulingConfig().taskScheduler();
        ThreadPoolTaskScheduler leaseScheduler = new AsyncTaskLeaseConfig().asyncTaskLeaseScheduler();
        leaseScheduler.initialize();
        try {
            assertNotSame(scheduler, leaseScheduler);
            assertEquals(2, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
            assertEquals(1, leaseScheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
            assertTrue(scheduler.getThreadNamePrefix().startsWith("core-scheduler-"));
            assertTrue(leaseScheduler.getThreadNamePrefix().startsWith("async-task-lease-"));
        } finally {
            scheduler.shutdown();
            leaseScheduler.shutdown();
        }
    }
}
