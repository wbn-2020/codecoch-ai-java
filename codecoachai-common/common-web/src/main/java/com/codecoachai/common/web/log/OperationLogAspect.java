package com.codecoachai.common.web.log;

import com.codecoachai.common.security.context.LoginUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;
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
 * 拦截标注了 {@link OperationLog} 的 Controller 方法，尽力写入 operation_log 表。
 *
 * 依赖：common-web 已引入 spring-boot-starter-web + common-security + common-mybatis（含 JdbcTemplate）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    public static final String LOG_RECORDED_ATTRIBUTE = OperationLogAspect.class.getName() + ".RECORDED";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{6}(?:19|20)\\d{2}\\d{2}\\d{2}\\d{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\"(?:api[-_]?key|authorization|bearer|token|password|secret)\"\\s*:\\s*\")[^\"]+(\")");
    private static final Pattern KV_SECRET = Pattern.compile("(?i)\\b(api[-_ ]?key|authorization|bearer|token|password|secret)\\b\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern JSON_SENSITIVE_TEXT = Pattern.compile(
            "(?i)(\"(?:resumeContent|jobDescription|jd|prompt|renderedPrompt|aiResponse|rawOutputText|"
                    + "requestPrompt|responseContent|requestBody|responseBody|answer|comment|remark|feedback|"
                    + "projectExperience|description|content)\"\\s*:\\s*\")[^\"]*(\")");

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
            errorMsg = safeAuditText(ex.getMessage(), 1000);
            throw ex;
        } finally {
            long costMs = System.currentTimeMillis() - start;
            try {
                // 审计落库不能影响主业务结果；即使日志表异常，也只记录 warn 并放行业务响应。
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
            // 请求参数和响应体可能较大，入库前截断，避免审计表字段溢出影响业务接口。
            argsJson = safeAuditText(toJson(joinPoint.getArgs()), 4000);
        }
        String responseJson = null;
        if (annotation.logResponse() && result != null) {
            responseJson = safeAuditText(toJson(result), 4000);
        }

        jdbcTemplate.update(INSERT_SQL,
                traceId, userId, username,
                annotation.module(), annotation.action(),
                null, null,
                methodName, uri, argsJson, responseJson,
                status, errorMsg, ip, userAgent, costMs,
                LocalDateTime.now());
        if (request != null) {
            request.setAttribute(LOG_RECORDED_ATTRIBUTE, Boolean.TRUE);
        }
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

    private String safeAuditText(String text, int max) {
        return truncate(maskSensitive(text), max);
    }

    private String maskSensitive(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String masked = JSON_SECRET.matcher(text).replaceAll("$1******$2");
        masked = JSON_SENSITIVE_TEXT.matcher(masked).replaceAll("$1******$2");
        masked = KV_SECRET.matcher(masked).replaceAll("$1=******");
        masked = EMAIL.matcher(masked).replaceAll("***@***");
        masked = CHINA_MOBILE.matcher(masked).replaceAll("1**********");
        return ID_CARD.matcher(masked).replaceAll("******************");
    }
}
