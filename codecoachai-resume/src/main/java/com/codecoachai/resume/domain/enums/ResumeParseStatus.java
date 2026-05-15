package com.codecoachai.resume.domain.enums;

import java.util.Arrays;

public enum ResumeParseStatus {

    PENDING("Waiting for parsing"),
    PARSING("Parsing in progress"),
    SUCCESS("Parsed successfully"),
    FAILED("Parsing failed"),
    WAIT_CONFIRM("Waiting for user confirmation");

    private final String message;

    ResumeParseStatus(String message) {
        this.message = message;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }

    public static ResumeParseStatus of(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElse(null);
    }
}
