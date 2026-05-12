package com.codecoachai.common.core.util;

public final class StringUtils {

    private StringUtils() {
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
