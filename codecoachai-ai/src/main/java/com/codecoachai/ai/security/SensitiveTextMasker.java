package com.codecoachai.ai.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class SensitiveTextMasker {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{6}(?:19|20)\\d{2}\\d{2}\\d{2}\\d{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\"(?:api[-_]?key|authorization|bearer|token|password|secret)\"\\s*:\\s*\")[^\"]+(\")");
    private static final Pattern KV_SECRET = Pattern.compile("(?i)\\b(api[-_ ]?key|authorization|bearer|token|password|secret)\\b\\s*[:=]\\s*([^\\s,;]+)");

    private SensitiveTextMasker() {
    }

    public static String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "******";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    public static int length(String value) {
        return value == null ? 0 : value.length();
    }

    public static String maskText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String masked = EMAIL.matcher(value).replaceAll("***@***");
        masked = CHINA_MOBILE.matcher(masked).replaceAll("1**********");
        masked = ID_CARD.matcher(masked).replaceAll("******************");
        masked = JSON_SECRET.matcher(masked).replaceAll("$1******$2");
        return KV_SECRET.matcher(masked).replaceAll("$1=******");
    }

    public static String safePreview(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        String preview = normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
        return maskText(preview);
    }

    public static String sha256Prefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }
}
