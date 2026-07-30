package com.codecoachai.gateway.config;

import com.codecoachai.common.core.util.InternalSignatureUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        List<TrustedProxyRange> trustedProxies = parseTrustedProxies(trustedProxyConfig);
        return exchange -> Mono.just(resolveClientIp(exchange, trustedProxies));
    }

    /**
     * 按用户 Token 限流（登录用户）。
     */
    @Bean("userKeyResolver")
    public KeyResolver userKeyResolver(
            @Value("${codecoachai.gateway.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
            String trustedProxyConfig) {
        List<TrustedProxyRange> trustedProxies = parseTrustedProxies(trustedProxyConfig);
        return exchange -> {
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (token != null && !token.isBlank()) {
                String tokenHash = InternalSignatureUtils.sha256Hex(
                        token.getBytes(StandardCharsets.UTF_8));
                return Mono.just("token:" + tokenHash);
            }
            return Mono.just(resolveClientIp(exchange, trustedProxies));
        };
    }

    private String resolveClientIp(ServerWebExchange exchange, List<TrustedProxyRange> trustedProxies) {
        String remoteIp = resolveRemoteIp(exchange);
        if (isTrustedProxy(remoteIp, trustedProxies)) {
            String forwardedIp = resolveForwardedClientIp(
                    exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"),
                    trustedProxies);
            if (StringUtils.hasText(forwardedIp)) {
                return forwardedIp;
            }
            String realIp = normalizeIpLiteral(
                    exchange.getRequest().getHeaders().getFirst("X-Real-IP"));
            if (StringUtils.hasText(realIp)) {
                return realIp;
            }
        }
        return StringUtils.hasText(remoteIp) ? remoteIp : UNKNOWN_IP;
    }

    private String resolveRemoteIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : UNKNOWN_IP;
    }

    private boolean isTrustedProxy(String remoteIp, List<TrustedProxyRange> trustedProxies) {
        byte[] address = parseIpAddress(remoteIp);
        return address != null && trustedProxies.stream().anyMatch(range -> range.contains(address));
    }

    private String resolveForwardedClientIp(String headerValue, List<TrustedProxyRange> trustedProxies) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        String[] chain = headerValue.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            String candidate = normalizeIpLiteral(chain[index]);
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (!isTrustedProxy(candidate, trustedProxies)) {
                return candidate;
            }
        }
        return "";
    }

    private List<TrustedProxyRange> parseTrustedProxies(String configValue) {
        List<TrustedProxyRange> ranges = new ArrayList<>();
        Arrays.stream(String.valueOf(configValue).split(","))
                .map(String::trim)
                .map(this::parseTrustedProxyRange)
                .filter(range -> range != null)
                .forEach(ranges::add);
        return List.copyOf(ranges);
    }

    private TrustedProxyRange parseTrustedProxyRange(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int separator = value.lastIndexOf('/');
        String addressValue = separator < 0 ? value : value.substring(0, separator);
        String normalizedAddress = normalizeIpLiteral(addressValue);
        byte[] address = parseIpAddress(normalizedAddress);
        if (address == null) {
            return null;
        }

        int maxPrefix = address.length * Byte.SIZE;
        int prefixLength = maxPrefix;
        if (separator >= 0) {
            try {
                prefixLength = Integer.parseInt(value.substring(separator + 1));
            } catch (NumberFormatException ex) {
                return null;
            }
            if (prefixLength < 0 || prefixLength > maxPrefix) {
                return null;
            }
        }
        return new TrustedProxyRange(mask(address, prefixLength), prefixLength);
    }

    private byte[] parseIpAddress(String value) {
        String normalized = normalizeIpLiteral(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return InetAddress.getByName(normalized).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private byte[] mask(byte[] address, int prefixLength) {
        byte[] network = address.clone();
        for (int bit = prefixLength; bit < network.length * Byte.SIZE; bit++) {
            int byteIndex = bit / Byte.SIZE;
            int bitIndex = Byte.SIZE - 1 - (bit % Byte.SIZE);
            network[byteIndex] &= (byte) ~(1 << bitIndex);
        }
        return network;
    }

    private String normalizeIpLiteral(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String candidate = value.trim();
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (candidate.length() > 64) {
            return "";
        }
        if (candidate.indexOf(':') < 0) {
            String[] octets = candidate.split("\\.", -1);
            if (octets.length != 4) {
                return "";
            }
            for (String octet : octets) {
                if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                    return "";
                }
                int numeric = Integer.parseInt(octet);
                if (numeric > 255) {
                    return "";
                }
            }
            return candidate;
        }
        if (!candidate.matches("[0-9A-Fa-f:.]+")) {
            return "";
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException ex) {
            return "";
        }
    }

    private record TrustedProxyRange(byte[] network, int prefixLength) {

        private boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            for (int bit = 0; bit < prefixLength; bit++) {
                int byteIndex = bit / Byte.SIZE;
                int bitIndex = Byte.SIZE - 1 - (bit % Byte.SIZE);
                int mask = 1 << bitIndex;
                if ((address[byteIndex] & mask) != (network[byteIndex] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
