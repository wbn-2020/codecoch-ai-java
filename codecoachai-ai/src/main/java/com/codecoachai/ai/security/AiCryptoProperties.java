package com.codecoachai.ai.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.ai.crypto")
public class AiCryptoProperties {

    /**
     * Set with codecoachai.ai.crypto.secret-key or CODECOACHAI_AI_CRYPTO_SECRET_KEY.
     * Plain text values must be at least 16 characters. Values prefixed with
     * base64: must decode to a 16, 24, or 32 byte AES key.
     */
    private String secretKey = "";
}
