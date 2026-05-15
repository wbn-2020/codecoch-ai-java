package com.codecoachai.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        int queryIndex = path.indexOf('?');
        String normalized = queryIndex >= 0 ? path.substring(0, queryIndex) : path;
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
