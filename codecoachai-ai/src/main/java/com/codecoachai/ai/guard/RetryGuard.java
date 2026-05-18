package com.codecoachai.ai.guard;

import com.codecoachai.ai.client.AiProviderException;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.enums.AiFailureType;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 重试守卫：指数退避 + 选择性重试。
 *
 * 重试场景：
 *   - TIMEOUT（网络/读取超时）
 *   - HTTP_ERROR 且状态码 >= 500 或 429（限流）
 *   - EMPTY_RESPONSE（偶发空响应）
 *
 * 不重试：CONFIG_ERROR、HTTP_ERROR 4xx（除 429）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryGuard {

    private final AiRouterProperties routerProperties;

    /**
     * 按配置的 retry 策略执行；不可重试异常直接抛出。
     */
    public <T> T execute(String tag, Supplier<T> action) {
        AiRouterProperties.Retry retry = routerProperties.getRetry();
        int max = retry.getMaxAttempts() == null || retry.getMaxAttempts() < 1 ? 1 : retry.getMaxAttempts();
        long initial = retry.getInitialBackoffMs() == null ? 500L : retry.getInitialBackoffMs();
        long maxBackoff = retry.getMaxBackoffMs() == null ? 5000L : retry.getMaxBackoffMs();

        AiProviderException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                return action.get();
            } catch (AiProviderException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == max) {
                    throw ex;
                }
                long backoff = Math.min(initial * (1L << (attempt - 1)), maxBackoff);
                log.warn("[{}] attempt {}/{} failed ({}), backoff {}ms",
                        tag, attempt, max, ex.getFailureType(), backoff);
                sleepQuietly(backoff);
            }
        }
        throw last != null ? last : new AiProviderException(AiFailureType.UNKNOWN_ERROR, "Retry exhausted");
    }

    public boolean isRetryable(AiProviderException ex) {
        AiFailureType type = ex.getFailureType();
        if (type == null) {
            return false;
        }
        switch (type) {
            case TIMEOUT:
            case EMPTY_RESPONSE:
            case UNKNOWN_ERROR:
                return true;
            case HTTP_ERROR:
                Integer code = ex.getHttpStatus();
                return code != null && (code == 429 || code >= 500);
            case CONFIG_ERROR:
            default:
                return false;
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
