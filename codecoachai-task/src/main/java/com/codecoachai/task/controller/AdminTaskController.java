package com.codecoachai.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.constant.MqTopics;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task Admin")
@RestController
@RequestMapping("/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageDeadLetterMapper deadLetterMapper;
    private final Optional<MqProducer> mqProducer;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Page async tasks")
    @GetMapping
    public Result<PageResult<AsyncTask>> pageTasks(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        SecurityAssert.requireAdmin();
        String resolvedBizType = StringUtils.hasText(bizType) ? bizType : type;
        Page<AsyncTask> page = asyncTaskMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<AsyncTask>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(AsyncTask::getMessageId, keyword)
                                .or().like(AsyncTask::getBizType, keyword)
                                .or().like(AsyncTask::getBizId, keyword)
                                .or().like(AsyncTask::getStatus, keyword)
                                .or().like(AsyncTask::getFailureReason, keyword))
                        .eq(StringUtils.hasText(resolvedBizType), AsyncTask::getBizType, resolvedBizType)
                        .eq(StringUtils.hasText(status), AsyncTask::getStatus, status)
                        .eq(userId != null, AsyncTask::getUserId, userId)
                        .orderByDesc(AsyncTask::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "Get async task")
    @GetMapping("/{id}")
    public Result<AsyncTask> getTask(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        AsyncTask task = asyncTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(task);
    }

    @Operation(summary = "Get async task by message id")
    @GetMapping("/by-message-id/{messageId}")
    public Result<AsyncTask> getByMessageId(@PathVariable String messageId) {
        SecurityAssert.requireAdmin();
        AsyncTask task = asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>().eq(AsyncTask::getMessageId, messageId).last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(task);
    }

    @Operation(summary = "Task status stats")
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        SecurityAssert.requireAdmin();
        List<Map<String, Object>> counts = asyncTaskMapper.selectMaps(
                new QueryWrapper<AsyncTask>()
                        .select("status", "COUNT(*) AS count")
                        .groupBy("status"));
        return Result.success(counts);
    }

    @Operation(summary = "Retry failed async task")
    @PostMapping("/{id}/retry")
    public Result<Void> retryTask(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        AsyncTask task = asyncTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        if (!"FAILED".equals(task.getStatus()) && !"DEAD".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only FAILED/DEAD tasks can be retried");
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

    @Operation(summary = "Page dead letters")
    @GetMapping("/dead-letters")
    public Result<PageResult<MessageDeadLetter>> pageDeadLetters(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String bizType) {
        SecurityAssert.requireAdmin();
        Page<MessageDeadLetter> page = deadLetterMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<MessageDeadLetter>()
                        .eq(StringUtils.hasText(handleStatus), MessageDeadLetter::getHandleStatus, handleStatus)
                        .eq(StringUtils.hasText(bizType), MessageDeadLetter::getBizType, bizType)
                        .orderByDesc(MessageDeadLetter::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "Get dead letter")
    @GetMapping("/dead-letters/{id}")
    public Result<MessageDeadLetter> getDeadLetter(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        return Result.success(dl);
    }

    @Operation(summary = "Recover dead letter")
    @PostMapping("/dead-letters/{id}/recover")
    public Result<Void> recoverDeadLetter(@PathVariable Long id,
                                          @RequestParam(required = false) String note) {
        SecurityAssert.requireAdmin();
        MessageDeadLetter dl = getRecoverableDeadLetter(id);
        replayDeadLetter(dl);
        updateDeadLetterStatus(id, "RECOVERED", note);
        return Result.success();
    }

    @Operation(summary = "Ignore dead letter")
    @PostMapping("/dead-letters/{id}/ignore")
    public Result<Void> ignoreDeadLetter(@PathVariable Long id,
                                         @RequestParam(required = false) String note) {
        SecurityAssert.requireAdmin();
        updateDeadLetterStatus(id, "IGNORED", note);
        return Result.success();
    }

    @Operation(summary = "Compatibility endpoint for dead letter retry")
    @PostMapping("/{id}/dead-letter/retry")
    public Result<Void> recoverDeadLetterCompat(@PathVariable Long id,
                                                @RequestParam(required = false) String note) {
        SecurityAssert.requireAdmin();
        MessageDeadLetter dl = getRecoverableDeadLetter(id);
        replayDeadLetter(dl);
        updateDeadLetterStatus(id, "RECOVERED", note);
        return Result.success();
    }

    @Operation(summary = "Task service health")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("task-service ok");
    }

    private MessageDeadLetter getRecoverableDeadLetter(Long id) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        if (!"UNHANDLED".equals(dl.getHandleStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only UNHANDLED dead letters can be recovered");
        }
        return dl;
    }

    private void replayDeadLetter(MessageDeadLetter dl) {
        MqProducer producer = mqProducer.orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ producer is not available"));
        // Current dead-letter table stores bizType/bizId/payload but not original topic/tag;
        // use the established bizType routing table and fail closed for unsupported types.
        if ("resume.parse".equals(dl.getBizType())) {
            ResumeParsePayload payload = readPayload(dl.getPayload(), ResumeParsePayload.class);
            if (payload == null || payload.getResumeId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter resume payload is invalid");
            }
            producer.sendSync(MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_PARSE),
                    dl.getBizType(), StringUtils.hasText(dl.getBizId()) ? dl.getBizId() : String.valueOf(payload.getResumeId()),
                    dl.getUserId(), payload);
            return;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported dead letter bizType: " + dl.getBizType());
    }

    private <T> T readPayload(String payload, Class<T> type) {
        if (!StringUtils.hasText(payload)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter payload is empty");
        }
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter payload cannot be parsed");
        }
    }

    private void updateDeadLetterStatus(Long id, String status, String note) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        LambdaUpdateWrapper<MessageDeadLetter> wrapper = new LambdaUpdateWrapper<MessageDeadLetter>()
                .eq(MessageDeadLetter::getId, id)
                .set(MessageDeadLetter::getHandleStatus, status)
                .set(StringUtils.hasText(note), MessageDeadLetter::getHandleNote, note)
                .set(MessageDeadLetter::getUpdatedAt, LocalDateTime.now());
        Long handlerUserId = LoginUserContext.getUserId();
        if (handlerUserId != null) {
            wrapper.set(MessageDeadLetter::getHandlerUserId, handlerUserId);
        }
        deadLetterMapper.update(null, wrapper);
    }
}
