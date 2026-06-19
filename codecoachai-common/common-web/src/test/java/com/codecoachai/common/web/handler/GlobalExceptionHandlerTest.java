package com.codecoachai.common.web.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void sanitizeMasksCommonSecretsAndPii() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Method sanitize = GlobalExceptionHandler.class.getDeclaredMethod("sanitize", String.class);
        sanitize.setAccessible(true);

        String result = (String) sanitize.invoke(handler,
                "email=alice@example.com phone=13812345678 token=abc.def Authorization: Bearer sk-live-123 apiKey=secret-key password=p@ss");

        assertFalse(result.contains("alice@example.com"));
        assertFalse(result.contains("13812345678"));
        assertFalse(result.contains("abc.def"));
        assertFalse(result.contains("sk-live-123"));
        assertFalse(result.contains("secret-key"));
        assertFalse(result.contains("p@ss"));
        assertTrue(result.contains("***@***"));
        assertTrue(result.contains("1**********"));
    }
}
