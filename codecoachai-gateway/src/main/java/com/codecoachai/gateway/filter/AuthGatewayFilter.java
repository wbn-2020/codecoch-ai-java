package com.codecoachai.gateway.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.constant.SecurityConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.gateway.domain.TokenInfo;
import com.codecoachai.gateway.service.AuthTokenClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String GATEWAY_SERVICE_NAME = "codecoachai-gateway";

    private static final List<String> WHITE_PATHS = List.of(
            "/health",
            "/ai/health",
            "/auth/login",
            "/auth/register",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/refresh-token");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthTokenClient authTokenClient;

    @Value("${codecoachai.internal.auth.enabled:true}")
    private boolean internalAuthEnabled;

    @Value("${codecoachai.internal.auth.secret:}")
    private String internalSecret;

    @PostConstruct
    public void validateInternalAuthSecret() {
        if (internalAuthEnabled && !StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if ("/inner".equals(path) || path.startsWith("/inner/")) {
            return writeError(exchange, ErrorCode.FORBIDDEN);
        }
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            ServerHttpRequest mutated = request.mutate()
                    .headers(this::removeUserHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        }
        if (isWhitePath(path)) {
            ServerHttpRequest mutated = request.mutate()
                    .headers(this::removeUserHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return writeError(exchange, ErrorCode.UNAUTHORIZED);
        }

        return authTokenClient.tokenInfo(authorization)
                .flatMap(result -> {
                    if (result == null) {
                        return writeError(exchange, ErrorCode.SYSTEM_ERROR);
                    }
                    if (!result.isSuccess()) {
                        return writeError(exchange, result);
                    }
                    if (result.getData() == null) {
                        return writeError(exchange, ErrorCode.TOKEN_INVALID);
                    }
                    ServerHttpRequest mutated = request.mutate()
                            .headers(headers -> enrichUserHeaders(headers, authorization, result.getData(), request))
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

    private void enrichUserHeaders(HttpHeaders headers, String authorization, TokenInfo tokenInfo,
            ServerHttpRequest request) {
        removeUserHeaders(headers);
        headers.set(HeaderConstants.AUTHORIZATION, authorization);
        headers.set(HeaderConstants.USER_ID, String.valueOf(tokenInfo.getUserId()));
        headers.set(HeaderConstants.USERNAME, StringUtils.hasText(tokenInfo.getUsername()) ? tokenInfo.getUsername() : "");
        List<String> roles = tokenInfo.getRoles();
        if (roles != null && !roles.isEmpty()) {
            headers.set(HeaderConstants.ROLES, String.join(",", roles));
        }
        signUserContext(headers, tokenInfo, request);
    }

    private void removeUserHeaders(HttpHeaders headers) {
        // Do not trust externally supplied identity/internal-call headers; gateway owns these values.
        headers.remove(HeaderConstants.USER_ID);
        headers.remove(HeaderConstants.USERNAME);
        headers.remove(HeaderConstants.ROLES);
        headers.remove(HeaderConstants.USER_CONTEXT_TIMESTAMP);
        headers.remove(HeaderConstants.USER_CONTEXT_NONCE);
        headers.remove(HeaderConstants.USER_CONTEXT_SIGNER);
        headers.remove(HeaderConstants.USER_CONTEXT_SIGNATURE);
        headers.remove(HeaderConstants.USER_CONTEXT_SIGNATURE_V2);
        headers.remove(HeaderConstants.INTERNAL_CALL);
        headers.remove(HeaderConstants.SERVICE_NAME);
        headers.remove(HeaderConstants.INTERNAL_TIMESTAMP);
        headers.remove(HeaderConstants.INTERNAL_NONCE);
        headers.remove(HeaderConstants.INTERNAL_SIGNATURE);
        headers.remove(HeaderConstants.INTERNAL_SIGNATURE_V2);
        headers.remove(HeaderConstants.INTERNAL_BODY_SHA256);
    }

    private void signUserContext(HttpHeaders headers, TokenInfo tokenInfo, ServerHttpRequest request) {
        if (!internalAuthEnabled || !StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String userId = String.valueOf(tokenInfo.getUserId());
        String username = headers.getFirst(HeaderConstants.USERNAME);
        String roles = headers.getFirst(HeaderConstants.ROLES);
        String path = request.getURI().getRawPath();
        String rawQuery = request.getURI().getRawQuery();
        String bodySha256 = bodySha256(request);
        String legacyPayload = InternalSignatureUtils.userContextPayload(
                String.valueOf(request.getMethod()), path, timestamp, userId, username, roles);
        String payloadV2 = InternalSignatureUtils.userContextPayloadV2(
                String.valueOf(request.getMethod()),
                path,
                rawQuery,
                timestamp,
                nonce,
                GATEWAY_SERVICE_NAME,
                bodySha256,
                userId,
                username,
                roles);
        headers.set(HeaderConstants.USER_CONTEXT_TIMESTAMP, timestamp);
        headers.set(HeaderConstants.USER_CONTEXT_NONCE, nonce);
        headers.set(HeaderConstants.USER_CONTEXT_SIGNER, GATEWAY_SERVICE_NAME);
        headers.set(HeaderConstants.INTERNAL_BODY_SHA256, bodySha256);
        headers.set(
                HeaderConstants.USER_CONTEXT_SIGNATURE,
                InternalSignatureUtils.hmacSha256Hex(internalSecret, legacyPayload));
        headers.set(
                HeaderConstants.USER_CONTEXT_SIGNATURE_V2,
                InternalSignatureUtils.hmacSha256Hex(internalSecret, payloadV2));
    }

    private String bodySha256(ServerHttpRequest request) {
        long contentLength = request.getHeaders().getContentLength();
        boolean transferEncoded = !request.getHeaders().getOrEmpty(HttpHeaders.TRANSFER_ENCODING).isEmpty();
        HttpMethod method = request.getMethod();
        if (contentLength == 0L
                || (contentLength < 0L
                        && !transferEncoded
                        && (HttpMethod.GET.equals(method)
                                || HttpMethod.HEAD.equals(method)
                                || HttpMethod.OPTIONS.equals(method)))) {
            return InternalSignatureUtils.EMPTY_BODY_SHA256;
        }
        // Gateway must preserve request streaming; payload bytes are intentionally not buffered here.
        return InternalSignatureUtils.STREAMING_BODY_SHA256;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ErrorCode errorCode) {
        return writeError(exchange, Result.fail(errorCode));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, Result<?> result) {
        exchange.getResponse().setStatusCode(httpStatusFor(result));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = toJsonBytes(result);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatus httpStatusFor(Result<?> result) {
        Integer code = result == null ? null : result.getCode();
        if (ErrorCode.UNAUTHORIZED.getCode() == code || ErrorCode.TOKEN_INVALID.getCode() == code) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ErrorCode.FORBIDDEN.getCode() == code) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private byte[] toJsonBytes(Result<?> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException ex) {
            return "{\"code\":50000,\"message\":\"系统内部错误\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
