package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task User")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class UserTaskController {

    private final AsyncTaskMapper asyncTaskMapper;

    @Operation(summary = "Page current user's async tasks")
    @GetMapping
    public Result<PageResult<AsyncTask>> pageTasks(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status) {
        Long userId = SecurityAssert.requireLoginUserId();
        String resolvedBizType = StringUtils.hasText(bizType) ? bizType : type;
        Page<AsyncTask> page = asyncTaskMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getUserId, userId)
                        .eq(StringUtils.hasText(resolvedBizType), AsyncTask::getBizType, resolvedBizType)
                        .eq(StringUtils.hasText(status), AsyncTask::getStatus, status)
                        .orderByDesc(AsyncTask::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "Get current user's async task detail")
    @GetMapping("/{id}")
    public Result<AsyncTask> detail(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        AsyncTask task = asyncTaskMapper.selectOne(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getId, id)
                .eq(AsyncTask::getUserId, userId)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(task);
    }
}
