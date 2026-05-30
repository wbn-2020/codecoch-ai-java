package com.codecoachai.ai.agent.config;

import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 个人知识库向量索引专用线程池。
 * <p>用于将向量 upsert / 近重统计移出请求线程，避免上传大文档时阻塞用户。
 * 队列满时回退由调用线程执行（CallerRunsPolicy），保证任务不丢失。
 */
@Slf4j
@Component
public class KnowledgeIndexExecutor {

    private final ThreadPoolTaskExecutor executor;

    public KnowledgeIndexExecutor() {
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setCorePoolSize(2);
        this.executor.setMaxPoolSize(4);
        this.executor.setQueueCapacity(256);
        this.executor.setThreadNamePrefix("knowledge-index-");
        this.executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        this.executor.setWaitForTasksToCompleteOnShutdown(true);
        this.executor.setAwaitTerminationSeconds(30);
        this.executor.initialize();
    }

    /**
     * 提交一个索引任务到专用线程池。任务内部异常需自行捕获，这里仅兜底记录。
     */
    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (Exception ex) {
            log.warn("Knowledge index task submit failed, fallback to inline run", ex);
            task.run();
        }
    }
}
