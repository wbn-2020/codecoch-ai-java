package com.codecoachai.common.web.advice;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.filter.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ResultTraceResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "traceId";

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof Result<?> result && !StringUtils.hasText(result.getTraceId())) {
            String traceId = resolveTraceId(request);
            if (StringUtils.hasText(traceId)) {
                result.setTraceId(traceId);
            }
        }
        return body;
    }

    private String resolveTraceId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String traceId = normalized(servletRequest.getServletRequest()
                    .getAttribute(TraceIdFilter.TRACE_ID_REQUEST_ATTRIBUTE));
            if (traceId != null) {
                return traceId;
            }
        }
        if (request != null) {
            String traceId = normalized(request.getHeaders().getFirst(HEADER_TRACE_ID));
            if (traceId != null) {
                return traceId;
            }
        }
        return normalized(MDC.get(MDC_TRACE_ID));
    }

    private String normalized(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
