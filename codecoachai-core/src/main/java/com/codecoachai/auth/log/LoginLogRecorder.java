package com.codecoachai.auth.log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
@Component
@RequiredArgsConstructor
public class LoginLogRecorder {

    private final LoginLogAsyncWriter asyncWriter;

    public void recordSuccess(Long userId, String username, String loginType) {
        asyncWriter.write(capture(userId, username, loginType, "SUCCESS", null));
    }

    public void recordFailed(String username, String loginType, String reason) {
        asyncWriter.write(capture(null, username, loginType, "FAILED", reason));
    }

    public void recordLogout(Long userId, String username) {
        asyncWriter.write(capture(userId, username, "LOGOUT", "SUCCESS", null));
    }

    private Entry capture(Long userId, String username, String loginType, String status, String reason) {
        HttpServletRequest req = currentRequest();
        return new Entry(
                userId,
                truncate(username, 64),
                loginType,
                status,
                req == null ? null : clientIp(req),
                req == null ? null : truncate(req.getHeader("User-Agent"), 255),
                truncate(reason, 255),
                truncate(MDC.get("traceId"), 128));
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

    public record Entry(
            Long userId,
            String username,
            String loginType,
            String status,
            String ip,
            String userAgent,
            String reason,
            String traceId) {
    }
}
