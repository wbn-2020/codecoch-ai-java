package com.codecoachai.core.local;

import com.codecoachai.common.core.domain.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalResultMapper {

    private final ObjectMapper objectMapper;

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
}
