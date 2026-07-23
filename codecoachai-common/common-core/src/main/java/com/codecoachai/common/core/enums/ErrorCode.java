package com.codecoachai.common.core.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40000, "请求参数错误"),
    VALIDATION_ERROR(40001, "参数校验失败"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    RESUME_UPLOAD_BUSY(42910, "Resume upload is busy", 429),
    UPLOAD_INTERRUPTED(50310, "Upload admission was interrupted", 503),
    UNAUTHORIZED(41000, "未登录", 401),
    TOKEN_INVALID(41001, "Token 无效或已过期", 401),
    FORBIDDEN(41003, "无访问权限", 403),
    USER_ERROR(42000, "用户模块错误"),
    USERNAME_EXISTS(42001, "用户名已存在"),
    USER_NOT_FOUND(42002, "用户不存在"),
    PASSWORD_ERROR(42003, "用户名或密码错误"),
    USER_DISABLED(42004, "账号已禁用"),
    ACCOUNT_LOCKED(42005, "账号已被临时锁定，请15分钟后再试"),
    PASSWORD_CONFIRM_NOT_MATCH(42006, "两次密码不一致"),
    OLD_PASSWORD_ERROR(42007, "原密码错误"),
    DISABLE_SELF_NOT_ALLOWED(42008, "不能禁用自己"),
    RESOURCE_NOT_FOUND(40400, "Resource not found", 404),
    STALE_SOURCE_VERSION(40901, "Source version is stale", 409),
    RESOURCE_RELATION_CONFLICT(40902, "Resource relation conflict", 409),
    SEMANTIC_VALIDATION_ERROR(42200, "Semantic validation failed", 422),
    SNAPSHOT_NOT_COMPARABLE(42201, "Snapshots are not comparable", 422),
    SYSTEM_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;
    private final Integer httpStatus;

    ErrorCode(int code, String message) {
        this(code, message, null);
    }

    ErrorCode(int code, String message, Integer httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public static Optional<ErrorCode> fromCode(Integer code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst();
    }
}
