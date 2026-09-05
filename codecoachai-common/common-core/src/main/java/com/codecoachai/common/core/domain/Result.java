package com.codecoachai.common.core.domain;

import com.codecoachai.common.core.enums.ErrorCode;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result<T> {

    private Integer code;
    private String message;
    private T data;
    private String traceId;
    private Boolean retryable;
    private String nextStep;
    private Map<String, String> fieldErrors;

    public Result(Integer code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> fail(Integer code, String message, Boolean retryable,
                                     String nextStep, Map<String, String> fieldErrors) {
        Result<T> result = fail(code, message);
        result.setRetryable(retryable);
        result.setNextStep(nextStep);
        result.setFieldErrors(fieldErrors);
        return result;
    }

    public boolean isSuccess() {
        return code != null && code == ErrorCode.SUCCESS.getCode();
    }
}
