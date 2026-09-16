package com.codecoachai.common.core.util;

import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class PasswordStrength {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456",
            "12345678",
            "123456789",
            "password",
            "admin",
            "admin123",
            "qwerty",
            "111111",
            "000000",
            "abc123",
            "password1");

    private PasswordStrength() {
    }

    public static boolean isWeak(String password, String username) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            return true;
        }
        if (StringUtils.hasText(username) && password.equalsIgnoreCase(username.trim())) {
            return true;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            return true;
        }
        return COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT));
    }
}
