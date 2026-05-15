package com.codecoachai.resume.domain.enums;

import java.util.Arrays;

public enum ResumeOptimizeStatus {

    PROCESSING,
    SUCCESS,
    FAILED;

    public String getCode() {
        return name();
    }

    public static ResumeOptimizeStatus of(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElse(null);
    }
}
