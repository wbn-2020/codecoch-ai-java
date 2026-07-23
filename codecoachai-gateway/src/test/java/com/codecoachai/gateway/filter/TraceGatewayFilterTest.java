package com.codecoachai.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.gateway.config.ReactiveTraceMdcBridge;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class TraceGatewayFilterTest {

    private final ReactiveTraceMdcBridge bridge = new ReactiveTraceMdcBridge();
    private final TraceGatewayFilter filter = new TraceGatewayFilter();

    @BeforeEach
    void setUp() {
        MDC.clear();
        bridge.start();
    }

    @AfterEach
    void tearDown() {
        bridge.stop();
        MDC.clear();
    }

    @Test
    void propagatesTraceThroughHeadersAndReactorContextWithoutClearingOuterMdc() {
        MDC.put("traceId", "outer-trace");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(HeaderConstants.TRACE_ID, "request-trace-123")
                        .build());
        AtomicReference<String> observedMdc = new AtomicReference<>();
        AtomicReference<String> observedContext = new AtomicReference<>();

        filter.filter(exchange, mutatedExchange ->
                        Mono.just("signal")
                                .map(signal -> {
                                    observedMdc.set(MDC.get("traceId"));
                                    return signal;
                                })
                                .doOnEach(signal -> {
                                    if (signal.isOnNext()) {
                                        observedContext.set(signal.getContextView().get("traceId"));
                                    }
                                })
                                .then(Mono.defer(() -> mutatedExchange.getResponse().setComplete())))
                .block(Duration.ofSeconds(5));

        assertEquals("request-trace-123", observedMdc.get());
        assertEquals("request-trace-123", observedContext.get());
        assertEquals(
                "request-trace-123",
                exchange.getResponse().getHeaders().getFirst(HeaderConstants.TRACE_ID));
        assertEquals("outer-trace", MDC.get("traceId"));
    }

    @Test
    void invalidTraceLogIncludesGeneratedTraceId(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(HeaderConstants.TRACE_ID, "bad")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(5));

        String generatedTraceId =
                exchange.getResponse().getHeaders().getFirst(HeaderConstants.TRACE_ID);
        assertNotNull(generatedTraceId);
        assertTrue(output.getOut().contains("Invalid X-Trace-Id received"));
        assertTrue(output.getOut().contains(generatedTraceId));
    }
}
