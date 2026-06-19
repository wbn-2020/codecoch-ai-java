package com.codecoachai.common.web.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AutoOperationLogFilterTest {

    @Test
    void auditQueueSaturationDoesNotFailRequestThread() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        AutoOperationLogFilter filter = new AutoOperationLogFilter(jdbcTemplate);
        ExecutorService executor = extractExecutor(filter);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
        CountDownLatch release = new CountDownLatch(1);
        int queueCapacity = pool.getQueue().remainingCapacity();
        assertTrue(queueCapacity > 0 && queueCapacity <= 1024, "audit executor queue must be bounded");
        int saturationTasks = pool.getMaximumPoolSize() + queueCapacity;
        for (int i = 0; i < saturationTasks; i++) {
            pool.execute(() -> await(release));
        }

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/questions");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (req, res) -> response.setStatus(200);

            assertDoesNotThrow(() -> filter.doFilter(request, response, chain));
        } finally {
            release.countDown();
            filter.shutdown();
        }
    }

    private ExecutorService extractExecutor(AutoOperationLogFilter filter) throws Exception {
        Field field = AutoOperationLogFilter.class.getDeclaredField("logExecutor");
        field.setAccessible(true);
        return (ExecutorService) field.get(filter);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
