package com.codecoachai.common.feign.config;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import feign.RequestTemplate;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class OpenFeignConfig {

    private static final List<String> PASS_HEADERS = List.of(
            HeaderConstants.AUTHORIZATION,
            HeaderConstants.USER_ID,
            HeaderConstants.USERNAME,
            HeaderConstants.ROLES,
            HeaderConstants.TRACE_ID
    );

    @Bean
    public RequestInterceptor codeCoachAiFeignRequestInterceptor(
            @Value("${spring.application.name:unknown-service}") String serviceName,
            @Value("${codecoachai.internal.auth.enabled:true}") boolean internalAuthEnabled,
            @Value("${codecoachai.internal.auth.secret:}") String internalSecret) {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                for (String header : PASS_HEADERS) {
                    String value = request.getHeader(header);
                    if (StringUtils.hasText(value)) {
                        template.header(header, value);
                    }
                }
            }
            signForwardedUserContext(template, internalAuthEnabled, internalSecret);
            template.header(HeaderConstants.INTERNAL_CALL, "true");
            template.header(HeaderConstants.SERVICE_NAME, serviceName);
            if (internalAuthEnabled) {
                if (!StringUtils.hasText(internalSecret)) {
                    throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
                }
                String timestamp = String.valueOf(System.currentTimeMillis());
                String nonce = UUID.randomUUID().toString();
                String path = InternalSignatureUtils.normalizePath(template.path());
                String payload = InternalSignatureUtils.canonicalPayload(
                        template.method(), path, timestamp, nonce, serviceName);
                String signature = InternalSignatureUtils.hmacSha256Hex(internalSecret, payload);
                template.header(HeaderConstants.INTERNAL_TIMESTAMP, timestamp);
                template.header(HeaderConstants.INTERNAL_NONCE, nonce);
                template.header(HeaderConstants.INTERNAL_SIGNATURE, signature);
            }
        };
    }

    private void signForwardedUserContext(RequestTemplate template, boolean internalAuthEnabled, String internalSecret) {
        String userId = firstHeader(template, HeaderConstants.USER_ID);
        if (!StringUtils.hasText(userId)) {
            return;
        }
        if (!internalAuthEnabled || !StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }
        template.removeHeader(HeaderConstants.USER_CONTEXT_TIMESTAMP);
        template.removeHeader(HeaderConstants.USER_CONTEXT_SIGNATURE);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String username = firstHeader(template, HeaderConstants.USERNAME);
        String roles = firstHeader(template, HeaderConstants.ROLES);
        String payload = InternalSignatureUtils.userContextPayload(
                template.method(), template.path(), timestamp, userId, username, roles);
        String signature = InternalSignatureUtils.hmacSha256Hex(internalSecret, payload);
        template.header(HeaderConstants.USER_CONTEXT_TIMESTAMP, timestamp);
        template.header(HeaderConstants.USER_CONTEXT_SIGNATURE, signature);
    }

    private String firstHeader(RequestTemplate template, String headerName) {
        return template.headers().getOrDefault(headerName, List.of()).stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }
}
