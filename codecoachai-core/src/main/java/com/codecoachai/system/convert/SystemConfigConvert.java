package com.codecoachai.system.convert;

import com.codecoachai.system.domain.entity.SystemConfig;
import com.codecoachai.system.domain.vo.SystemConfigVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class SystemConfigConvert {

    private SystemConfigConvert() {
    }

    public static SystemConfigVO toVO(SystemConfig config) {
        SystemConfigVO vo = new SystemConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        boolean sensitive = isSensitiveKey(config.getConfigKey());
        vo.setConfigValue(sensitive ? null : config.getConfigValue());
        vo.setConfigValueMasked(sensitive ? maskSecret(config.getConfigValue()) : config.getConfigValue());
        vo.setConfigValueHash(sha256Prefix(config.getConfigValue()));
        vo.setSensitiveConfig(sensitive);
        vo.setRawAccessPermission("admin:system:config:raw:view");
        vo.setConfigType(config.getValueType());
        vo.setValueType(config.getValueType());
        vo.setDescription(config.getDescription());
        vo.setStatus(config.getStatus());
        return vo;
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("access_key")
                || lower.contains("private")
                || lower.contains("credential");
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "******";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static String sha256Prefix(String value) {
        if (value == null || value.isBlank()) {
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
