package com.codecoachai.common.core.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "请求参数错误"),
    VALIDATION_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(41000, "未登录"),
    TOKEN_INVALID(41001, "Token 无效或已过期"),
    FORBIDDEN(41003, "无访问权限"),
    USER_ERROR(42000, "用户模块错误"),
    USERNAME_EXISTS(42001, "用户名已存在"),
    USER_NOT_FOUND(42002, "用户不存在"),
    PASSWORD_ERROR(42003, "用户名或密码错误"),
    USER_DISABLED(42004, "账号已禁用"),
    PASSWORD_CONFIRM_NOT_MATCH(42006, "两次密码不一致"),
    OLD_PASSWORD_ERROR(42007, "原密码错误"),
    DISABLE_SELF_NOT_ALLOWED(42008, "不能禁用自己"),
    SYSTEM_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
