package com.codecoachai.common.security.util;

import com.codecoachai.common.core.constant.HeaderConstants;
import com.codecoachai.common.core.util.InternalSignatureUtils;
import com.codecoachai.common.security.config.InternalAuthProperties;
import com.codecoachai.common.security.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.util.StringUtils;

public final class HeaderUserContextReader {

    private HeaderUserContextReader() {
    }

    public static LoginUser read(HttpServletRequest request, InternalAuthProperties properties) {
        String userIdValue = request.getHeader(HeaderConstants.USER_ID);
        if (!StringUtils.hasText(userIdValue)) {
            return null;
        }
        validateUserContextSignature(request, properties, userIdValue);
        Long userId = parseLong(userIdValue);
        if (userId == null) {
            return null;
        }
        return LoginUser.builder()
                .userId(userId)
                .username(request.getHeader(HeaderConstants.USERNAME))
                .roles(parseRoles(request.getHeader(HeaderConstants.ROLES)))
                .build();
    }

    private static void validateUserContextSignature(HttpServletRequest request, InternalAuthProperties properties,
            String userIdValue) {
        if (properties == null || !StringUtils.hasText(properties.getSecret())) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }
        String timestamp = request.getHeader(HeaderConstants.USER_CONTEXT_TIMESTAMP);
        String signature = request.getHeader(HeaderConstants.USER_CONTEXT_SIGNATURE);
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            throw new IllegalStateException("trusted user context signature is missing");
        }
        if (!isTimestampValid(timestamp, properties.getAllowedClockSkewSeconds())) {
            throw new IllegalStateException("trusted user context signature is expired or invalid");
        }
        String payload = InternalSignatureUtils.userContextPayload(
                request.getMethod(),
                normalizeRequestPath(request),
                timestamp,
                userIdValue,
                request.getHeader(HeaderConstants.USERNAME),
                request.getHeader(HeaderConstants.ROLES));
        String expected = InternalSignatureUtils.hmacSha256Hex(properties.getSecret(), payload);
        if (!InternalSignatureUtils.constantTimeEquals(expected, signature)) {
            throw new IllegalStateException("trusted user context signature mismatch");
        }
    }

    private static boolean isTimestampValid(String timestamp, long allowedClockSkewSeconds) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long allowedSkewMillis = Duration.ofSeconds(allowedClockSkewSeconds).toMillis();
            return Math.abs(System.currentTimeMillis() - requestTime) <= allowedSkewMillis;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String normalizeRequestPath(HttpServletRequest request) {
        String path = InternalSignatureUtils.normalizePath(request.getRequestURI());
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            return InternalSignatureUtils.normalizePath(path.substring(contextPath.length()));
        }
        return path;
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> parseRoles(String roles) {
        if (!StringUtils.hasText(roles)) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
