package com.codecoachai.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.system.domain.entity.LoginLog;
import com.codecoachai.system.domain.entity.OperationLog;
import com.codecoachai.system.mapper.LoginLogMapper;
import com.codecoachai.system.mapper.OperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 日志审计 Controller（管理端）。
 * 提供操作日志和登录日志的分页查询。
 */
@Tag(name = "日志审计-后台")
@RestController
@RequiredArgsConstructor
public class AdminLogController {

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;

    // ==================== 登录日志 ====================

    @Operation(summary = "分页查询登录日志")
    @com.codecoachai.common.web.log.OperationLog(module = "system", action = "QUERY_LOGIN_LOG", description = "查询登录日志", logArgs = false)
    @GetMapping({"/admin/login-logs", "/admin/logs/logins"})
    public Result<PageResult<LoginLog>> pageLoginLogs(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String loginStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        // 前端历史上会传 1/0，新接口也支持 SUCCESS/FAILED；这里统一成数据库枚举，避免筛选无结果。
        String resolvedStatus = StringUtils.hasText(loginStatus) ? normalizeStatus(loginStatus) : normalizeStatus(status);
        Page<LoginLog> page = loginLogMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<LoginLog>()
                        .eq(userId != null, LoginLog::getUserId, userId)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(LoginLog::getUsername, keyword)
                                .or().like(LoginLog::getIp, keyword)
                                .or().like(LoginLog::getUserAgent, keyword)
                                .or().like(LoginLog::getFailReason, keyword))
                        .like(StringUtils.hasText(username), LoginLog::getUsername, username)
                        .eq(StringUtils.hasText(resolvedStatus), LoginLog::getLoginStatus, resolvedStatus)
                        .eq(StringUtils.hasText(loginType), LoginLog::getLoginType, loginType)
                        .ge(startTime != null, LoginLog::getLoginTime, startTime)
                        .le(endTime != null, LoginLog::getLoginTime, endTime)
                        .orderByDesc(LoginLog::getLoginTime));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    // ==================== 操作日志 ====================

    @Operation(summary = "分页查询操作日志")
    @com.codecoachai.common.web.log.OperationLog(module = "system", action = "QUERY_OPERATION_LOG", description = "查询操作日志", logArgs = false)
    @GetMapping({"/admin/operation-logs", "/admin/logs/operations"})
    public Result<PageResult<OperationLog>> pageOperationLogs(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Page<OperationLog> page = operationLogMapper.selectPage(
                Page.of(pageNo, pageSize),
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
                        .eq(StringUtils.hasText(status), OperationLog::getStatus, status)
                        .ge(startTime != null, OperationLog::getCreatedAt, startTime)
                        .le(endTime != null, OperationLog::getCreatedAt, endTime)
                        .orderByDesc(OperationLog::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
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
}
