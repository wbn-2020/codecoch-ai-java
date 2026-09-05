package com.codecoachai.resume.domain.enums;

import lombok.Getter;

@Getter
public enum SkillProfileStatus {

    PROCESSING("PROCESSING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String code;

    SkillProfileStatus(String code) {
        this.code = code;
    }
}
