package com.codecoachai.gateway.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 限流配置。
 * 提供两种 KeyResolver：按 IP 和按用户 Token。
 */
@Configuration
public class RateLimiterConfig {

    private static final String UNKNOWN_IP = "unknown";

    /**
     * 按客户端 IP 限流（默认策略）。只有请求来自可信代理时才采信 X-Forwarded-For / X-Real-IP。
     */
    @Bean("ipKeyResolver")
    @Primary
    public KeyResolver ipKeyResolver(
            @Value("${codecoachai.gateway.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
            String trustedProxyConfig) {
        Set<String> trustedProxies = parseTrustedProxies(trustedProxyConfig);
        return exchange -> Mono.just(resolveClientIp(exchange, trustedProxies));
    }

    /**
     * 按用户 Token 限流（登录用户）。
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver(
            @Value("${codecoachai.gateway.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
            String trustedProxyConfig) {
        Set<String> trustedProxies = parseTrustedProxies(trustedProxyConfig);
        return exchange -> {
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (token != null && !token.isBlank()) {
                return Mono.just(token);
            }
            return Mono.just(resolveClientIp(exchange, trustedProxies));
        };
    }

    private String resolveClientIp(ServerWebExchange exchange, Set<String> trustedProxies) {
        String remoteIp = resolveRemoteIp(exchange);
        if (isTrustedProxy(remoteIp, trustedProxies)) {
            String forwardedIp = firstForwardedIp(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
            if (StringUtils.hasText(forwardedIp)) {
                return forwardedIp;
            }
            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return StringUtils.hasText(remoteIp) ? remoteIp : UNKNOWN_IP;
    }

    private String resolveRemoteIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : UNKNOWN_IP;
    }

    private boolean isTrustedProxy(String remoteIp, Set<String> trustedProxies) {
        return StringUtils.hasText(remoteIp) && trustedProxies.contains(remoteIp);
    }

    private String firstForwardedIp(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        return Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private Set<String> parseTrustedProxies(String configValue) {
        return Arrays.stream(String.valueOf(configValue).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }
}
