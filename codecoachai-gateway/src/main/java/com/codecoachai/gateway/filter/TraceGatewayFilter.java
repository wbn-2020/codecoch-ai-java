package com.codecoachai.gateway.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TraceGatewayFilter implements GlobalFilter, Ordered {

    private static final String MDC_TRACE_ID = "traceId";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        TraceIdResolution resolution = resolveTraceId(
                exchange.getRequest().getHeaders().getFirst(HeaderConstants.TRACE_ID));
        String traceId = resolution.traceId();
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(HeaderConstants.TRACE_ID, traceId);
            return Mono.<Void>empty();
        });
        if (!resolution.valid()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return response.setComplete()
                    .doOnSubscribe(subscription -> MDC.put(MDC_TRACE_ID, traceId))
                    .doFinally(signalType -> MDC.remove(MDC_TRACE_ID));
        }
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HeaderConstants.TRACE_ID, traceId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        return chain.filter(mutatedExchange)
                .doOnSubscribe(subscription -> MDC.put(MDC_TRACE_ID, traceId))
                .doFinally(signalType -> MDC.remove(MDC_TRACE_ID))
                .contextWrite(context -> context.put(MDC_TRACE_ID, traceId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private TraceIdResolution resolveTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return new TraceIdResolution(newTraceId(), true);
        }
        String value = traceId.trim();
        if (!TRACE_ID_PATTERN.matcher(value).matches()) {
            log.warn("Invalid X-Trace-Id received");
            return new TraceIdResolution(newTraceId(), false);
        }
        return new TraceIdResolution(value, true);
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private record TraceIdResolution(String traceId, boolean valid) {
    }
}
