package com.codecoachai.ai.security;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AesGcmTextEncryptor {

    private static final String PREFIX = "{aes-gcm-v1}";
    private static final String BASE64_KEY_PREFIX = "base64:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MIN_TEXT_KEY_LENGTH = 16;

    private final AiCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void warnIfMissingKey() {
        if (!StringUtils.hasText(properties.getSecretKey())) {
            log.warn("SECURITY_AI_MODEL_API_KEY_CRYPTO_KEY_MISSING: set codecoachai.ai.crypto.secret-key "
                    + "or CODECOACHAI_AI_CRYPTO_SECRET_KEY before saving AI model apiKey values");
        }
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        String value = plainText.trim();
        if (isEncrypted(value)) {
            return value;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt AI model apiKey", ex);
        }
    }

    public String decryptIfNeeded(String storedText) {
        if (!StringUtils.hasText(storedText)) {
            return storedText;
        }
        String value = storedText.trim();
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("Invalid encrypted AI model apiKey payload");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt AI model apiKey", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith(PREFIX);
    }

    private SecretKeySpec resolveKey() {
        String configuredKey = properties.getSecretKey();
        if (!StringUtils.hasText(configuredKey)) {
            throw new IllegalStateException("AI model apiKey encryption key is not configured");
        }
        String value = configuredKey.trim();
        byte[] keyBytes;
        if (value.startsWith(BASE64_KEY_PREFIX)) {
            try {
                keyBytes = Base64.getDecoder().decode(value.substring(BASE64_KEY_PREFIX.length()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("AI model apiKey base64 encryption key is invalid", ex);
            }
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalStateException("AI model apiKey base64 encryption key must be 16, 24, or 32 bytes");
            }
        } else {
            if (value.length() < MIN_TEXT_KEY_LENGTH) {
                throw new IllegalStateException("AI model apiKey encryption key must be at least 16 characters");
            }
            try {
                keyBytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is not available", ex);
            }
        }
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
}
