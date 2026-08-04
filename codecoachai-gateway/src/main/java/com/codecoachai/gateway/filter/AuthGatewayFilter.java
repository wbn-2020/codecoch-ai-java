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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
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

    @Value("${codecoachai.gateway.internal.target-secrets.codecoachai-core:}")
    private String coreTargetSecret;

    @Value("${codecoachai.gateway.internal.target-secrets.codecoachai-ai:}")
    private String aiTargetSecret;

    @Value("${codecoachai.gateway.internal.target-secrets.codecoachai-search:}")
    private String searchTargetSecret;

    @Value("${codecoachai.gateway.internal.max-signed-body-bytes:1048576}")
    private int maxSignedBodyBytes;

    @Value("${codecoachai.gateway.internal.unsigned-body-paths:}")
    private String unsignedBodyPaths = "";

    @PostConstruct
    public void validateInternalAuthSecret() {
        if (internalAuthEnabled
                && (!StringUtils.hasText(internalSecret)
                        || !StringUtils.hasText(coreTargetSecret)
                        || !StringUtils.hasText(aiTargetSecret)
                        || !StringUtils.hasText(searchTargetSecret))) {
            throw new IllegalStateException(
                    "Gateway internal signing secrets for Core, AI, and Search must be configured");
        }
        if (maxSignedBodyBytes < 1 || maxSignedBodyBytes > 16 * 1024 * 1024) {
            throw new IllegalStateException(
                    "codecoachai.gateway.internal.max-signed-body-bytes must be between 1 and 16777216");
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
                    return forwardAuthenticated(
                            exchange,
                            chain,
                            authorization,
                            result.getData());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isWhitePath(String path) {
        return WHITE_PATHS.stream().anyMatch(path::equals);
    }

    private Mono<Void> forwardAuthenticated(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String authorization,
            TokenInfo tokenInfo) {
        ServerHttpRequest request = exchange.getRequest();
        if (allowsUnsignedBody(request)) {
            return forwardSigned(
                    exchange,
                    chain,
                    request,
                    authorization,
                    tokenInfo,
                    InternalSignatureUtils.STREAMING_BODY_SHA256);
        }
        if (hasNoBody(request)) {
            return forwardSigned(
                    exchange,
                    chain,
                    request,
                    authorization,
                    tokenInfo,
                    InternalSignatureUtils.EMPTY_BODY_SHA256);
        }
        if (request.getHeaders().getContentLength() > maxSignedBodyBytes) {
            return writePayloadTooLarge(exchange);
        }

        Mono<byte[]> cachedBody = DataBufferUtils.join(request.getBody(), maxSignedBodyBytes)
                .map(this::readAndRelease)
                .defaultIfEmpty(new byte[0])
                .onErrorMap(
                        DataBufferLimitException.class,
                        SignedBodyTooLargeException::new);
        return cachedBody
                .flatMap(body -> {
                    String bodySha256 = InternalSignatureUtils.sha256Hex(body);
                    ServerHttpRequest signed = signedRequest(
                            exchange,
                            request,
                            authorization,
                            tokenInfo,
                            bodySha256,
                            body.length);
                    ServerHttpRequest replayable = replayableRequest(
                            exchange,
                            signed,
                            body);
                    return chain.filter(exchange.mutate().request(replayable).build());
                })
                .onErrorResume(SignedBodyTooLargeException.class, ignored -> writePayloadTooLarge(exchange));
    }

    private Mono<Void> forwardSigned(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            ServerHttpRequest request,
            String authorization,
            TokenInfo tokenInfo,
            String bodySha256) {
        ServerHttpRequest signed = signedRequest(
                exchange,
                request,
                authorization,
                tokenInfo,
                bodySha256,
                null);
        return chain.filter(exchange.mutate().request(signed).build());
    }

    private ServerHttpRequest signedRequest(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            String authorization,
            TokenInfo tokenInfo,
            String bodySha256,
            Integer cachedBodyLength) {
        return request.mutate()
                .headers(headers -> {
                    enrichUserHeaders(
                            headers,
                            authorization,
                            tokenInfo,
                            request,
                            exchange,
                            bodySha256);
                    if (cachedBodyLength != null) {
                        headers.remove(HttpHeaders.TRANSFER_ENCODING);
                        headers.setContentLength(cachedBodyLength);
                    }
                })
                .build();
    }

    private ServerHttpRequest replayableRequest(
            ServerWebExchange exchange,
            ServerHttpRequest request,
            byte[] body) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
    }

    private byte[] readAndRelease(DataBuffer buffer) {
        try {
            byte[] body = new byte[buffer.readableByteCount()];
            buffer.read(body);
            return body;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private boolean hasNoBody(ServerHttpRequest request) {
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength == 0L) {
            return true;
        }
        boolean transferEncoded =
                !request.getHeaders().getOrEmpty(HttpHeaders.TRANSFER_ENCODING).isEmpty();
        HttpMethod method = request.getMethod();
        return contentLength < 0L
                && !transferEncoded
                && (HttpMethod.GET.equals(method)
                        || HttpMethod.HEAD.equals(method)
                        || HttpMethod.OPTIONS.equals(method));
    }

    private boolean allowsUnsignedBody(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        if (!HttpMethod.POST.equals(method)
                && !HttpMethod.PUT.equals(method)
                && !HttpMethod.PATCH.equals(method)) {
            return false;
        }
        MediaType contentType = request.getHeaders().getContentType();
        if (contentType == null
                || !"multipart".equals(contentType.getType().toLowerCase(Locale.ROOT))) {
            return false;
        }
        String path = request.getURI().getPath();
        return StringUtils.hasText(unsignedBodyPaths)
                && Arrays.stream(StringUtils.commaDelimitedListToStringArray(unsignedBodyPaths))
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .anyMatch(path::equals);
    }

    private void enrichUserHeaders(HttpHeaders headers, String authorization, TokenInfo tokenInfo,
            ServerHttpRequest request, ServerWebExchange exchange, String bodySha256) {
        removeUserHeaders(headers);
        headers.set(HeaderConstants.AUTHORIZATION, authorization);
        headers.set(HeaderConstants.USER_ID, String.valueOf(tokenInfo.getUserId()));
        headers.set(HeaderConstants.USERNAME, StringUtils.hasText(tokenInfo.getUsername()) ? tokenInfo.getUsername() : "");
        List<String> roles = tokenInfo.getRoles();
        if (roles != null && !roles.isEmpty()) {
            headers.set(HeaderConstants.ROLES, String.join(",", roles));
        }
        signUserContext(headers, tokenInfo, request, targetSecret(exchange), bodySha256);
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

    private void signUserContext(
            HttpHeaders headers,
            TokenInfo tokenInfo,
            ServerHttpRequest request,
            String targetSecret,
            String bodySha256) {
        if (!internalAuthEnabled || !StringUtils.hasText(targetSecret)) {
            throw new IllegalStateException("Gateway target signing secret must be configured");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String userId = String.valueOf(tokenInfo.getUserId());
        String username = headers.getFirst(HeaderConstants.USERNAME);
        String roles = headers.getFirst(HeaderConstants.ROLES);
        String path = request.getURI().getRawPath();
        String rawQuery = request.getURI().getRawQuery();
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
                InternalSignatureUtils.hmacSha256Hex(targetSecret, legacyPayload));
        headers.set(
                HeaderConstants.USER_CONTEXT_SIGNATURE_V2,
                InternalSignatureUtils.hmacSha256Hex(targetSecret, payloadV2));
    }

    private String targetSecret(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String targetService = route == null ? "codecoachai-core" : route.getUri().getHost();
        return switch (targetService) {
            case "codecoachai-ai" -> aiTargetSecret;
            case "codecoachai-search" -> searchTargetSecret;
            case "codecoachai-core" -> StringUtils.hasText(coreTargetSecret)
                    ? coreTargetSecret
                    : internalSecret;
            default -> throw new IllegalStateException("Unsupported Gateway target: " + targetService);
        };
    }

    private Mono<Void> writePayloadTooLarge(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = toJsonBytes(Result.fail(
                ErrorCode.PARAM_ERROR.getCode(),
                "Request body exceeds the signed payload limit"));
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
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

    private static final class SignedBodyTooLargeException extends RuntimeException {

        private SignedBodyTooLargeException(DataBufferLimitException cause) {
            super(cause);
        }
    }
}
