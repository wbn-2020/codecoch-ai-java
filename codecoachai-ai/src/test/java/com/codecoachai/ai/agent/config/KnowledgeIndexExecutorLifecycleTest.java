package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class KnowledgeIndexExecutorLifecycleTest {

    @Test
    void springContextOwnsInitializationAndShutdown() throws InterruptedException {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(KnowledgeIndexExecutorConfig.class, KnowledgeIndexExecutor.class);
        context.refresh();

        ThreadPoolTaskExecutor taskExecutor = context.getBean(
                KnowledgeIndexExecutorConfig.KNOWLEDGE_INDEX_TASK_EXECUTOR,
                ThreadPoolTaskExecutor.class);
        try {
            KnowledgeIndexExecutor knowledgeIndexExecutor = context.getBean(KnowledgeIndexExecutor.class);
            CountDownLatch executed = new CountDownLatch(1);

            knowledgeIndexExecutor.submit(executed::countDown);
            assertTrue(executed.await(
                    Duration.ofSeconds(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
        } finally {
            context.close();
        }
        assertTrue(taskExecutor.getThreadPoolExecutor().isShutdown());
    }
}
