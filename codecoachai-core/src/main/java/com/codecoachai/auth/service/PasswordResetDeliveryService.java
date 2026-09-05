package com.codecoachai.auth.service;

public interface PasswordResetDeliveryService {

    void sendResetToken(Long userId, String email, String token, long expiresInSeconds);
}
