package com.codecoachai.common.web.log;

import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志 AOP 切面。
 * 拦截标注了 {@link OperationLog} 的 Controller 方法，异步写入 operation_log 表。
 *
 * 依赖：common-web 已引入 spring-boot-starter-web + common-security + common-mybatis（含 JdbcTemplate）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO operation_log (trace_id, user_id, username, module, action, target_type, target_id, " +
                    "method, request_uri, request_args, response, status, error_msg, ip, user_agent, cost_ms, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMsg = null;
        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAILED";
            errorMsg = truncate(ex.getMessage(), 1000);
            throw ex;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            try {
                saveLog(joinPoint, operationLog, status, errorMsg, result, costMs);
            } catch (Exception logEx) {
                log.warn("操作日志写入失败", logEx);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperationLog annotation,
                         String status, String errorMsg, Object result, long costMs) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        HttpServletRequest request = getRequest();
        String uri = request != null ? request.getRequestURI() : "";
        String ip = request != null ? getClientIp(request) : "";
        String userAgent = request != null ? truncate(request.getHeader("User-Agent"), 255) : "";

        Long userId = LoginUserContext.getUserId();
        String username = LoginUserContext.getUsername();
        String traceId = MDC.get("traceId");

        String argsJson = null;
        if (annotation.logArgs()) {
            argsJson = truncate(toJson(joinPoint.getArgs()), 4000);
        }
        String responseJson = null;
        if (annotation.logResponse() && result != null) {
            responseJson = truncate(toJson(result), 4000);
        }

        jdbcTemplate.update(INSERT_SQL,
                traceId, userId, username,
                annotation.module(), annotation.action(),
                null, null,
                methodName, uri, argsJson, responseJson,
                status, errorMsg, ip, userAgent, costMs,
                LocalDateTime.now());
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
