package com.codecoachai.resume.domain.enums;

import java.util.Arrays;

public enum ResumeParseStatus {

    PENDING("等待解析"),
    PARSING("解析中"),
    SUCCESS("解析成功"),
    FAILED("解析失败"),
    WAIT_CONFIRM("等待用户确认");

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
