package com.codecoachai.common.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 配置。
 * 复用 Spring Boot 自带 redis 连接参数（spring.data.redis.*）。
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:3000ms}")
    private String timeout;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(StringUtils.hasText(password) ? password : null)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(16)
                .setConnectTimeout(5000)
                .setTimeout(parseTimeoutMs(timeout));
        RedissonClient client = Redisson.create(config);
        log.info("Redisson 客户端初始化完成 address={} database={}", address, database);
        return client;
    }

    private int parseTimeoutMs(String text) {
        if (!StringUtils.hasText(text)) {
            return 3000;
        }
        String trimmed = text.trim().toLowerCase();
        if (trimmed.endsWith("ms")) {
            return Integer.parseInt(trimmed.substring(0, trimmed.length() - 2).trim());
        }
        if (trimmed.endsWith("s")) {
            return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1).trim()) * 1000;
        }
        return Integer.parseInt(trimmed);
    }
}
