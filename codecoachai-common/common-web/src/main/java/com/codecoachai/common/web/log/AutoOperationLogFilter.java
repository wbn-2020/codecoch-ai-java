package com.codecoachai.common.web.log;

import com.codecoachai.common.security.context.LoginUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fallback HTTP audit logger. Endpoints annotated with {@link OperationLog}
 * keep their precise module/action names; every other external request is
 * still recorded here so audit coverage follows newly added admin/user APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoOperationLogFilter extends OncePerRequestFilter {

    private static final String INSERT_SQL =
            "INSERT INTO operation_log (trace_id, user_id, username, module, action, target_type, target_id, " +
                    "method, request_uri, request_args, response, status, error_msg, ip, user_agent, cost_ms, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService logExecutor = Executors.newFixedThreadPool(2, new AuditThreadFactory());

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || uri.startsWith("/health")
                || uri.startsWith("/actuator")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/doc.html")
                || uri.startsWith("/webjars")
                || uri.startsWith("/inner/")
                || uri.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            failure = ex;
            throw ex;
        } finally {
            if (!Boolean.TRUE.equals(request.getAttribute(OperationLogAspect.LOG_RECORDED_ATTRIBUTE))) {
                submitAuditLog(request, response, start, failure);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        logExecutor.shutdown();
    }

    private void submitAuditLog(HttpServletRequest request, HttpServletResponse response, long start, Exception failure) {
        long costMs = System.currentTimeMillis() - start;
        String status = failure == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED";
        String errorMsg = failure == null ? null : truncate(failure.getMessage(), 1000);
        String uri = request.getRequestURI();
        AuditLogEntry entry = new AuditLogEntry(
                MDC.get("traceId"),
                LoginUserContext.getUserId(),
                LoginUserContext.getUsername(),
                resolveModule(uri),
                resolveAction(request.getMethod(), uri),
                resolveTargetType(uri),
                resolveTargetId(uri),
                request.getMethod(),
                truncate(uri, 500),
                truncate(request.getQueryString(), 4000),
                status,
                errorMsg,
                getClientIp(request),
                truncate(request.getHeader("User-Agent"), 255),
                costMs,
                LocalDateTime.now());

        logExecutor.execute(() -> writeAuditLog(entry));
    }

    private void writeAuditLog(AuditLogEntry entry) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    entry.traceId(),
                    entry.userId(),
                    entry.username(),
                    entry.module(),
                    entry.action(),
                    entry.targetType(),
                    entry.targetId(),
                    entry.method(),
                    entry.requestUri(),
                    entry.requestArgs(),
                    null,
                    entry.status(),
                    entry.errorMsg(),
                    entry.ip(),
                    entry.userAgent(),
                    entry.costMs(),
                    entry.createdAt());
        } catch (Exception logEx) {
            log.warn("自动操作日志写入失败", logEx);
        }
    }

    private String resolveModule(String uri) {
        String path = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (path.startsWith("/admin/questions") || path.startsWith("/admin/question-")) return "question";
        if (path.startsWith("/admin/ai") || path.startsWith("/ai/")) return "ai";
        if (path.startsWith("/admin/users") || path.startsWith("/admin/roles") || path.startsWith("/admin/menus")) return "user";
        if (path.startsWith("/admin/tasks") || path.startsWith("/admin/async-tasks") || path.startsWith("/tasks")) return "task";
        if (path.startsWith("/admin/search") || path.startsWith("/search")) return "search";
        if (path.startsWith("/admin/files") || path.startsWith("/files") || path.startsWith("/knowledge")) return "file";
        if (path.startsWith("/admin/interview") || path.startsWith("/interviews")) return "interview";
        if (path.startsWith("/admin/notifications") || path.startsWith("/admin/notices") || path.startsWith("/notifications")) return "notification";
        if (path.startsWith("/admin/analytics") || path.startsWith("/admin/ops") || path.startsWith("/analytics")) return "analytics";
        if (path.startsWith("/admin/agent") || path.startsWith("/agent")) return "agent";
        if (path.startsWith("/skill-profiles") || path.startsWith("/resumes") || path.startsWith("/resume-match")) return "resume";
        if (path.startsWith("/study-plans") || path.startsWith("/daily-tasks")) return "study";
        if (path.startsWith("/job-targets")) return "job";
        if (path.startsWith("/auth")) return "auth";
        if (path.startsWith("/users") || path.startsWith("/dashboard")) return "user";
        if (path.startsWith("/admin/system") || path.startsWith("/admin/logs") || path.startsWith("/admin/login-logs")
                || path.startsWith("/admin/operation-logs") || path.startsWith("/admin/slow-sql-logs")) return "system";
        if (path.startsWith("/admin")) return "admin";
        return "system";
    }

    private String resolveAction(String method, String uri) {
        String verb = method == null ? "REQUEST" : method.toUpperCase(Locale.ROOT);
        String path = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if ("GET".equals(verb)) {
            return path.matches(".*/\\d+($|/.*)") ? "VIEW" : "QUERY";
        }
        if ("POST".equals(verb)) return "CREATE_OR_EXECUTE";
        if ("PUT".equals(verb) || "PATCH".equals(verb)) return "UPDATE";
        if ("DELETE".equals(verb)) return "DELETE";
        return verb;
    }

    private String resolveTargetType(String uri) {
        if (!StringUtils.hasText(uri)) return null;
        String[] segments = uri.split("/");
        for (String segment : segments) {
            if (StringUtils.hasText(segment) && !segment.chars().allMatch(Character::isDigit) && !"admin".equals(segment)) {
                return truncate(segment, 64);
            }
        }
        return null;
    }

    private String resolveTargetId(String uri) {
        if (!StringUtils.hasText(uri)) return null;
        for (String segment : uri.split("/")) {
            if (StringUtils.hasText(segment) && segment.chars().allMatch(Character::isDigit)) {
                return truncate(segment, 64);
            }
        }
        return null;
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

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }

    private record AuditLogEntry(String traceId, Long userId, String username, String module, String action,
                                 String targetType, String targetId, String method, String requestUri,
                                 String requestArgs, String status, String errorMsg, String ip,
                                 String userAgent, Long costMs, LocalDateTime createdAt) {
    }

    private static class AuditThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "codecoachai-auto-operation-log");
            thread.setDaemon(true);
            return thread;
        }
    }
}
