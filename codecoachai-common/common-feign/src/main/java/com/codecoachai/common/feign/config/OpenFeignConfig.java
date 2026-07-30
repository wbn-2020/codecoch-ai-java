package com.codecoachai.common.feign.config;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import feign.RequestTemplate;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
            removeTrustedSignatureHeaders(template);
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

            String bodySha256 = bodySha256(template);
            replaceHeader(template, HeaderConstants.INTERNAL_BODY_SHA256, bodySha256);
            signForwardedUserContext(
                    template, serviceName, bodySha256, internalAuthEnabled, internalSecret);
            replaceHeader(template, HeaderConstants.INTERNAL_CALL, "true");
            replaceHeader(template, HeaderConstants.SERVICE_NAME, serviceName);
            if (internalAuthEnabled) {
                if (!StringUtils.hasText(internalSecret)) {
                    throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
                }
                String timestamp = String.valueOf(System.currentTimeMillis());
                String nonce = UUID.randomUUID().toString();
                String path = InternalSignatureUtils.normalizePath(template.path());
                String rawQuery = InternalSignatureUtils.rawQueryFromTarget(template.url());
                String legacyPayload = InternalSignatureUtils.canonicalPayload(
                        template.method(), path, timestamp, nonce, serviceName);
                String payloadV2 = InternalSignatureUtils.internalRequestPayloadV2(
                        template.method(), path, rawQuery, timestamp, nonce, serviceName, bodySha256);
                replaceHeader(template, HeaderConstants.INTERNAL_TIMESTAMP, timestamp);
                replaceHeader(template, HeaderConstants.INTERNAL_NONCE, nonce);
                replaceHeader(
                        template,
                        HeaderConstants.INTERNAL_SIGNATURE,
                        InternalSignatureUtils.hmacSha256Hex(internalSecret, legacyPayload));
                replaceHeader(
                        template,
                        HeaderConstants.INTERNAL_SIGNATURE_V2,
                        InternalSignatureUtils.hmacSha256Hex(internalSecret, payloadV2));
            }
        };
    }

    private void signForwardedUserContext(
            RequestTemplate template,
            String serviceName,
            String bodySha256,
            boolean internalAuthEnabled,
            String internalSecret) {
        String userId = firstHeader(template, HeaderConstants.USER_ID);
        if (!StringUtils.hasText(userId)) {
            return;
        }
        if (!internalAuthEnabled || !StringUtils.hasText(internalSecret)) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String username = firstHeader(template, HeaderConstants.USERNAME);
        String roles = firstHeader(template, HeaderConstants.ROLES);
        String path = InternalSignatureUtils.normalizePath(template.path());
        String rawQuery = InternalSignatureUtils.rawQueryFromTarget(template.url());
        String legacyPayload = InternalSignatureUtils.userContextPayload(
                template.method(), template.path(), timestamp, userId, username, roles);
        String payloadV2 = InternalSignatureUtils.userContextPayloadV2(
                template.method(),
                path,
                rawQuery,
                timestamp,
                nonce,
                serviceName,
                bodySha256,
                userId,
                username,
                roles);
        replaceHeader(template, HeaderConstants.USER_CONTEXT_TIMESTAMP, timestamp);
        replaceHeader(template, HeaderConstants.USER_CONTEXT_NONCE, nonce);
        replaceHeader(template, HeaderConstants.USER_CONTEXT_SIGNER, serviceName);
        replaceHeader(
                template,
                HeaderConstants.USER_CONTEXT_SIGNATURE,
                InternalSignatureUtils.hmacSha256Hex(internalSecret, legacyPayload));
        replaceHeader(
                template,
                HeaderConstants.USER_CONTEXT_SIGNATURE_V2,
                InternalSignatureUtils.hmacSha256Hex(internalSecret, payloadV2));
    }

    private String firstHeader(RequestTemplate template, String headerName) {
        return template.headers().getOrDefault(headerName, List.of()).stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String bodySha256(RequestTemplate template) {
        if (isMultipart(template)) {
            return InternalSignatureUtils.STREAMING_BODY_SHA256;
        }
        return InternalSignatureUtils.sha256Hex(template.body());
    }

    private boolean isMultipart(RequestTemplate template) {
        return template.headers().entrySet().stream()
                .filter(entry -> HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));
    }

    private void removeTrustedSignatureHeaders(RequestTemplate template) {
        template.removeHeader(HeaderConstants.USER_ID);
        template.removeHeader(HeaderConstants.USERNAME);
        template.removeHeader(HeaderConstants.ROLES);
        template.removeHeader(HeaderConstants.USER_CONTEXT_TIMESTAMP);
        template.removeHeader(HeaderConstants.USER_CONTEXT_NONCE);
        template.removeHeader(HeaderConstants.USER_CONTEXT_SIGNER);
        template.removeHeader(HeaderConstants.USER_CONTEXT_SIGNATURE);
        template.removeHeader(HeaderConstants.USER_CONTEXT_SIGNATURE_V2);
        template.removeHeader(HeaderConstants.INTERNAL_CALL);
        template.removeHeader(HeaderConstants.SERVICE_NAME);
        template.removeHeader(HeaderConstants.INTERNAL_TIMESTAMP);
        template.removeHeader(HeaderConstants.INTERNAL_NONCE);
        template.removeHeader(HeaderConstants.INTERNAL_SIGNATURE);
        template.removeHeader(HeaderConstants.INTERNAL_SIGNATURE_V2);
        template.removeHeader(HeaderConstants.INTERNAL_BODY_SHA256);
    }

    private void replaceHeader(RequestTemplate template, String name, String value) {
        template.removeHeader(name);
        template.header(name, value);
    }
}
