package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步任务后台管理 Controller。
 */
@Tag(name = "任务管理-后台")
@RestController
@RequestMapping("/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageDeadLetterMapper deadLetterMapper;

    // ==================== 任务列表 ====================

    @Operation(summary = "分页查询异步任务")
    @GetMapping
    public Result<PageResult<AsyncTask>> pageTasks(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        Page<AsyncTask> page = asyncTaskMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(StringUtils.hasText(bizType), AsyncTask::getBizType, bizType)
                        .eq(StringUtils.hasText(status), AsyncTask::getStatus, status)
                        .eq(userId != null, AsyncTask::getUserId, userId)
                        .orderByDesc(AsyncTask::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<AsyncTask> getTask(@PathVariable Long id) {
        AsyncTask task = asyncTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(task);
    }

    @Operation(summary = "按 messageId 查询")
    @GetMapping("/by-message-id/{messageId}")
    public Result<AsyncTask> getByMessageId(@PathVariable String messageId) {
        AsyncTask task = asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>().eq(AsyncTask::getMessageId, messageId).last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(task);
    }

    @Operation(summary = "统计各状态数量")
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        List<Map<String, Object>> counts = asyncTaskMapper.selectMaps(
                new QueryWrapper<AsyncTask>()
                        .select("status", "COUNT(*) AS count")
                        .groupBy("status"));
        return Result.success(counts);
    }

    // ==================== 重试 ====================

    @Operation(summary = "手动重试失败任务（重置状态为 PENDING，等待下次消费）")
    @PostMapping("/{id}/retry")
    public Result<Void> retryTask(@PathVariable Long id) {
        AsyncTask task = asyncTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        if (!"FAILED".equals(task.getStatus()) && !"DEAD".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能重试 FAILED/DEAD 状态的任务");
        }
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getId, id)
                        .set(AsyncTask::getStatus, "PENDING")
                        .set(AsyncTask::getFailureReason, null)
                        .set(AsyncTask::getCompletedAt, null)
                        .set(AsyncTask::getUpdatedAt, LocalDateTime.now()));
        return Result.success();
    }

    // ==================== 死信管理 ====================

    @Operation(summary = "分页查询死信")
    @GetMapping("/dead-letters")
    public Result<PageResult<MessageDeadLetter>> pageDeadLetters(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String bizType) {
        Page<MessageDeadLetter> page = deadLetterMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<MessageDeadLetter>()
                        .eq(StringUtils.hasText(handleStatus), MessageDeadLetter::getHandleStatus, handleStatus)
                        .eq(StringUtils.hasText(bizType), MessageDeadLetter::getBizType, bizType)
                        .orderByDesc(MessageDeadLetter::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "死信详情")
    @GetMapping("/dead-letters/{id}")
    public Result<MessageDeadLetter> getDeadLetter(@PathVariable Long id) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        return Result.success(dl);
    }

    @Operation(summary = "标记死信为已恢复")
    @PostMapping("/dead-letters/{id}/recover")
    public Result<Void> recoverDeadLetter(@PathVariable Long id,
                                          @RequestParam(required = false) String note) {
        updateDeadLetterStatus(id, "RECOVERED", note);
        return Result.success();
    }

    @Operation(summary = "标记死信为已忽略")
    @PostMapping("/dead-letters/{id}/ignore")
    public Result<Void> ignoreDeadLetter(@PathVariable Long id,
                                         @RequestParam(required = false) String note) {
        updateDeadLetterStatus(id, "IGNORED", note);
        return Result.success();
    }

    private void updateDeadLetterStatus(Long id, String status, String note) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        deadLetterMapper.update(null,
                new LambdaUpdateWrapper<MessageDeadLetter>()
                        .eq(MessageDeadLetter::getId, id)
                        .set(MessageDeadLetter::getHandleStatus, status)
                        .set(StringUtils.hasText(note), MessageDeadLetter::getHandleNote, note)
                        .set(MessageDeadLetter::getUpdatedAt, LocalDateTime.now()));
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("task-service ok");
    }
}
