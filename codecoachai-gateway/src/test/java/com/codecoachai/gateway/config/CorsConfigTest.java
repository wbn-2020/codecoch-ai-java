package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

class CorsConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(CorsConfig.class);

    @Test
    void exposesTraceIdHeaderByDefault() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            CorsWebFilter filter = context.getBean(CorsWebFilter.class);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("http://api.codecoachai.local/api/test")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .build());

            filter.filter(exchange, filteredExchange -> {
                        filteredExchange.getResponse().getHeaders().set("X-Trace-Id", "trace-123");
                        return filteredExchange.getResponse().setComplete();
                    })
                    .block();

            assertEquals(
                    List.of("X-Trace-Id"),
                    exchange.getResponse().getHeaders().getAccessControlExposeHeaders());
        });
    }

    @Test
    void parsesConfiguredExposedHeaders() {
        CorsWebFilter filter = new CorsConfig().corsWebFilter(
                "http://localhost:5173",
                "GET",
                "Content-Type",
                " X-Trace-Id, X-Request-Id ");

        assertEquals(
                List.of("X-Trace-Id", "X-Request-Id"),
                corsConfiguration(filter).getExposedHeaders());
    }

    private CorsConfiguration corsConfiguration(CorsWebFilter filter) {
        CorsConfigurationSource source = (CorsConfigurationSource) ReflectionTestUtils.getField(
                filter,
                "configSource");
        return source.getCorsConfiguration(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build()));
    }
}
