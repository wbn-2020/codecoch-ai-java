package com.codecoachai.common.security.filter;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalCallFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalCallFilter.class);

    private static final Set<String> ALLOWED_SERVICES = Set.of(
            "codecoachai-gateway",
            "codecoachai-auth",
            "codecoachai-user",
            "codecoachai-question",
            "codecoachai-resume",
            "codecoachai-file",
            "codecoachai-interview",
            "codecoachai-ai",
            "codecoachai-system",
            "codecoachai-task",
            "codecoachai-search"
    );

    private static final String INTERNAL_NONCE_KEY_PREFIX = "codecoachai:internal:nonce:";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InternalAuthProperties internalAuthProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public InternalCallFilter(InternalAuthProperties internalAuthProperties, StringRedisTemplate stringRedisTemplate) {
        this.internalAuthProperties = internalAuthProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = normalizeRequestPath(request);
        if (!path.startsWith("/inner/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String internalCall = request.getHeader(HeaderConstants.INTERNAL_CALL);
        String serviceName = request.getHeader(HeaderConstants.SERVICE_NAME);
        if (!"true".equalsIgnoreCase(internalCall)) {
            log.warn("Reject internal request: invalid internal flag, path={}, serviceName={}", path, serviceName);
            writeForbidden(response);
            return;
        }
        if (!StringUtils.hasText(serviceName)) {
            log.warn("Reject internal request: missing service name, path={}", path);
            writeForbidden(response);
            return;
        }
        if (!ALLOWED_SERVICES.contains(serviceName)) {
            log.warn("Reject internal request: service not allowed, path={}, serviceName={}", path, serviceName);
            writeForbidden(response);
            return;
        }

        if (internalAuthProperties.isEnabled() && !verifySignature(request, path, serviceName, response)) {
            writeForbidden(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean verifySignature(HttpServletRequest request, String path, String serviceName,
            HttpServletResponse response) {
        if (!StringUtils.hasText(internalAuthProperties.getSecret())) {
            log.warn("Reject internal request: internal secret not configured, path={}, serviceName={}", path,
                    serviceName);
            return false;
        }

        String timestamp = request.getHeader(HeaderConstants.INTERNAL_TIMESTAMP);
        String nonce = request.getHeader(HeaderConstants.INTERNAL_NONCE);
        String signature = request.getHeader(HeaderConstants.INTERNAL_SIGNATURE);
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            log.warn("Reject internal request: missing signature headers, path={}, serviceName={}", path, serviceName);
            return false;
        }

        if (!isTimestampValid(timestamp)) {
            log.warn("Reject internal request: invalid or expired timestamp, path={}, serviceName={}", path,
                    serviceName);
            return false;
        }

        if (!markNonceUsed(serviceName, nonce, path)) {
            return false;
        }

        String payload = InternalSignatureUtils.canonicalPayload(
                request.getMethod(), path, timestamp, nonce, serviceName);
        String expectedSignature = InternalSignatureUtils.hmacSha256Hex(internalAuthProperties.getSecret(), payload);
        boolean matched = InternalSignatureUtils.constantTimeEquals(expectedSignature, signature);
        if (!matched) {
            log.warn("Reject internal request: signature mismatch, path={}, serviceName={}", path, serviceName);
        }
        return matched;
    }

    private boolean isTimestampValid(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long allowedSkewMillis = Duration.ofSeconds(internalAuthProperties.getAllowedClockSkewSeconds()).toMillis();
            return Math.abs(System.currentTimeMillis() - requestTime) <= allowedSkewMillis;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean markNonceUsed(String serviceName, String nonce, String path) {
        try {
            String key = INTERNAL_NONCE_KEY_PREFIX + serviceName + ":" + nonce;
            Boolean inserted = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(internalAuthProperties.getNonceTtlSeconds()));
            if (!Boolean.TRUE.equals(inserted)) {
                log.warn("Reject internal request: nonce replay detected, path={}, serviceName={}", path, serviceName);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("Reject internal request: redis nonce check failed, path={}, serviceName={}, error={}", path,
                    serviceName, ex.getClass().getSimpleName());
            return false;
        }
    }

    private String normalizeRequestPath(HttpServletRequest request) {
        String path = InternalSignatureUtils.normalizePath(request.getRequestURI());
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            String withoutContextPath = path.substring(contextPath.length());
            return InternalSignatureUtils.normalizePath(withoutContextPath);
        }
        return path;
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
    }
}
