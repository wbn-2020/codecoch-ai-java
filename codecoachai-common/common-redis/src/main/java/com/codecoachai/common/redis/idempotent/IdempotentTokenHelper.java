package com.codecoachai.common.redis.idempotent;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 幂等 Token 工具：
 * 流程：
 *   1. 客户端调用 generateToken() 获取 token
 *   2. 关键写接口请求头携带 X-Idempotent-Token
 *   3. 服务端用 consume() 判断；返回 false 表示是重复请求
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentTokenHelper {

    /** Redis key 前缀 */
    private static final String PREFIX = "codecoachai:idempotent:";

    /** 默认有效期 5 分钟 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成一个新 Token 并写入 Redis（占位值 "1"），未被消费时一直有效。
     */
    public String generateToken() {
        return generateToken(DEFAULT_TTL);
    }

    public String generateToken(Duration ttl) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(PREFIX + token, "1", ttl);
        return token;
    }

    /**
     * 消费 Token：DEL 返回 1 表示首次消费，0 表示重复请求或 token 不存在。
     *
     * @return true=放行 false=重复请求/无效 token
     */
    public boolean consume(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Boolean deleted = stringRedisTemplate.delete(PREFIX + token);
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * 业务自定义 Key 的幂等检查（无需提前生成 token，业务自己拼 key）。
     * 用于"同一业务在 ttl 内只能成功执行一次"的场景。
     *
     * @return true=首次执行 false=重复
     */
    public boolean tryAcquire(String bizKey, Duration ttl) {
        if (!StringUtils.hasText(bizKey)) {
            return false;
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(PREFIX + bizKey, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放业务幂等占位（如业务失败时清理）。
     */
    public void release(String bizKey) {
        if (StringUtils.hasText(bizKey)) {
            stringRedisTemplate.delete(PREFIX + bizKey);
        }
    }
}
