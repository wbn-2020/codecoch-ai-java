package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalResultMapper {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public <T> Result<T> invoke(Supplier<Result<T>> invocation) {
        try {
            return invocation.get();
        } catch (BusinessException ex) {
            return Result.fail(ex.getCode(), ex.getMessage());
        }
    }

    public <T> Result<T> value(Result<?> source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        T data = source.getData() == null ? null : objectMapper.convertValue(source.getData(), targetType);
        return new Result<>(source.getCode(), source.getMessage(), data, source.getTraceId());
    }

    public Result<Void> empty(Result<?> source) {
        if (source == null) {
            return null;
        }
        return new Result<>(source.getCode(), source.getMessage(), null, source.getTraceId());
    }

    public <T> Result<List<T>> values(Result<?> source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        List<T> data = source.getData() == null
                ? null
                : ((Collection<?>) source.getData()).stream()
                        .map(item -> objectMapper.convertValue(item, targetType))
                        .toList();
        return new Result<>(source.getCode(), source.getMessage(), data, source.getTraceId());
    }

    public <T> T convert(Object source, Class<T> targetType) {
        return source == null ? null : objectMapper.convertValue(source, targetType);
    }

    public <T> T convertRequiredBody(Object source, Class<T> targetType) {
        requireRequestBody(source);
        return convertRequestBody(source, targetType);
    }

    public <T> T convertValidatedBody(Object source, Class<T> targetType) {
        T target = convertRequiredBody(source, targetType);
        String message = validator.validate(target).stream()
                .sorted(Comparator
                        .comparing((ConstraintViolation<T> violation) -> violation.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        if (!message.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return target;
    }

    public void requireParameter(Object value, String name) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, name + "不能为空");
        }
    }

    private void requireRequestBody(Object source) {
        if (source == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Request body is missing or malformed");
        }
    }

    private <T> T convertRequestBody(Object source, Class<T> targetType) {
        try {
            return objectMapper.convertValue(source, targetType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Request body is missing or malformed");
        }
    }
}
