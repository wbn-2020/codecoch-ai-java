package com.codecoachai.auth.log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class PasswordResetSecurityLogRecorder {

    public void recordRequested(String email, String outcome) {
        log.info("PASSWORD_RESET_REQUEST outcome={} email={} ip={} traceId={}",
                outcome, maskEmail(email), currentIp(), MDC.get("traceId"));
    }

    public void recordCompleted(Long userId) {
        log.info("PASSWORD_RESET_COMPLETE userId={} ip={} traceId={}",
                userId, currentIp(), MDC.get("traceId"));
    }

    public void recordRejected(String reason) {
        log.info("PASSWORD_RESET_REJECTED reason={} ip={} traceId={}",
                reason, currentIp(), MDC.get("traceId"));
    }

    private String currentIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
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

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
