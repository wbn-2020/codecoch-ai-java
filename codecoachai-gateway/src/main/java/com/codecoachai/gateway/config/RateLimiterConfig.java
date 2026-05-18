package com.codecoachai.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Gateway 限流配置。
 * 提供两种 KeyResolver：按 IP 和按用户 Token。
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按客户端 IP 限流（默认策略）。
     */
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            }
            if (ip == null || ip.isBlank()) {
                ip = exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
            }
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return Mono.just(ip);
        };
    }

    /**
     * 按用户 Token 限流（登录用户）。
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (token != null && !token.isBlank()) {
                return Mono.just(token);
            }
            // 未登录用户降级为 IP 限流
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }
}
