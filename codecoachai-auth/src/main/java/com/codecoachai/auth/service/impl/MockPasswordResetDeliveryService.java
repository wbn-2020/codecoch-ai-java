package com.codecoachai.auth.service.impl;

import com.codecoachai.auth.service.PasswordResetDeliveryService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class MockPasswordResetDeliveryService implements PasswordResetDeliveryService {

    @Override
    public void sendResetToken(Long userId, String email, String token, long expiresInSeconds) {
        log.info("MOCK_PASSWORD_RESET_DELIVERY userId={} email={} resetPath=/reset-password tokenMasked={} "
                        + "tokenSha256Prefix={} tokenLength={} expiresInSeconds={}",
                userId, maskEmail(email), maskSecret(token), sha256Prefix(token), length(token), expiresInSeconds);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "******";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String sha256Prefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }
}
