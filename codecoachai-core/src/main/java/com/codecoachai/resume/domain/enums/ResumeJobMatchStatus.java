package com.codecoachai.resume.domain.enums;

import java.util.Arrays;

public enum ResumeJobMatchStatus {

    PROCESSING("Processing"),
    RUNNING("Running"),
    SUCCESS("Generated successfully"),
    FAILED("Generation failed");

    private final String message;

    ResumeJobMatchStatus(String message) {
        this.message = message;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }

    public static ResumeJobMatchStatus of(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElse(null);
    }
}
