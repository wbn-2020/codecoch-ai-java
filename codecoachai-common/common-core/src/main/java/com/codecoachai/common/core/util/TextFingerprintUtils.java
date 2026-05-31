package com.codecoachai.common.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class TextFingerprintUtils {

    public static final String NORMALIZATION_VERSION = "text-fingerprint-v1";

    private TextFingerprintUtils() {
    }

    public static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    public static String normalizeFingerprint(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return normalizeContent(content)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]+", "");
    }

    public static String sha256Hex(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    public static String fingerprintHash(String content) {
        return sha256Hex(normalizeFingerprint(content));
    }
}
