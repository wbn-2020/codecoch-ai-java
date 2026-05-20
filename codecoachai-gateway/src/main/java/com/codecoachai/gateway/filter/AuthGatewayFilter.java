package com.codecoachai.gateway.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.gateway.domain.TokenInfo;
import com.codecoachai.gateway.service.AuthTokenClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_PATHS = List.of("/auth/login", "/auth/register");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthTokenClient authTokenClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path.startsWith("/inner/")) {
            return writeError(exchange, ErrorCode.FORBIDDEN);
        }
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return writeError(exchange, ErrorCode.UNAUTHORIZED);
        }

        return authTokenClient.tokenInfo(authorization)
                .flatMap(result -> {
                    if (result == null || !result.isSuccess() || result.getData() == null) {
                        return writeError(exchange, ErrorCode.TOKEN_INVALID);
                    }
                    if (path.startsWith("/admin/") && !hasAdminRole(result.getData())) {
                        return writeError(exchange, ErrorCode.FORBIDDEN);
                    }
                    ServerHttpRequest mutated = request.mutate()
                            .headers(headers -> enrichUserHeaders(headers, authorization, result.getData()))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isWhitePath(String path) {
        return WHITE_PATHS.stream().anyMatch(path::equals);
    }

    private boolean hasAdminRole(TokenInfo tokenInfo) {
        List<String> roles = tokenInfo.getRoles();
        return roles != null && roles.stream().anyMatch(SecurityConstants.ROLE_ADMIN::equalsIgnoreCase);
    }

    private void enrichUserHeaders(HttpHeaders headers, String authorization, TokenInfo tokenInfo) {
        headers.set(HeaderConstants.AUTHORIZATION, authorization);
        headers.set(HeaderConstants.USER_ID, String.valueOf(tokenInfo.getUserId()));
        headers.set(HeaderConstants.USERNAME, tokenInfo.getUsername());
        List<String> roles = tokenInfo.getRoles();
        if (roles != null && !roles.isEmpty()) {
            headers.set(HeaderConstants.ROLES, String.join(",", roles));
        }
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ErrorCode errorCode) {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = toJsonBytes(Result.fail(errorCode));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] toJsonBytes(Result<Void> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException ex) {
            return "{\"code\":50000,\"message\":\"系统内部错误\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
