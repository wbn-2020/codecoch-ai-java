package com.codecoachai.auth.log;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录日志记录器。
 * 直接通过 JdbcTemplate 写入 login_log 表（与 V3_007 迁移脚本对应）。
 *
 * 使用方式：在 AuthService.login / logout 成功/失败后调用本类对应方法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogRecorder {

    private static final String INSERT_SQL =
            "INSERT INTO login_log (user_id, username, login_type, login_status, ip, user_agent, " +
                    "fail_reason, trace_id, login_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    @Async("commonAsyncExecutor")
    public void recordSuccess(Long userId, String username, String loginType) {
        record(userId, username, loginType, "SUCCESS", null);
    }

    @Async("commonAsyncExecutor")
    public void recordFailed(String username, String loginType, String reason) {
        record(null, username, loginType, "FAILED", reason);
    }

    @Async("commonAsyncExecutor")
    public void recordLogout(Long userId, String username) {
        record(userId, username, "LOGOUT", "SUCCESS", null);
    }

    private void record(Long userId, String username, String loginType, String status, String reason) {
        try {
            HttpServletRequest req = currentRequest();
            String ip = req != null ? clientIp(req) : null;
            String ua = req != null ? truncate(req.getHeader("User-Agent"), 255) : null;
            String traceId = MDC.get("traceId");

            jdbcTemplate.update(INSERT_SQL,
                    userId, truncate(username, 64), loginType, status,
                    ip, ua, truncate(reason, 255), traceId,
                    LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("登录日志写入失败 username={} status={}", username, status, ex);
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
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
}
