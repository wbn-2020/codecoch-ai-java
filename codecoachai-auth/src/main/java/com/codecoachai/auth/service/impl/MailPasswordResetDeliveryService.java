package com.codecoachai.auth.service.impl;

import com.codecoachai.auth.config.PasswordResetProperties;
import com.codecoachai.auth.service.PasswordResetDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "codecoachai.auth.password-reset", name = "delivery-provider", havingValue = "mail")
public class MailPasswordResetDeliveryService implements PasswordResetDeliveryService {

    private final JavaMailSender mailSender;
    private final PasswordResetProperties properties;

    @Override
    public void sendResetToken(Long userId, String email, String token, long expiresInSeconds) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(properties.getFrom())) {
            message.setFrom(properties.getFrom());
        }
        message.setTo(email);
        message.setSubject(properties.getSubject());
        message.setText(buildBody(token, expiresInSeconds));
        mailSender.send(message);
    }

    private String buildBody(String token, long expiresInSeconds) {
        String resetUrl = properties.getResetUrlTemplate().replace("{token}", token);
        long minutes = Math.max(1, expiresInSeconds / 60);
        return """
                You requested a CodeCoachAI password reset.

                Open this link within %d minutes:
                %s

                If you did not request this, ignore this email.
                """.formatted(minutes, resetUrl);
    }
}
