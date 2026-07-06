package com.codecoachai.gateway.filter;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class DemoReadOnlyGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> WRITE_WHITE_PATHS = List.of(
            "/auth/login",
            "/auth/logout",
            "/auth/register",
            "/auth/refresh-token",
            "/health",
            "/ai/health",
            "/portfolio-demo/load",
            "/portfolio-demo/reset");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${codecoachai.demo.read-only:false}")
    private boolean readOnly;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!readOnly || isSafeMethod(exchange.getRequest().getMethod())
                || isWriteWhitePath(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        return writeReadOnlyError(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private boolean isSafeMethod(HttpMethod method) {
        return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method);
    }

    private boolean isWriteWhitePath(String path) {
        return WRITE_WHITE_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> writeReadOnlyError(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> result = Result.fail(ErrorCode.FORBIDDEN.getCode(), "演示只读模式已开启，写入操作不会提交。");
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException ex) {
            bytes = "{\"code\":403,\"message\":\"Demo read-only mode\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
