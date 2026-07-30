package com.codecoachai.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class InternalSignatureUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_V2 = "codecoachai-signature-v2";

    public static final String EMPTY_BODY_SHA256 = sha256Hex(new byte[0]);
    public static final String STREAMING_BODY_SHA256 = "STREAMING-UNSIGNED-PAYLOAD";

    private InternalSignatureUtils() {
    }

    public static String canonicalPayload(String method, String path, String timestamp, String nonce,
            String serviceName) {
        return nullToEmpty(method).toUpperCase(Locale.ROOT)
                + "\n" + normalizePath(path)
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + serviceName;
    }

    public static String userContextPayload(String method, String path, String timestamp, String userId,
            String username, String roles) {
        return nullToEmpty(method).toUpperCase(Locale.ROOT)
                + "\n" + normalizePath(path)
                + "\n" + nullToEmpty(timestamp)
                + "\n" + nullToEmpty(userId)
                + "\n" + nullToEmpty(username)
                + "\n" + nullToEmpty(roles);
    }

    public static String internalRequestPayloadV2(String method, String path, String rawQuery, String timestamp,
            String nonce, String serviceName, String bodySha256) {
        return canonicalV2(
                "internal-request",
                normalizeMethod(method),
                normalizePath(path),
                normalizeQuery(rawQuery),
                timestamp,
                nonce,
                serviceName,
                bodySha256);
    }

    public static String userContextPayloadV2(String method, String path, String rawQuery, String timestamp,
            String nonce, String signer, String bodySha256, String userId, String username, String roles) {
        return canonicalV2(
                "user-context",
                normalizeMethod(method),
                normalizePath(path),
                normalizeQuery(rawQuery),
                timestamp,
                nonce,
                signer,
                bodySha256,
                userId,
                username,
                roles);
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

    public static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload == null ? new byte[0] : payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate SHA-256 digest", ex);
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
        normalized = normalizePercentEncoding(normalized);
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

    public static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String query = rawQuery.trim();
        if (query.startsWith("?")) {
            query = query.substring(1);
        }
        int fragmentIndex = query.indexOf('#');
        if (fragmentIndex >= 0) {
            query = query.substring(0, fragmentIndex);
        }
        if (query.isEmpty()) {
            return "";
        }

        String[] rawParts = query.split("&", -1);
        List<QueryPart> parts = new ArrayList<>(rawParts.length);
        for (int index = 0; index < rawParts.length; index++) {
            String rawPart = rawParts[index];
            int equalsIndex = rawPart.indexOf('=');
            boolean hasEquals = equalsIndex >= 0;
            String name = hasEquals ? rawPart.substring(0, equalsIndex) : rawPart;
            String value = hasEquals ? rawPart.substring(equalsIndex + 1) : "";
            parts.add(new QueryPart(
                    normalizePercentEncoding(name),
                    normalizePercentEncoding(value),
                    hasEquals,
                    index));
        }
        parts.sort(Comparator.comparing(QueryPart::name).thenComparingInt(QueryPart::originalIndex));

        StringBuilder normalized = new StringBuilder(query.length());
        for (QueryPart part : parts) {
            if (!normalized.isEmpty()) {
                normalized.append('&');
            }
            normalized.append(part.name());
            if (part.hasEquals()) {
                normalized.append('=').append(part.value());
            }
        }
        return normalized.toString();
    }

    public static String rawQueryFromTarget(String requestTarget) {
        if (requestTarget == null) {
            return "";
        }
        int queryIndex = requestTarget.indexOf('?');
        if (queryIndex < 0 || queryIndex == requestTarget.length() - 1) {
            return "";
        }
        int fragmentIndex = requestTarget.indexOf('#', queryIndex + 1);
        return fragmentIndex >= 0
                ? requestTarget.substring(queryIndex + 1, fragmentIndex)
                : requestTarget.substring(queryIndex + 1);
    }

    private static String canonicalV2(String kind, String... fields) {
        StringBuilder payload = new StringBuilder(SIGNATURE_V2)
                .append('\n')
                .append(kind)
                .append('\n');
        for (String field : fields) {
            String value = nullToEmpty(field);
            payload.append(value.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(value)
                    .append('\n');
        }
        return payload.toString();
    }

    private static String normalizeMethod(String method) {
        return nullToEmpty(method).toUpperCase(Locale.ROOT);
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

    private static String normalizePercentEncoding(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '%' && i + 2 < value.length()
                    && isHexCharacter(value.charAt(i + 1))
                    && isHexCharacter(value.charAt(i + 2))) {
                char high = Character.toUpperCase(value.charAt(i + 1));
                char low = Character.toUpperCase(value.charAt(i + 2));
                int decoded = Character.digit(high, 16) * 16 + Character.digit(low, 16);
                if (isUnreserved(decoded)) {
                    builder.append((char) decoded);
                } else {
                    builder.append('%').append(high).append(low);
                }
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

    private static boolean isUnreserved(int value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '-'
                || value == '.'
                || value == '_'
                || value == '~';
    }

    private record QueryPart(String name, String value, boolean hasEquals, int originalIndex) {
    }
}
