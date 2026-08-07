package com.codecoachai.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.system.domain.entity.LoginLog;
import com.codecoachai.system.domain.entity.OperationLog;
import com.codecoachai.system.domain.entity.SlowSqlLog;
import com.codecoachai.system.domain.vo.AdminLogSummaryVO;
import com.codecoachai.system.domain.vo.LoginLogVO;
import com.codecoachai.system.mapper.LoginLogMapper;
import com.codecoachai.system.mapper.OperationLogMapper;
import com.codecoachai.system.mapper.SlowSqlLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin audit log APIs.
 */
@Tag(name = "Admin Log Audit")
@RestController
@RequiredArgsConstructor
@Slf4j
public class AdminLogController {

    private static final String PERM_OPERATION_LOG = "admin:audit:operation-log";
    private static final String PERM_LOGIN_LOG = "admin:audit:login-log";
    private static final String PERM_SLOW_SQL_LOG = "admin:audit:slow-sql-log";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{6}(?:19|20)\\d{2}\\d{2}\\d{2}\\d{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\"(?:api[-_]?key|authorization|bearer|token|password|secret)\"\\s*:\\s*\")[^\"]+(\")");
    private static final Pattern KV_SECRET = Pattern.compile("(?i)\\b(api[-_ ]?key|authorization|bearer|token|password|secret)\\b\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern SENSITIVE_SQL_IDENTIFIER = Pattern.compile(
            "(?i)\\b(password_hash|passwordHash|password|reset_token|refresh_token|access_token|token|authorization|api_key|apiKey|secret|access_key|accessKey)\\b");

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final SlowSqlLogMapper slowSqlLogMapper;
    private final AdminPermissionGuard adminPermissionGuard;

    @Operation(summary = "Query log audit summary")
    @com.codecoachai.common.web.log.OperationLog(module = "system", action = "QUERY_LOG_SUMMARY",
            description = "Query log audit summary", logArgs = false)
    @GetMapping({"/admin/logs/summary", "/admin/audit/log-summary"})
    public Result<AdminLogSummaryVO> summary() {
        adminPermissionGuard.requireAny(PERM_OPERATION_LOG, PERM_LOGIN_LOG, PERM_SLOW_SQL_LOG);
        AdminLogSummaryVO vo = emptySummary();
        try {
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

            vo.setTotalOperationLogs(countOperationLogs(new LambdaQueryWrapper<>()));
            vo.setTodayOperationLogs(countOperationLogs(new LambdaQueryWrapper<OperationLog>()
                    .ge(OperationLog::getCreatedAt, todayStart)));
            vo.setFailedOperationLogs(countOperationLogs(new LambdaQueryWrapper<OperationLog>()
                    .eq(OperationLog::getStatus, "FAILED")));
            vo.setTodayFailedOperationLogs(countOperationLogs(new LambdaQueryWrapper<OperationLog>()
                    .eq(OperationLog::getStatus, "FAILED")
                    .ge(OperationLog::getCreatedAt, todayStart)));
            vo.setLatestOperationAt(latestOperationAt());

            vo.setTotalLoginLogs(countLoginLogs(new LambdaQueryWrapper<>()));
            vo.setTodayLoginLogs(countLoginLogs(new LambdaQueryWrapper<LoginLog>()
                    .ge(LoginLog::getLoginTime, todayStart)));
            vo.setFailedLoginLogs(countLoginLogs(new LambdaQueryWrapper<LoginLog>()
                    .eq(LoginLog::getLoginStatus, "FAILED")));
            vo.setTodayFailedLoginLogs(countLoginLogs(new LambdaQueryWrapper<LoginLog>()
                    .eq(LoginLog::getLoginStatus, "FAILED")
                    .ge(LoginLog::getLoginTime, todayStart)));
            vo.setLatestLoginAt(latestLoginAt());
        } catch (RuntimeException ex) {
            log.warn("Admin log summary degraded because audit log query failed", ex);
        }
        return Result.success(vo);
    }

