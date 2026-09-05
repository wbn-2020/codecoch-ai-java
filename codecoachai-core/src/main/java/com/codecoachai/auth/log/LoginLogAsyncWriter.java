package com.codecoachai.auth.log;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogAsyncWriter {

    private static final String INSERT_SQL =
            "INSERT INTO login_log (user_id, username, login_type, status, login_status, ip, user_agent, "
                    + "failure_reason, fail_reason, trace_id, login_time, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    @Async("commonAsyncExecutor")
    public void write(LoginLogRecorder.Entry entry) {
        try {
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(
                    INSERT_SQL,
                    entry.userId(),
                    entry.username(),
                    entry.loginType(),
                    entry.status(),
                    entry.status(),
                    entry.ip(),
                    entry.userAgent(),
                    entry.reason(),
                    entry.reason(),
                    entry.traceId(),
                    now,
                    now);
        } catch (Exception ex) {
            log.warn("登录日志写入失败 username={} status={}", entry.username(), entry.status(), ex);
        }
    }
}
