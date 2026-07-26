package com.codecoachai.gateway.service;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.gateway.domain.TokenInfo;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthTokenClient {

    private static final String AUTH_TOKEN_INFO_URL = "lb://codecoachai-auth/inner/auth/token-info";
    private static final String GATEWAY_SERVICE_NAME = "codecoachai-gateway";
    private static final ParameterizedTypeReference<Result<TokenInfo>> TOKEN_INFO_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient.Builder webClientBuilder;

    @Value("${codecoachai.internal.auth.enabled:true}")
    private boolean internalAuthEnabled;

    @Value("${codecoachai.internal.auth.secret:}")
    private String internalSecret;

    @Value("${codecoachai.gateway.auth.token-info-timeout-seconds:3}")
    private long tokenInfoTimeoutSeconds;

    public Mono<Result<TokenInfo>> tokenInfo(String authorization) {
        if (internalAuthEnabled && !StringUtils.hasText(internalSecret)) {
            return Mono.just(authServiceUnavailable());
        }
        InternalSignatureHeaders internalSignatureHeaders = buildInternalSignatureHeaders();
        return webClientBuilder.build()
                .get()
                .uri(AUTH_TOKEN_INFO_URL)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(HeaderConstants.INTERNAL_CALL, "true")
                .header(HeaderConstants.SERVICE_NAME, GATEWAY_SERVICE_NAME)
                .header(HeaderConstants.INTERNAL_TIMESTAMP, internalSignatureHeaders.timestamp())
                .header(HeaderConstants.INTERNAL_NONCE, internalSignatureHeaders.nonce())
                .header(HeaderConstants.INTERNAL_SIGNATURE, internalSignatureHeaders.signature())
                .header(HeaderConstants.INTERNAL_SIGNATURE_V2, internalSignatureHeaders.signatureV2())
                .header(HeaderConstants.INTERNAL_BODY_SHA256, internalSignatureHeaders.bodySha256())
                .retrieve()
                .bodyToMono(TOKEN_INFO_TYPE)
                .timeout(Duration.ofSeconds(tokenInfoTimeoutSeconds))
                .onErrorResume(ex -> Mono.just(authServiceUnavailable()));
    }

    private Result<TokenInfo> authServiceUnavailable() {
        return Result.fail(50300, "认证服务暂不可用，请稍后重试");
    }

    private InternalSignatureHeaders buildInternalSignatureHeaders() {
        if (!internalAuthEnabled) {
            return new InternalSignatureHeaders("", "", "", "", "");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String path = InternalSignatureUtils.normalizePath("/inner/auth/token-info");
        String bodySha256 = InternalSignatureUtils.EMPTY_BODY_SHA256;
        String legacyPayload =
                InternalSignatureUtils.canonicalPayload("GET", path, timestamp, nonce, GATEWAY_SERVICE_NAME);
        String payloadV2 = InternalSignatureUtils.internalRequestPayloadV2(
                "GET", path, "", timestamp, nonce, GATEWAY_SERVICE_NAME, bodySha256);
        return new InternalSignatureHeaders(
                timestamp,
                nonce,
                InternalSignatureUtils.hmacSha256Hex(internalSecret, legacyPayload),
                InternalSignatureUtils.hmacSha256Hex(internalSecret, payloadV2),
                bodySha256);
    }

    private record InternalSignatureHeaders(
            String timestamp,
            String nonce,
            String signature,
            String signatureV2,
            String bodySha256) {
    }
}
