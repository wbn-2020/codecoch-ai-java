package com.codecoachai.common.web.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
            "(?i)(passwordHash|password|passwd|pwd|token|accessToken|refreshToken|authorization|api[-_]?key|secret|clientSecret|privateKey)\\s*[:=]\\s*([^,\\s}\"']+)");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]{2,}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern CHINA_MOBILE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        return response(withTrace(Result.fail(ex.getCode(), ex.getMessage())), httpStatusFor(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return response(withTrace(Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), message)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return response(withTrace(Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), message)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return response(withTrace(Result.fail(ErrorCode.VALIDATION_ERROR.getCode(), message)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return response(
                withTrace(Result.fail(ErrorCode.PARAM_ERROR.getCode(), ex.getParameterName() + "不能为空")),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null) {
            response.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(withTrace(Result.fail(
                ErrorCode.PARAM_ERROR.getCode(),
                "HTTP method " + ex.getMethod() + " is not supported")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return response(
                withTrace(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "Request media type is not supported")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return response(
                withTrace(Result.fail(ErrorCode.PARAM_ERROR.getCode(), ex.getName() + " has an invalid value")),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return response(
                withTrace(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "Request body is missing or malformed")),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Result<Void>> handleRequestBinding(ServletRequestBindingException ex) {
        return response(
                withTrace(Result.fail(ErrorCode.PARAM_ERROR.getCode(), "Required request metadata is missing")),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        return response(withTrace(Result.fail(ErrorCode.UNAUTHORIZED)), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(NotPermissionException ex) {
        return response(withTrace(Result.fail(ErrorCode.FORBIDDEN)), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled system exception type={} message={} location={} traceId={}",
                ex.getClass().getName(), sanitize(ex.getMessage()), exceptionLocation(ex), MDC.get("traceId"));
        return response(withTrace(Result.fail(ErrorCode.SYSTEM_ERROR)), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 将 traceId 注入到 Result 中，方便前端排查问题。
     */
    private <T> Result<T> withTrace(Result<T> result) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            result.setTraceId(traceId);
        }
        return result;
    }

    private <T> ResponseEntity<Result<T>> response(Result<T> result, HttpStatus status) {
        return ResponseEntity.status(status).body(result);
    }

    private HttpStatus httpStatusFor(BusinessException ex) {
        if (ex.getHttpStatus() != null) {
            HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
            if (status != null) {
                return status;
            }
        }
        Integer code = ex.getCode();
        HttpStatus domainStatus = ErrorCode.fromCode(code)
                .map(ErrorCode::getHttpStatus)
                .map(HttpStatus::resolve)
                .orElse(null);
        if (domainStatus != null) {
            return domainStatus;
        }
        return HttpStatus.OK;
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    private String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String sanitized = BEARER_TOKEN_PATTERN.matcher(message).replaceAll("Bearer ***");
        sanitized = KEY_VALUE_SECRET_PATTERN.matcher(sanitized).replaceAll("$1=***");
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("***@***");
        sanitized = CHINA_MOBILE_PATTERN.matcher(sanitized).replaceAll("1**********");
        return sanitized;
    }

    private String exceptionLocation(Exception ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            return "unknown";
        }
        StackTraceElement first = stackTrace[0];
        return first.getClassName() + "#" + first.getMethodName() + ":" + first.getLineNumber();
    }
}
