package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class GatewayRateLimiterContractTest {

    @Test
    void authAndSearchRoutesUseConcreteRedisRateLimiters() throws IOException {
        Map<String, Object> root = yamlRoot();
        Map<String, Object> gateway = map(map(map(root, "spring"), "cloud"), "gateway");
        List<?> routes = list(gateway, "routes");

        assertRateLimiter(
                route(routes, "codecoachai-auth"),
                "#{@ipKeyResolver}",
                3,
                10);
        assertRateLimiter(
                route(routes, "codecoachai-search"),
                "#{@userKeyResolver}",
                10,
                20);

        Map<String, Object> requestRateLimiter =
                map(map(gateway, "filter"), "request-rate-limiter");
        assertEquals(Boolean.TRUE, requestRateLimiter.get("deny-empty-key"));
        assertEquals(429, requestRateLimiter.get("empty-key-status-code"));
    }

    private void assertRateLimiter(
            Map<String, Object> route,
            String expectedKeyResolver,
            int expectedReplenishRate,
            int expectedBurstCapacity) {
        List<?> filters = list(route, "filters");
        Map<String, Object> rateLimiter = filters.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::stringKeyMap)
                .filter(filter -> "RequestRateLimiter".equals(filter.get("name")))
                .findFirst()
                .orElse(null);
        assertNotNull(rateLimiter, () -> route.get("id") + " must enable RequestRateLimiter");

        Map<String, Object> args = map(rateLimiter, "args");
        assertEquals(expectedKeyResolver, args.get("key-resolver"));
        assertEquals(expectedReplenishRate, args.get("redis-rate-limiter.replenishRate"));
        assertEquals(expectedBurstCapacity, args.get("redis-rate-limiter.burstCapacity"));
        assertEquals(1, args.get("redis-rate-limiter.requestedTokens"));
        assertTrue(expectedBurstCapacity >= expectedReplenishRate);
    }

    private Map<String, Object> route(List<?> routes, String id) {
        return routes.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::stringKeyMap)
                .filter(route -> id.equals(route.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing gateway route " + id));
    }

    private Map<String, Object> yamlRoot() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("docs/nacos/codecoachai-gateway-dev.yml"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        String yaml = Files.readString(root.resolve("docs/nacos/codecoachai-gateway-dev.yml"));
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(loaded instanceof Map<?, ?> loadedMap)) {
            throw new IllegalArgumentException("Gateway Nacos config must contain a YAML mapping");
        }
        return stringKeyMap(loadedMap);
    }

    private Map<String, Object> map(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map<?, ?> valueMap)) {
            throw new IllegalArgumentException("Expected YAML mapping at " + key + ", got " + value);
        }
        return stringKeyMap(valueMap);
    }

    private List<?> list(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof List<?> valueList)) {
            throw new IllegalArgumentException("Expected YAML list at " + key + ", got " + value);
        }
        return valueList;
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
