package com.codecoachai.auth.service;

import com.codecoachai.auth.config.PasswordResetProperties;
import com.codecoachai.auth.service.impl.MailPasswordResetDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailPasswordResetDeliveryServiceTest {

    @Test
    void sendResetTokenUsesFragmentTokenInResetUrl() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setDeliveryProvider("mail");

        MailPasswordResetDeliveryService service = new MailPasswordResetDeliveryService(mailSender, properties);

        service.sendResetToken(1L, "user@example.com", "abc+123/=", 900);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        assertThat(messageCaptor.getValue().getText())
                .contains("/reset-password#token=abc%2B123%2F%3D")
                .doesNotContain("/reset-password?token=");
    }
}
