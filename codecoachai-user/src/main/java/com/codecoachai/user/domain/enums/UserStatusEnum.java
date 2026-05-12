package com.codecoachai.user.domain.enums;

import lombok.Getter;

@Getter
public enum UserStatusEnum {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final Integer code;
    private final String label;

    UserStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(Integer code) {
        for (UserStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value.label;
            }
        }
        return "未知";
    }
}
