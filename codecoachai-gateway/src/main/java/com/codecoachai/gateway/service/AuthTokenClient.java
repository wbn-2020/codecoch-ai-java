package com.codecoachai.gateway.service;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.gateway.domain.TokenInfo;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
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

    public Mono<Result<TokenInfo>> tokenInfo(String authorization) {
        return webClientBuilder.build()
                .get()
                .uri(AUTH_TOKEN_INFO_URL)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(HeaderConstants.INTERNAL_CALL, "true")
                .header(HeaderConstants.SERVICE_NAME, GATEWAY_SERVICE_NAME)
                .retrieve()
                .bodyToMono(TOKEN_INFO_TYPE)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> Mono.just(Result.fail(41001, "Token invalid or expired")));
    }
}
