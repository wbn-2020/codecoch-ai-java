package com.codecoachai.common.redis.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 分布式锁工具类。
 * 推荐场景：
 *   - 面试报告生成（lock:report:{sessionId}）
 *   - 简历解析串行（lock:resume:parse:{resumeId}）
 *   - 学习计划生成（lock:plan:gen:{userId}）
 *
 * 默认行为：
 *   - waitTime = 0 表示不等待，未抢到立即失败
 *   - leaseTime 自动续期由 Redisson 看门狗机制保障（默认 30s 续期）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockHelper {

    private final RedissonClient redissonClient;

    /**
     * 尝试以指定时间获取锁并执行任务（不会等待）。
     * 适合用于"如果已经有人在跑就直接跳过"的场景。
     *
     * @param lockKey 锁 key
     * @param leaseTime 持有时间（秒），到期自动释放；-1 表示交给看门狗
     * @param task 任务
     * @return true=拿到锁并执行了 false=没拿到
     */
    public boolean tryLockAndRun(String lockKey, long leaseTime, Runnable task) {
        return tryLockAndRun(lockKey, 0, leaseTime, task);
    }

    /**
     * 尝试在 waitTime 内获取锁并执行。
     */
    public boolean tryLockAndRun(String lockKey, long waitTimeSeconds, long leaseTimeSeconds, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("Lock not acquired: {}", lockKey);
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Lock interrupted: {}", lockKey, ex);
            return false;
        }
    }

    /**
     * 带返回值版本。
     */
    public <T> T tryLockAndCall(String lockKey, long waitTimeSeconds, long leaseTimeSeconds,
                                Supplier<T> supplier, Supplier<T> onFail) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                return onFail != null ? onFail.get() : null;
            }
            try {
                return supplier.get();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return onFail != null ? onFail.get() : null;
        }
    }

    /**
     * 阻塞获取锁（带超时），无法获取则抛业务异常。
     */
    public RLock lockOrThrow(String lockKey, long waitTimeSeconds, long leaseTimeSeconds) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("Failed to acquire lock: " + lockKey);
            }
            return lock;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock interrupted: " + lockKey, ex);
        }
    }
}
