package com.codecoachai.task.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 锁定跨服务日期参数的 wire 格式。
 *
 * <p>Spring Cloud OpenFeign 的 SpringMvcContract 用
 * {@code DefaultFormattingConversionService} 转换 query 参数，并把方法参数的注解
 * 通过 {@code TypeDescriptor(MethodParameter)} 传给转换器（见 SpringMvcContract 的
 * ConvertingExpanderFactory：convert(value, typeDescriptor, String)）。
 * 因此 {@code @DateTimeFormat(iso = ISO.DATE)} 决定实际发出的字符串。
 *
 * <p>缺注解时 JDK 默认日期格式会产出带斜杠的 {@code 2026/9/11}，
 * 接收端 Spring Boot 按 ISO 解析会直接 400（线上曾出现 planDate invalid value）。
 * 该测试复现同一条转换路径，防止回归。
 */
class FeignRequestDateFormatContractTest {

    private final DefaultFormattingConversionService conversionService =
            new DefaultFormattingConversionService();

    @Test
    void agentReminderPlanDateIsSerializedAsIsoDate() throws Exception {
        String rendered = renderRequestParam(
                AiFeignClient.class, "listReminderCandidates", "planDate", LocalDate.of(2026, 9, 11));

        assertEquals("2026-09-11", rendered);
    }

    @Test
    void applicationReminderDateIsSerializedAsIsoDate() throws Exception {
        String rendered = renderRequestParam(
                ResumeFeignClient.class, "listApplicationReminderCandidates", "date", LocalDate.of(2026, 9, 11));

        assertEquals("2026-09-11", rendered);
    }

    @Test
    void everyFeignLocalDateParamDeclaresIsoDateFormat() {
        assertRequestParamUsesIsoDate(AiFeignClient.class, "listReminderCandidates", "planDate");
        assertRequestParamUsesIsoDate(ResumeFeignClient.class, "listApplicationReminderCandidates", "date");
    }

    /**
     * 反证：同一条转换路径上，缺 @DateTimeFormat 的 LocalDate 会被格式化成带斜杠的本地化形式，
     * 这正是线上 planDate=2026/9/11 被接收端判为非法值的根因。用于确保上面的正向断言确实有区分度。
     */
    @Test
    void withoutAnnotationLocalDateFollowsLocaleDependentFormat() throws Exception {
        String rendered = renderBareLocalDate(LocalDate.of(2026, 9, 11));

        assertTrue(rendered.contains("/"),
                "Sanity check expects locale-dependent format (e.g. 2026/9/11) when @DateTimeFormat is absent, "
                        + "otherwise this contract test cannot detect the regression. Actual=" + rendered);
    }

    private interface BareLocalDateClient {
        String render(@RequestParam("date") LocalDate date);
    }

    private String renderBareLocalDate(LocalDate value) throws Exception {
        Method method = BareLocalDateClient.class.getDeclaredMethod("render", LocalDate.class);
        Object rendered = conversionService.convert(
                value, new TypeDescriptor(new MethodParameter(method, 0)), TypeDescriptor.valueOf(String.class));
        assertNotNull(rendered);
        return rendered.toString();
    }

    private String renderRequestParam(Class<?> client, String methodName, String paramName, Object value)
            throws Exception {
        MethodParameter methodParameter = requestParamMethodParameter(client, methodName, paramName);
        Object rendered = conversionService.convert(
                value, new TypeDescriptor(methodParameter), TypeDescriptor.valueOf(String.class));
        assertNotNull(rendered);
        return rendered.toString();
    }

    private void assertRequestParamUsesIsoDate(Class<?> client, String methodName, String paramName) {
        MethodParameter methodParameter = requestParamMethodParameter(client, methodName, paramName);
        DateTimeFormat annotation = methodParameter.getParameterAnnotation(DateTimeFormat.class);
        assertNotNull(annotation,
                "Feign date parameter must declare @DateTimeFormat to avoid locale-dependent wire format: "
                        + paramName);
        assertSame(DateTimeFormat.ISO.DATE, annotation.iso(),
                "Feign LocalDate parameter must use ISO.DATE: " + paramName);
    }

    private MethodParameter requestParamMethodParameter(Class<?> client, String methodName, String paramName) {
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
                "RequestParam not found: " + client.getSimpleName() + "#" + methodName + "(" + paramName + ")");
    }
}