    @Operation(summary = "Page login logs")
    @com.codecoachai.common.web.log.OperationLog(module = "system", action = "QUERY_LOGIN_LOG",
            description = "Query login logs", logArgs = false)
    @GetMapping({"/admin/login-logs", "/admin/logs/logins"})
    public Result<PageResult<LoginLogVO>> pageLoginLogs(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String loginStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        adminPermissionGuard.require(PERM_LOGIN_LOG);
        String resolvedStatus = StringUtils.hasText(loginStatus) ? normalizeStatus(loginStatus) : normalizeStatus(status);
        try {
            Page<LoginLog> page = loginLogMapper.selectPage(
                    Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                    new LambdaQueryWrapper<LoginLog>()
                            .eq(userId != null, LoginLog::getUserId, userId)
                            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                    .like(LoginLog::getUsername, keyword)
                                    .or().like(LoginLog::getIp, keyword)
                                    .or().like(LoginLog::getUserAgent, keyword)
                                    .or().like(LoginLog::getFailReason, keyword))
                            .like(StringUtils.hasText(username), LoginLog::getUsername, username)
                            .eq(StringUtils.hasText(traceId), LoginLog::getTraceId, traceId)
                            .eq(StringUtils.hasText(resolvedStatus), LoginLog::getLoginStatus, resolvedStatus)
                            .eq(StringUtils.hasText(loginType), LoginLog::getLoginType, loginType)
                            .ge(startTime != null, LoginLog::getLoginTime, startTime)
                            .le(endTime != null, LoginLog::getLoginTime, endTime)
                            .orderByDesc(LoginLog::getLoginTime));
            List<LoginLogVO> records = page.getRecords().stream().map(this::toLoginLogVO).toList();
            return Result.success(PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize()));
        } catch (RuntimeException ex) {
            log.warn("Login log page degraded because audit log query failed", ex);
            return Result.success(emptyPage(pageNo, pageSize));
        }
    }

    @Operation(summary = "Page operation logs")
    @com.codecoachai.common.web.log.OperationLog(module = "system", action = "QUERY_OPERATION_LOG",
            description = "Query operation logs", logArgs = false)
    @GetMapping({"/admin/operation-logs", "/admin/logs/operations"})
    public Result<PageResult<OperationLogAuditVO>> pageOperationLogs(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        adminPermissionGuard.require(PERM_OPERATION_LOG);
        try {
            Page<OperationLog> page = operationLogMapper.selectPage(
                    Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                    new LambdaQueryWrapper<OperationLog>()
                            .eq(userId != null, OperationLog::getUserId, userId)
                            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                    .like(OperationLog::getUsername, keyword)
                                    .or().like(OperationLog::getModule, keyword)
                                    .or().like(OperationLog::getAction, keyword)
                                    .or().like(OperationLog::getRequestUri, keyword)
                                    .or().like(OperationLog::getIp, keyword)
                                    .or().like(OperationLog::getErrorMsg, keyword))
                            .like(StringUtils.hasText(username), OperationLog::getUsername, username)
                            .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                            .eq(StringUtils.hasText(action), OperationLog::getAction, action)
                            .eq(StringUtils.hasText(traceId), OperationLog::getTraceId, traceId)
                            .eq(StringUtils.hasText(status), OperationLog::getStatus, status)
                            .ge(startTime != null, OperationLog::getCreatedAt, startTime)
                            .le(endTime != null, OperationLog::getCreatedAt, endTime)
                            .orderByDesc(OperationLog::getCreatedAt));
            List<OperationLogAuditVO> records = page.getRecords().stream().map(this::toOperationLogVO).toList();
            return Result.success(PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize()));
        } catch (RuntimeException ex) {
            log.warn("Operation log page degraded because audit log query failed", ex);
            return Result.success(emptyPage(pageNo, pageSize));
        }
    }

    @Operation(summary = "Page slow SQL logs")
    @com.codecoachai.common.web.log.OperationLog(module = "system", action = "QUERY_SLOW_SQL_LOG",
            description = "Query slow SQL logs", logArgs = false)
    @GetMapping({"/admin/slow-sql-logs", "/admin/logs/slow-sql"})
    public Result<PageResult<SlowSqlLogAuditVO>> pageSlowSqlLogs(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mapperId,
            @RequestParam(required = false) String sqlCommandType,
            @RequestParam(required = false) Long minCostMs,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        adminPermissionGuard.require(PERM_SLOW_SQL_LOG);
        try {
            Page<SlowSqlLog> page = slowSqlLogMapper.selectPage(
                    Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                    new LambdaQueryWrapper<SlowSqlLog>()
                            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                    .like(SlowSqlLog::getMapperId, keyword)
                                    .or().like(SlowSqlLog::getSqlText, keyword)
                                    .or().like(SlowSqlLog::getDatabaseName, keyword))
                            .like(StringUtils.hasText(mapperId), SlowSqlLog::getMapperId, mapperId)
                            .eq(StringUtils.hasText(sqlCommandType), SlowSqlLog::getSqlCommandType, sqlCommandType)
                            .ge(minCostMs != null, SlowSqlLog::getCostMs, minCostMs)
                            .ge(startTime != null, SlowSqlLog::getCreatedAt, startTime)
                            .le(endTime != null, SlowSqlLog::getCreatedAt, endTime)
                            .orderByDesc(SlowSqlLog::getCreatedAt));
            List<SlowSqlLogAuditVO> records = page.getRecords().stream().map(this::toSlowSqlLogVO).toList();
            return Result.success(PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize()));
        } catch (RuntimeException ex) {
            log.warn("Slow SQL log page degraded because slow SQL log query failed", ex);
            return Result.success(emptyPage(pageNo, pageSize));
        }
    }

    private <T> PageResult<T> emptyPage(Long pageNo, Long pageSize) {
        long current = defaultPage(pageNo);
        long size = defaultSize(pageSize);
        return PageResult.of(Collections.emptyList(), 0L, current, size);
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100L);
    }

    private AdminLogSummaryVO emptySummary() {
        AdminLogSummaryVO vo = new AdminLogSummaryVO();
        vo.setTotalOperationLogs(0L);
        vo.setTodayOperationLogs(0L);
        vo.setFailedOperationLogs(0L);
        vo.setTodayFailedOperationLogs(0L);
        vo.setTotalLoginLogs(0L);
        vo.setTodayLoginLogs(0L);
        vo.setFailedLoginLogs(0L);
        vo.setTodayFailedLoginLogs(0L);
        return vo;
    }

    private Long countOperationLogs(LambdaQueryWrapper<OperationLog> wrapper) {
        Long count = operationLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private Long countLoginLogs(LambdaQueryWrapper<LoginLog> wrapper) {
        Long count = loginLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private LocalDateTime latestOperationAt() {
        OperationLog latest = operationLogMapper.selectOne(new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreatedAt)
                .last("limit 1"));
        return latest == null ? null : latest.getCreatedAt();
    }

    private LocalDateTime latestLoginAt() {
        LoginLog latest = loginLogMapper.selectOne(new LambdaQueryWrapper<LoginLog>()
                .orderByDesc(LoginLog::getLoginTime)
                .last("limit 1"));
        return latest == null ? null : latest.getLoginTime();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return status;
        }
        String value = status.trim();
        if ("1".equals(value)) {
            return "SUCCESS";
        }
        if ("0".equals(value)) {
            return "FAILED";
        }
        return value;
    }

    private LoginLogVO toLoginLogVO(LoginLog log) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setLoginType(log.getLoginType());
        vo.setLoginStatus(log.getLoginStatus());
        vo.setIpMasked(maskIp(log.getIp()));
        vo.setMaskedIp(vo.getIpMasked());
        vo.setIp(vo.getIpMasked());
        vo.setUserAgentSummary(summarizeUserAgent(log.getUserAgent()));
        vo.setUserAgent(vo.getUserAgentSummary());
        vo.setFailReason(log.getFailReason());
        vo.setTraceIdShort(shortId(log.getTraceId()));
        vo.setShortTraceId(vo.getTraceIdShort());
        vo.setTraceId(vo.getTraceIdShort());
        vo.setLoginTime(log.getLoginTime());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private OperationLogAuditVO toOperationLogVO(OperationLog log) {
        OperationLogAuditVO vo = new OperationLogAuditVO();
        vo.setId(log.getId());
        vo.setTraceId(shortId(log.getTraceId()));
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setOperation(log.getAction());
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setMethod(log.getMethod());
        vo.setRequestUri(log.getRequestUri());
        vo.setRequestArgsPreview(safePreview(log.getRequestArgs(), 160));
        vo.setRequestArgsHash(sha256Prefix(log.getRequestArgs()));
        vo.setResponsePreview(safePreview(log.getResponse(), 160));
        vo.setResponseHash(sha256Prefix(log.getResponse()));
        vo.setRawAvailable(false);
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(safePreview(log.getErrorMsg(), 160));
        vo.setErrorMsg(vo.getErrorMessage());
        vo.setIp(maskIp(log.getIp()));
        vo.setUserAgentSummary(summarizeUserAgent(log.getUserAgent()));
        vo.setCostTime(log.getCostMs());
        vo.setCostMs(log.getCostMs());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private SlowSqlLogAuditVO toSlowSqlLogVO(SlowSqlLog log) {
        SlowSqlLogAuditVO vo = new SlowSqlLogAuditVO();
        vo.setId(log.getId());
        vo.setMapperId(log.getMapperId());
        vo.setSqlCommandType(log.getSqlCommandType());
        vo.setSqlText(safePreview(maskSensitiveSql(log.getSqlText()), 240));
        vo.setSqlTextPreview(vo.getSqlText());
        vo.setSqlTextHash(sha256Prefix(log.getSqlText()));
        vo.setParameterSummary(safePreview(maskSensitiveSql(log.getParameterSummary()), 200));
        vo.setParameterSummaryHash(sha256Prefix(log.getParameterSummary()));
        vo.setRawAvailable(false);
        vo.setDatabaseName(log.getDatabaseName());
        vo.setCostMs(log.getCostMs());
        vo.setThresholdMs(log.getThresholdMs());
        vo.setResultSize(log.getResultSize());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return ip;
        }
        String value = ip.trim();
        if (value.contains(":")) {
            int index = value.indexOf(':');
            return index <= 0 ? "***" : value.substring(0, index) + ":***";
        }
        String[] parts = value.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return "***";
    }

    private String summarizeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return userAgent;
        }
        String value = userAgent.trim();
        String browser = containsAny(value, "Edg/") ? "Edge"
                : containsAny(value, "Chrome/") ? "Chrome"
                : containsAny(value, "Firefox/") ? "Firefox"
                : containsAny(value, "Safari/") ? "Safari"
                : "Browser";
        String platform = containsAny(value, "Windows") ? "Windows"
                : containsAny(value, "Mac OS X", "Macintosh") ? "macOS"
                : containsAny(value, "Android") ? "Android"
                : containsAny(value, "iPhone", "iPad") ? "iOS"
                : containsAny(value, "Linux") ? "Linux"
                : "Unknown OS";
        return browser + " on " + platform;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String shortId(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }

    private String safePreview(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        String preview = normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
        return maskText(preview);
    }

    private String maskText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String masked = EMAIL.matcher(value).replaceAll("***@***");
        masked = CHINA_MOBILE.matcher(masked).replaceAll("1**********");
        masked = ID_CARD.matcher(masked).replaceAll("******************");
        masked = JSON_SECRET.matcher(masked).replaceAll("$1******$2");
        return KV_SECRET.matcher(masked).replaceAll("$1=******");
    }

    private String maskSensitiveSql(String value) {
        return value == null ? null : SENSITIVE_SQL_IDENTIFIER.matcher(value).replaceAll("[sensitive]");
    }

    private String sha256Prefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }

    @Data
    public static class OperationLogAuditVO {
        private Long id;
        private String traceId;
        private Long userId;
        private String username;
        private String module;
        private String action;
        private String operation;
        private String targetType;
        private String targetId;
        private String method;
        private String requestUri;
        private String requestArgsPreview;
        private String requestArgsHash;
        private String responsePreview;
        private String responseHash;
        private Boolean rawAvailable;
        private String status;
        private String errorMessage;
        private String errorMsg;
        private String ip;
        private String userAgentSummary;
        private Long costTime;
        private Long costMs;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SlowSqlLogAuditVO {
        private Long id;
        private String mapperId;
        private String sqlCommandType;
        private String sqlText;
        private String sqlTextPreview;
        private String sqlTextHash;
        private String parameterSummary;
        private String parameterSummaryHash;
        private Boolean rawAvailable;
        private String databaseName;
        private Long costMs;
        private Long thresholdMs;
        private Integer resultSize;
        private LocalDateTime createdAt;
    }
}
