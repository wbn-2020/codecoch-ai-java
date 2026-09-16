package com.codecoachai.common.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordStrengthTest {

    @Test
    void rejectsShortUsernameMatchLetterOnlyAndCommonPasswords() {
        assertTrue(PasswordStrength.isWeak("abc123", "alice"));
        assertTrue(PasswordStrength.isWeak("admin123", "admin"));
        assertTrue(PasswordStrength.isWeak("alice", "alice"));
        assertTrue(PasswordStrength.isWeak("abcdefgh", "alice"));
        assertTrue(PasswordStrength.isWeak("12345678", "alice"));
    }

    @Test
    void acceptsLetterDigitPasswordOfAtLeastEightChars() {
        assertFalse(PasswordStrength.isWeak("Alpha123", "alice"));
    }
}
