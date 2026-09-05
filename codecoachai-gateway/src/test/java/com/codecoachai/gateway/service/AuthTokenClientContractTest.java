package com.codecoachai.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AuthTokenClientContractTest {

    @Test
    void tokenInfoInternalRequestTargetsTheCoreService() {
        assertEquals(
                "lb://codecoachai-core/inner/auth/token-info",
                AuthTokenClient.AUTH_TOKEN_INFO_URL);
    }
}
