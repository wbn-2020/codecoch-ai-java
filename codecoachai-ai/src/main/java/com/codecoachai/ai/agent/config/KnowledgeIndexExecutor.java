package com.codecoachai.ai.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public KnowledgeIndexExecutor(
            @Qualifier(KnowledgeIndexExecutorConfig.KNOWLEDGE_INDEX_TASK_EXECUTOR)
            ThreadPoolTaskExecutor executor) {
        this.executor = executor;
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
