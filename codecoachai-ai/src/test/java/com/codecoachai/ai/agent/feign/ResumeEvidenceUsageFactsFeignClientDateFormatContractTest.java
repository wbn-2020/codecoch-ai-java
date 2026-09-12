package com.codecoachai.ai.agent.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 锁定 ResumeEvidenceUsageFactsFeignClient 的 dataCutoffAt wire 格式：
 * 必须使用 ISO.DATE_TIME，避免跨服务因本地化日期格式不一致导致 400。
 */
class ResumeEvidenceUsageFactsFeignClientDateFormatContractTest {

    private final DefaultFormattingConversionService conversionService =
            new DefaultFormattingConversionService();

    @Test
    void dataCutoffAtIsSerializedAsIsoDateTime() throws Exception {
        MethodParameter methodParameter =
                requestParamMethodParameter("getFacts", "dataCutoffAt");
        Object rendered = conversionService.convert(
                LocalDateTime.of(2026, 9, 11, 8, 30, 15),
                new TypeDescriptor(methodParameter),
                TypeDescriptor.valueOf(String.class));

        assertEquals("2026-09-11T08:30:15", rendered);
    }

    @Test
    void dataCutoffAtDeclaresIsoDateTimeFormat() {
        MethodParameter methodParameter =
                requestParamMethodParameter("getFacts", "dataCutoffAt");
        DateTimeFormat annotation = methodParameter.getParameterAnnotation(DateTimeFormat.class);

        assertNotNull(annotation, "dataCutoffAt must declare @DateTimeFormat(iso = ISO.DATE_TIME)");
        assertSame(DateTimeFormat.ISO.DATE_TIME, annotation.iso());
    }

    private MethodParameter requestParamMethodParameter(String methodName, String paramName) {
        Class<?> client = ResumeEvidenceUsageFactsFeignClient.class;
        for (Method method : client.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Parameter[] parameters = method.getParameters();
            for (int index = 0; index < parameters.length; index++) {
                RequestParam requestParam = parameters[index].getAnnotation(RequestParam.class);
                if (requestParam != null && List.of(requestParam.value()).contains(paramName)) {
                    return new MethodParameter(method, index);
                }
            }
        }
        throw new IllegalStateException(
                "RequestParam not found: " + methodName + "(" + paramName + ")");
    }
}
