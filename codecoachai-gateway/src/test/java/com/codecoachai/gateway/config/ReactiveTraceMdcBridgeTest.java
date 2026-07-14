package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

class ReactiveTraceMdcBridgeTest {

    private final ReactiveTraceMdcBridge bridge = new ReactiveTraceMdcBridge();

    @BeforeEach
    void startBridge() {
        MDC.clear();
        bridge.start();
    }

    @AfterEach
    void stopBridge() {
        bridge.stop();
        MDC.clear();
    }

    @Test
    void keepsInterleavedTraceContextsIsolated() {
        Scheduler scheduler = Schedulers.newSingle("trace-mdc-test");
        List<String> observations = new CopyOnWriteArrayList<>();
        try {
            Flux.merge(
                            observedSignals("trace-alpha", scheduler, observations),
                            observedSignals("trace-beta", scheduler, observations))
                    .blockLast(Duration.ofSeconds(5));
        } finally {
            scheduler.dispose();
        }

        assertEquals(40, observations.size());
        assertTrue(observations.stream().allMatch(value ->
                value.equals("trace-alpha:trace-alpha")
                        || value.equals("trace-beta:trace-beta")));
    }

    @Test
    void restoresPreviousMdcAfterEachSignalAndCleansAfterCompletion() {
        MDC.put("traceId", "outer-trace");

        String observed = Flux.just("value")
                .publishOn(Schedulers.boundedElastic())
                .map(value -> MDC.get("traceId"))
                .contextWrite(context -> context.put("traceId", "reactive-trace"))
                .single()
                .block(Duration.ofSeconds(5));

        assertEquals("reactive-trace", observed);
        assertEquals("outer-trace", MDC.get("traceId"));

        MDC.clear();
        Flux.just("value")
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(value -> assertEquals("reactive-trace", MDC.get("traceId")))
                .contextWrite(context -> context.put("traceId", "reactive-trace"))
                .blockLast(Duration.ofSeconds(5));

        assertNull(MDC.get("traceId"));
    }

    private Flux<String> observedSignals(
            String traceId,
            Scheduler scheduler,
            List<String> observations) {
        return Flux.range(0, 20)
                .delayElements(Duration.ofMillis(1), scheduler)
                .map(index -> {
                    observations.add(traceId + ":" + MDC.get("traceId"));
                    return traceId;
                })
                .contextWrite(context -> context.put("traceId", traceId));
    }
}
