package com.codecoachai.auth.service.impl;

import com.codecoachai.auth.service.PasswordResetDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MockPasswordResetDeliveryService implements PasswordResetDeliveryService {

    @Override
    public void sendResetToken(Long userId, String email, String token, long expiresInSeconds) {
        log.info("MOCK_PASSWORD_RESET_DELIVERY userId={} email={} resetPath=/reset-password?token={} expiresInSeconds={}",
                userId, maskEmail(email), token, expiresInSeconds);
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
}
