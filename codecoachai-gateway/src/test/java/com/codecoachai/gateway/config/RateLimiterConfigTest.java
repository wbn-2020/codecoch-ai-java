package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    @Test
    void ipResolverOnlyTrustsForwardedAddressFromConfiguredProxy() {
        KeyResolver resolver = config.ipKeyResolver("127.0.0.1,10.0.0.2");
        MockServerWebExchange trustedProxyRequest = exchange(
                "127.0.0.1",
                "198.51.100.99, 203.0.113.8, 10.0.0.2",
                null);
        MockServerWebExchange directRequest = exchange(
                "198.51.100.9",
                "203.0.113.8",
                null);

        assertEquals("203.0.113.8", resolver.resolve(trustedProxyRequest).block());
        assertEquals("198.51.100.9", resolver.resolve(directRequest).block());
    }

    @Test
    void ipResolverSupportsDockerNetworkCidrWithoutTrustingAdjacentNetworks() {
        KeyResolver resolver = config.ipKeyResolver("127.0.0.1,192.168.16.0/20");
        MockServerWebExchange dockerProxyRequest = exchange(
                "192.168.19.12",
                "198.51.100.44, 192.168.17.3",
                null);
        MockServerWebExchange adjacentNetworkRequest = exchange(
                "192.168.32.12",
                "198.51.100.44",
                null);

        assertEquals("198.51.100.44", resolver.resolve(dockerProxyRequest).block());
        assertEquals("192.168.32.12", resolver.resolve(adjacentNetworkRequest).block());
    }

    @Test
    void userResolverHashesBearerTokenAndFallsBackToClientIp() {
        KeyResolver resolver = config.userKeyResolver("127.0.0.1");
        String rawToken = "Bearer highly-sensitive-token";
        MockServerWebExchange authenticated = exchange("198.51.100.9", null, rawToken);
        MockServerWebExchange anonymous = exchange("198.51.100.9", null, null);

        String resolvedTokenKey = resolver.resolve(authenticated).block();
        assertNotEquals(rawToken, resolvedTokenKey);
        assertEquals(70, resolvedTokenKey.length());
        assertEquals("token:", resolvedTokenKey.substring(0, 6));
        assertEquals("198.51.100.9", resolver.resolve(anonymous).block());
    }

    private MockServerWebExchange exchange(String remoteIp, String forwardedFor, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress(remoteIp, 12345));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MockServerWebExchange.from(request.build());
    }
}
