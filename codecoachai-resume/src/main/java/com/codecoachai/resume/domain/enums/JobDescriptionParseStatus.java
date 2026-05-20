package com.codecoachai.resume.domain.enums;

import java.util.Arrays;

public enum JobDescriptionParseStatus {

    NOT_PARSED("Not parsed"),
    PARSING("Parsing in progress"),
    PARSED("Parsed successfully"),
    FAILED("Parsing failed");

    private final String message;

    JobDescriptionParseStatus(String message) {
        this.message = message;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }

    public static JobDescriptionParseStatus of(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElse(null);
    }
}
