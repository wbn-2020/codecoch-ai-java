package com.codecoachai.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class InternalSignatureUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private InternalSignatureUtils() {
    }

    public static String canonicalPayload(String method, String path, String timestamp, String nonce,
            String serviceName) {
        return method.toUpperCase()
                + "\n" + normalizePath(path)
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + serviceName;
    }

    public static String userContextPayload(String method, String path, String timestamp, String userId,
            String username, String roles) {
        return method.toUpperCase()
                + "\n" + normalizePath(path)
                + "\n" + nullToEmpty(timestamp)
                + "\n" + nullToEmpty(userId)
                + "\n" + nullToEmpty(username)
                + "\n" + nullToEmpty(roles);
    }

    public static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate internal request signature", ex);
        }
    }

    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = stripQueryAndFragment(path.trim());
        if (normalized.isEmpty()) {
            return "/";
        }
        normalized = normalized.replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = uppercasePercentEncoding(normalized);
        normalized = normalized.replaceAll("/+", "/");
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || isDotSegment(segment)) {
                continue;
            }
            if (isDotDotSegment(segment)) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.addLast(segment);
        }
        if (segments.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", segments);
    }

    public static String normalizeRequestPath(String requestUri, String contextPath) {
        String normalizedPath = normalizePath(requestUri);
        String normalizedContextPath = normalizePath(contextPath);
        if ("/".equals(normalizedContextPath)) {
            return normalizedPath;
        }
        if (normalizedPath.equals(normalizedContextPath)) {
            return "/";
        }
        if (normalizedPath.startsWith(normalizedContextPath + "/")) {
            return normalizePath(normalizedPath.substring(normalizedContextPath.length()));
        }
        return normalizedPath;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stripQueryAndFragment(String path) {
        int queryIndex = path.indexOf('?');
        int fragmentIndex = path.indexOf('#');
        int endIndex;
        if (queryIndex >= 0 && fragmentIndex >= 0) {
            endIndex = Math.min(queryIndex, fragmentIndex);
        } else if (queryIndex >= 0) {
            endIndex = queryIndex;
        } else {
            endIndex = fragmentIndex;
        }
        return endIndex >= 0 ? path.substring(0, endIndex) : path;
    }

    private static String uppercasePercentEncoding(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '%' && i + 2 < value.length()
                    && isHexCharacter(value.charAt(i + 1))
                    && isHexCharacter(value.charAt(i + 2))) {
                builder.append('%')
                        .append(Character.toUpperCase(value.charAt(i + 1)))
                        .append(Character.toUpperCase(value.charAt(i + 2)));
                i += 2;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static boolean isDotSegment(String segment) {
        return ".".equals(segment.replace("%2E", "."));
    }

    private static boolean isDotDotSegment(String segment) {
        return "..".equals(segment.replace("%2E", "."));
    }

    private static boolean isHexCharacter(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }
}
