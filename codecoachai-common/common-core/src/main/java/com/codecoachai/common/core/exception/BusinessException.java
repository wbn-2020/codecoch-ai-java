package com.codecoachai.common.core.exception;

import com.codecoachai.common.core.enums.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;
    private final Integer httpStatus;
    private final Boolean retryable;
    private final String nextStep;
    private final Map<String, String> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), errorCode.getHttpStatus(), message, null, null, null);
    }

    public BusinessException(Integer code, String message) {
        this(code, ErrorCode.fromCode(code).map(ErrorCode::getHttpStatus).orElse(null),
                message, null, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Boolean retryable,
                             String nextStep, Map<String, String> fieldErrors) {
        this(errorCode.getCode(), errorCode.getHttpStatus(), message,
                retryable, nextStep, fieldErrors);
    }

    public static BusinessException field(ErrorCode errorCode, String field, String message,
                                          String nextStep) {
        return new BusinessException(
                errorCode,
                message,
                false,
                nextStep,
                Map.of(field, message));
    }

    public static BusinessException retryable(ErrorCode errorCode, String message, String nextStep) {
        return new BusinessException(errorCode, message, true, nextStep, null);
    }

    private BusinessException(Integer code, Integer httpStatus, String message, Boolean retryable,
                              String nextStep, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.nextStep = nextStep;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty()
                ? null
                : Map.copyOf(new LinkedHashMap<>(fieldErrors));
    }
}
