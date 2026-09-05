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
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.mq.payload.AgentDailyPlanPayload;
import com.codecoachai.common.mq.payload.InterviewReportPayload;
import com.codecoachai.common.mq.payload.JobTargetParsePayload;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.payload.QuestionRecommendationGeneratePayload;
import com.codecoachai.common.mq.payload.ResumeJobMatchPayload;
import com.codecoachai.common.mq.payload.ResumeOptimizePayload;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.payload.StudyPlanGeneratePayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.task.domain.dto.AdminTaskActionDTO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.domain.enums.AsyncTaskGovernanceStatus;
import com.codecoachai.task.domain.vo.AdminAsyncTaskVO;
import com.codecoachai.task.domain.vo.AdminDeadLetterVO;
import com.codecoachai.task.domain.vo.AdminTaskGovernancePreviewVO;
import com.codecoachai.task.domain.vo.AdminTaskImpactPreviewVO;
import com.codecoachai.task.domain.vo.AdminTaskStatsVO;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.codecoachai.task.service.AsyncTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端异步任务与死信治理接口。
 * 负责查询任务执行状态、人工恢复死信消息和标记不可恢复消息。
 */
@Tag(name = "Task Admin")
@RestController
@RequestMapping("/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

    private static final String PERM_TASK_LIST = "admin:task:list";
    private static final String PERM_TASK_RETRY = "admin:task:retry";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9Xx])\\d{6}(?:19|20)\\d{2}\\d{2}\\d{2}\\d{3}[0-9Xx](?![0-9Xx])");
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\"(?:api[-_]?key|authorization|bearer|token|password|secret)\"\\s*:\\s*\")[^\"]+(\")");
    private static final String BIZ_RESUME_PARSE = "resume.parse";
    private static final String BIZ_RESUME_OPTIMIZE = "resume.optimize";
    private static final String BIZ_JOB_TARGET_PARSE = "job-target.parse";
    private static final String BIZ_RESUME_JOB_MATCH = "resume-job-match.analyze";
    private static final String BIZ_QUESTION_GENERATE = "question.generate";
    private static final String BIZ_QUESTION_AI_GENERATE = "question.ai-generate";
    private static final String BIZ_QUESTION_RECOMMENDATION_GENERATE = "question-recommendation.generate";
    private static final String BIZ_INTERVIEW_REPORT = "interview.report";
    private static final String BIZ_STUDY_PLAN_GENERATE = "study-plan.generate";
    private static final String BIZ_AGENT_DAILY_PLAN_GENERATE = "agent.daily-plan.generate";
    private static final String BIZ_SEARCH_SYNC = "search.sync";
    private static final String INDEX_QUESTION = "cc_question";
    private static final String INDEX_RESUME = "cc_resume";
    private static final String INDEX_INTERVIEW = "cc_interview";
    private static final ZoneId ADMIN_STATS_ZONE_ID = ZoneId.of(AsyncTaskMapper.ADMIN_STATS_TIMEZONE);
    private static final String ALL_TIME_TO_SNAPSHOT = "ALL_TIME_TO_SNAPSHOT";
    private static final String RANGE_TO_SNAPSHOT = "RANGE_TO_SNAPSHOT";

    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageDeadLetterMapper deadLetterMapper;
    private final AsyncTaskService asyncTaskService;
    private final Optional<MqProducer> mqProducer;
    private final ObjectMapper objectMapper;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @Operation(summary = "Page async tasks")
    @GetMapping
    public Result<PageResult<AdminAsyncTaskVO>> pageTasks(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String governanceStatus,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdBefore) {
        permissionGuard.require(PERM_TASK_LIST);
        validateTaskWindow(createdFrom, createdBefore);
        // type 是早期管理页字段，bizType 是当前实体字段；统一解析后再查询。
        String resolvedBizType = StringUtils.hasText(bizType) ? bizType : type;
        List<String> resolvedStatuses = normalizeStatusFilter(status);
        Page<AsyncTask> page = asyncTaskMapper.selectPage(
                Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getDeleted, 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(AsyncTask::getMessageId, keyword)
                                .or().like(AsyncTask::getBizType, keyword)
                                .or().like(AsyncTask::getBizId, keyword)
                                .or().like(AsyncTask::getStatus, keyword)
                                .or().like(AsyncTask::getFailureReason, keyword))
                        .eq(StringUtils.hasText(resolvedBizType), AsyncTask::getBizType, resolvedBizType)
                        .eq(resolvedStatuses.size() == 1, AsyncTask::getStatus,
                                resolvedStatuses.size() == 1 ? resolvedStatuses.get(0) : null)
                        .in(resolvedStatuses.size() > 1, AsyncTask::getStatus, resolvedStatuses)
                        .eq(StringUtils.hasText(governanceStatus), AsyncTask::getGovernanceStatus,
                                AsyncTaskGovernanceStatus.normalize(governanceStatus))
                        .eq(userId != null, AsyncTask::getUserId, userId)
                        .ge(createdFrom != null, AsyncTask::getCreatedAt, createdFrom)
                        .lt(createdBefore != null, AsyncTask::getCreatedAt, createdBefore)
                        .orderByDesc(AsyncTask::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords().stream().map(this::toTaskVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    static List<String> normalizeStatusFilter(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        return Pattern.compile("[,，]")
                .splitAsStream(status)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .limit(20)
                .toList();
    }

    @Operation(summary = "List bounded async-task governance inventory")
    @GetMapping("/governance-inventory")
    public Result<List<AdminAsyncTaskVO>> governanceInventory(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String governanceStatus,
            @RequestParam(required = false) Long minAgeMinutes,
            @RequestParam(defaultValue = "50") Integer limit) {
        permissionGuard.require(PERM_TASK_LIST);
        LocalDateTime now = LocalDateTime.now();
        long safeMinAge = minAgeMinutes == null ? 0L : Math.max(0L, Math.min(minAgeMinutes, 43_200L));
        LambdaQueryWrapper<AsyncTask> wrapper = new LambdaQueryWrapper<AsyncTask>()
                .eq(StringUtils.hasText(bizType), AsyncTask::getBizType, bizType == null ? null : bizType.trim())
                .eq(StringUtils.hasText(status), AsyncTask::getStatus, status == null ? null : status.trim())
                .eq(StringUtils.hasText(governanceStatus), AsyncTask::getGovernanceStatus,
                        StringUtils.hasText(governanceStatus)
                                ? AsyncTaskGovernanceStatus.normalize(governanceStatus)
                                : null)
                .le(safeMinAge > 0, AsyncTask::getCreatedAt, now.minusMinutes(safeMinAge))
                .orderByDesc(AsyncTask::getUpdatedAt)
                .last("limit " + safeLimit(limit));
        if (!StringUtils.hasText(status)) {
            wrapper.in(AsyncTask::getStatus, List.of("FAILED", "DEAD", "ERROR", "DEAD_LETTER"));
        }
        return Result.success(asyncTaskMapper.selectList(wrapper).stream().map(this::toTaskVO).toList());
    }

    @Operation(summary = "Get async task")
    @GetMapping("/{id}")
    public Result<AdminAsyncTaskVO> getTask(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_LIST);
        AsyncTask task = asyncTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(toTaskVO(task));
    }

    @Operation(summary = "Get async task by message id")
    @GetMapping("/by-message-id/{messageId}")
    public Result<AdminAsyncTaskVO> getByMessageId(@PathVariable String messageId) {
        permissionGuard.require(PERM_TASK_LIST);
        AsyncTask task = asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>().eq(AsyncTask::getMessageId, messageId).last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return Result.success(toTaskVO(task));
    }

    @Operation(summary = "List async tasks by business key")
    @GetMapping("/by-biz")
    public Result<List<AdminAsyncTaskVO>> listByBiz(
            @RequestParam String bizType,
            @RequestParam String bizId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "20") Integer limit) {
        permissionGuard.require(PERM_TASK_LIST);
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "bizType and bizId are required");
        }
        List<AsyncTask> tasks = asyncTaskMapper.selectList(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getBizType, bizType.trim())
                        .eq(AsyncTask::getBizId, bizId.trim())
                        .eq(userId != null, AsyncTask::getUserId, userId)
                        .orderByDesc(AsyncTask::getCreatedAt)
                        .last("limit " + safeLimit(limit)));
        return Result.success(tasks.stream().map(this::toTaskVO).toList());
    }

    @Operation(summary = "List async tasks by trace id")
    @GetMapping("/by-trace")
    public Result<List<AdminAsyncTaskVO>> listByTrace(
            @RequestParam String traceId,
            @RequestParam(defaultValue = "20") Integer limit) {
        permissionGuard.require(PERM_TASK_LIST);
        if (!StringUtils.hasText(traceId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请提供任务追踪编号");
        }
        List<AsyncTask> tasks = asyncTaskMapper.selectList(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getTraceId, traceId.trim())
                        .orderByDesc(AsyncTask::getCreatedAt)
                        .last("limit " + safeLimit(limit)));
        return Result.success(tasks.stream().map(this::toTaskVO).toList());
    }

    @Operation(summary = "Task status stats")
    @GetMapping("/stats")
    public Result<AdminTaskStatsVO> stats(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdBefore) {
        permissionGuard.require(PERM_TASK_LIST);
        validateTaskWindow(createdFrom, createdBefore);
        LocalDateTime generatedAt = LocalDateTime.now(ADMIN_STATS_ZONE_ID);
        LocalDateTime windowEnd = createdBefore == null ? generatedAt : createdBefore;
        List<String> resolvedStatuses = normalizeStatusFilter(status);
        long total = asyncTaskMapper.countAdminTasks(
                resolvedStatuses,
                createdFrom,
                windowEnd);
        QueryWrapper<AsyncTask> statsWrapper = new QueryWrapper<AsyncTask>()
                .select("status", "COUNT(1) AS count")
                .eq("deleted", 0)
                .ge(createdFrom != null, "created_at", createdFrom)
                .lt("created_at", windowEnd)
                .groupBy("status");
        if (!resolvedStatuses.isEmpty()) {
            statsWrapper.in("status", resolvedStatuses);
        }
        List<Map<String, Object>> counts = asyncTaskMapper.selectMaps(statsWrapper);
        AdminTaskStatsVO vo = new AdminTaskStatsVO();
        vo.setTotal(total);
        vo.setStatusCounts(counts.stream().map(this::toStatusCount).toList());
        vo.setStatuses(resolvedStatuses);
        vo.setStatusFilter(resolvedStatuses.isEmpty() ? "ALL" : String.join(",", resolvedStatuses));
        vo.setWindowType(createdFrom == null ? ALL_TIME_TO_SNAPSHOT : RANGE_TO_SNAPSHOT);
        vo.setWindowStart(createdFrom);
        vo.setWindowEnd(windowEnd);
        vo.setGeneratedAt(generatedAt);
        vo.setBusinessTimezone(AsyncTaskMapper.ADMIN_STATS_TIMEZONE);
        vo.setScopeDescription(taskStatsScopeDescription(resolvedStatuses));
        vo.setNavigationPath("/admin/async-tasks");
        vo.setNavigationQuery(taskStatsNavigationQuery(resolvedStatuses, createdFrom, windowEnd));
        return Result.success(vo);
    }

    private AdminTaskStatsVO.StatusCountVO toStatusCount(Map<String, Object> row) {
        AdminTaskStatsVO.StatusCountVO vo = new AdminTaskStatsVO.StatusCountVO();
        Object status = mapValue(row, "status");
        Object count = mapValue(row, "count");
        vo.setStatus(status == null ? null : String.valueOf(status));
        vo.setCount(count instanceof Number number ? number.longValue() : 0L);
        return vo;
    }

    private Object mapValue(Map<String, Object> row, String key) {
        return row.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String taskStatsScopeDescription(List<String> statuses) {
        if (statuses.equals(AsyncTaskMapper.ADMIN_FAILURE_STATUSES)) {
            return "全部未删除任务；状态为 FAILED、DEAD、ERROR 或 DEAD_LETTER；统计截止时间不含之后新建任务。";
        }
        return statuses.isEmpty()
                ? "全部未删除任务；统计截止时间不含之后新建任务。"
                : "全部未删除任务；仅统计指定状态；统计截止时间不含之后新建任务。";
    }

    private Map<String, String> taskStatsNavigationQuery(List<String> statuses,
                                                         LocalDateTime createdFrom,
                                                         LocalDateTime createdBefore) {
        java.util.LinkedHashMap<String, String> query = new java.util.LinkedHashMap<>();
        if (!statuses.isEmpty()) {
            query.put("status", String.join(",", statuses));
        }
        if (createdFrom != null) {
            query.put("createdFrom", createdFrom.toString());
        }
        query.put("createdBefore", createdBefore.toString());
        return query;
    }

    private void validateTaskWindow(LocalDateTime createdFrom, LocalDateTime createdBefore) {
        if (createdFrom != null && createdBefore != null && !createdFrom.isBefore(createdBefore)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "createdFrom must be before createdBefore");
        }
    }

    @Operation(summary = "Preview failed async task retry impact")
    @GetMapping("/{id}/retry-preview")
    public Result<AdminTaskImpactPreviewVO> retryTaskPreview(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_RETRY);
        AsyncTask task = getTaskEntity(id);
        return Result.success(taskRetryPreview(task));
    }

    @Operation(summary = "Preview async task governance classification")
    @GetMapping("/{id}/governance-preview")
    public Result<AdminTaskGovernancePreviewVO> governancePreview(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_RETRY);
        return Result.success(taskGovernancePreview(getTaskEntity(id)));
    }

    @Operation(summary = "Classify async task governance state without dispatching")
    @PostMapping("/{id}/governance")
    @OperationLog(module = "task", action = "GOVERN_ASYNC_TASK",
            description = "人工分类异步任务治理状态", logArgs = false, logResponse = false)
    public Result<Void> updateTaskGovernance(@PathVariable Long id,
                                             @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        String note = requireActionNote(dto);
        String lockKey = requireConfirmedTaskAction("async-task-governance:" + id, dto);
        try {
            AsyncTask task = getTaskEntity(id);
            AdminTaskGovernancePreviewVO preview = taskGovernancePreview(task);
            if (!StringUtils.hasText(dto == null ? null : dto.getPreviewHash())
                    || !preview.getPreviewHash().equals(dto.getPreviewHash().trim())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "治理预览已过期，请刷新后重新确认");
            }
            AsyncTaskGovernanceStatus requested = AsyncTaskGovernanceStatus.parse(dto.getGovernanceStatus());
            if (!preview.getAllowedGovernanceStatuses().contains(requested.name())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "当前任务状态不允许设置该治理状态");
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = asyncTaskMapper.updateGovernance(
                    task.getId(),
                    requested.name(),
                    truncate(note, 500),
                    truncate(dto.getGovernanceOwner(), 128),
                    AsyncTaskGovernanceStatus.RETRY_APPROVED.equals(requested)
                            ? retryPreviewHash(task, now)
                            : preview.getPreviewHash(),
                    task.getUpdatedAt(),
                    now);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "任务已更新，请刷新治理预览后重试");
            }
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "Retry failed async task")
    @PostMapping("/{id}/retry")
    @OperationLog(module = "task", action = "RETRY_ASYNC_TASK", description = "重试失败异步任务", logArgs = false, logResponse = false)
    public Result<Void> retryTask(@PathVariable Long id,
                                  @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        requireActionNote(dto);
        String lockKey = requireConfirmedTaskAction("async-task-retry:" + id, dto);
        boolean dispatchAttempted = false;
        try {
            AsyncTask task = getTaskEntity(id);
            if (!isRetryableTaskStatus(task.getStatus())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Only FAILED/DEAD tasks can be retried");
            }
            AdminTaskImpactPreviewVO preview = taskRetryPreview(task);
            if (!StringUtils.hasText(dto == null ? null : dto.getPreviewHash())
                    || !preview.getPreviewHash().equals(dto.getPreviewHash().trim())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "重试预览已过期，请刷新后重新确认");
            }
            if (!AsyncTaskGovernanceStatus.RETRY_APPROVED.name().equals(
                    AsyncTaskGovernanceStatus.normalize(task.getGovernanceStatus()))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "任务必须先完成 RETRY_APPROVED 治理审批");
            }
            if (!preview.getPreviewHash().equals(task.getRetryPreviewHash())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "任务重试审批与当前预览不一致，请重新审批");
            }
            RetryDispatch dispatch = buildRetryDispatch(task, newRetryIdentity(task, preview.getPreviewHash()));
            AsyncTask retryTask = asyncTaskService.prepareManualRetry(task, dispatch.attempt);
            try {
                MqProducer producer = mqProducer.orElseThrow(() ->
                        new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ producer is not available"));
                dispatchAttempted = true;
                producer.sendEnvelopeSync(dispatch.destination, dispatch.envelope);
            } catch (BusinessException ex) {
                asyncTaskService.markManualRetryDispatchFailed(
                        task.getId(), retryTask.getId(), retryTask.getExecutionId(),
                        "Manual retry dispatch failed: " + ex.getMessage());
                throw ex;
            } catch (Exception ex) {
                asyncTaskService.markManualRetryDispatchFailed(
                        task.getId(), retryTask.getId(), retryTask.getExecutionId(),
                        "Manual retry dispatch failed: " + ex.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Manual retry dispatch failed");
            }
            return Result.success();
        } catch (RuntimeException ex) {
            if (!dispatchAttempted) {
                operationConfirmationGuard.release(lockKey);
            }
            throw ex;
        }
    }

    @Operation(summary = "Page dead letters")
    @GetMapping("/dead-letters")
    public Result<PageResult<AdminDeadLetterVO>> pageDeadLetters(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String bizType) {
        permissionGuard.require(PERM_TASK_LIST);
        Page<MessageDeadLetter> page = deadLetterMapper.selectPage(
                Page.of(defaultPage(pageNo), defaultSize(pageSize)),
                new LambdaQueryWrapper<MessageDeadLetter>()
                        .eq(StringUtils.hasText(handleStatus), MessageDeadLetter::getHandleStatus, handleStatus)
                        .eq(StringUtils.hasText(bizType), MessageDeadLetter::getBizType, bizType)
                        .orderByDesc(MessageDeadLetter::getCreatedAt));
        return Result.success(PageResult.of(page.getRecords().stream().map(this::toDeadLetterVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "Get dead letter")
    @GetMapping("/dead-letters/{id}")
    public Result<AdminDeadLetterVO> getDeadLetter(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_LIST);
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        return Result.success(toDeadLetterVO(dl));
    }

    @Operation(summary = "Preview dead letter recover impact")
    @GetMapping("/dead-letters/{id}/recover-preview")
    public Result<AdminTaskImpactPreviewVO> recoverDeadLetterPreview(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_RETRY);
        MessageDeadLetter dl = getRecoverableDeadLetter(id);
        return Result.success(deadLetterRecoverPreview(dl));
    }

    @Operation(summary = "Recover dead letter")
    @PostMapping("/dead-letters/{id}/recover")
    @OperationLog(module = "task", action = "RECOVER_DEAD_LETTER", description = "恢复死信消息", logArgs = false, logResponse = false)
    public Result<Void> recoverDeadLetter(@PathVariable Long id,
                                          @RequestParam(required = false) String note,
                                          @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        String actionNote = requireActionNote(note, dto);
        String lockKey = requireConfirmedTaskAction("dead-letter-recover:" + id, dto);
        boolean dispatchAttempted = false;
        try {
            MessageDeadLetter dl = getRecoverableDeadLetter(id);
            DeadLetterReplayDispatch dispatch = buildDeadLetterReplayDispatch(dl);
            MqProducer producer = mqProducer.orElseThrow(() ->
                    new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ producer is not available"));
            dispatchAttempted = true;
            producer.sendSync(dispatch.destination, dispatch.bizType, dispatch.bizId, dispatch.userId, dispatch.payload);
            updateDeadLetterStatus(id, "RECOVERED", actionNote, "UNHANDLED");
            return Result.success();
        } catch (RuntimeException ex) {
            if (!dispatchAttempted) {
                operationConfirmationGuard.release(lockKey);
            }
            throw ex;
        }
    }

    @Operation(summary = "Ignore dead letter")
    @PostMapping("/dead-letters/{id}/ignore")
    @OperationLog(module = "task", action = "IGNORE_DEAD_LETTER", description = "忽略死信消息", logArgs = false, logResponse = false)
    public Result<Void> ignoreDeadLetter(@PathVariable Long id,
                                         @RequestParam(required = false) String note,
                                         @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        String actionNote = requireActionNote(note, dto);
        String lockKey = requireConfirmedTaskAction("dead-letter-ignore:" + id, dto);
        try {
            getIgnorableDeadLetter(id);
            updateDeadLetterStatus(id, "IGNORED", actionNote, "UNHANDLED");
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @Operation(summary = "Compatibility endpoint for dead letter retry preview")
    @GetMapping("/{id}/dead-letter/retry-preview")
    public Result<AdminTaskImpactPreviewVO> recoverDeadLetterCompatPreview(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_RETRY);
        MessageDeadLetter dl = getRecoverableDeadLetterCompat(id);
        return Result.success(deadLetterRecoverPreview(dl));
    }

    @Operation(summary = "Compatibility endpoint for dead letter retry")
    @PostMapping("/{id}/dead-letter/retry")
    @OperationLog(module = "task", action = "RECOVER_DEAD_LETTER_COMPAT", description = "兼容入口恢复死信消息", logArgs = false, logResponse = false)
    public Result<Void> recoverDeadLetterCompat(@PathVariable Long id,
                                                @RequestParam(required = false) String note,
                                                @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        String actionNote = requireActionNote(note, dto);
        MessageDeadLetter dl = getRecoverableDeadLetterCompat(id);
        String lockKey = requireConfirmedTaskAction("dead-letter-recover:" + dl.getId(), dto);
        boolean dispatchAttempted = false;
        try {
            DeadLetterReplayDispatch dispatch = buildDeadLetterReplayDispatch(dl);
            MqProducer producer = mqProducer.orElseThrow(() ->
                    new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ producer is not available"));
            dispatchAttempted = true;
            producer.sendSync(dispatch.destination, dispatch.bizType, dispatch.bizId, dispatch.userId, dispatch.payload);
            updateDeadLetterStatus(dl.getId(), "RECOVERED", actionNote, "UNHANDLED");
            return Result.success();
        } catch (RuntimeException ex) {
            if (!dispatchAttempted) {
                operationConfirmationGuard.release(lockKey);
            }
            throw ex;
        }
    }

    @Operation(summary = "Task service health")
    @GetMapping("/health")
    public Result<String> health() {
        permissionGuard.require(PERM_TASK_LIST);
        return Result.success("task-service ok");
    }

    private AsyncTask getTaskEntity(Long id) {
        AsyncTask task = asyncTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "task not found");
        }
        return task;
    }

    private boolean isRetryableTaskStatus(String status) {
        return "FAILED".equals(status) || "DEAD".equals(status)
                || "ERROR".equals(status) || "DEAD_LETTER".equals(status);
    }

    private int safeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private String requireActionNote(AdminTaskActionDTO dto) {
        return requireActionNote(null, dto);
    }

    private String requireActionNote(String note, AdminTaskActionDTO dto) {
        String resolved = StringUtils.hasText(note) ? note : dto == null ? null : dto.getNote();
        if (!StringUtils.hasText(resolved)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写操作备注");
        }
        return resolved.trim();
    }

    private String requireConfirmedTaskAction(String operation, AdminTaskActionDTO dto) {
        return operationConfirmationGuard.requireConfirmed(
                operation,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey());
    }

    private AdminTaskImpactPreviewVO taskRetryPreview(AsyncTask task) {
        AdminTaskImpactPreviewVO vo = new AdminTaskImpactPreviewVO();
        vo.setId(task.getId());
        vo.setTargetType("ASYNC_TASK");
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setUserId(task.getUserId());
        vo.setCurrentStatus(task.getStatus());
        vo.setPreviewHash(retryPreviewHash(task));
        vo.setExecutable(isRetryableTaskStatus(task.getStatus()));
        vo.setRiskLevel("MEDIUM");
        vo.setRequiredPermission(PERM_TASK_RETRY);
        vo.setRequiredNote("请填写失败原因已处理的说明");
        vo.setImpact("将任务状态重置为 PENDING，后续由对应补偿流程重新执行；非幂等业务可能产生重复 AI 调用或重复解析。");
        return vo;
    }

    private AdminTaskGovernancePreviewVO taskGovernancePreview(AsyncTask task) {
        String failureClass = failureClass(task);
        boolean successful = "SUCCESS".equalsIgnoreCase(task.getStatus());
        boolean retryable = isRetryableTaskStatus(task.getStatus());
        AdminTaskGovernancePreviewVO vo = new AdminTaskGovernancePreviewVO();
        vo.setId(task.getId());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setTaskStatus(task.getStatus());
        vo.setGovernanceStatus(AsyncTaskGovernanceStatus.normalize(task.getGovernanceStatus()));
        vo.setFailureClass(failureClass);
        vo.setAgeMinutes(taskAgeMinutes(task, LocalDateTime.now()));
        vo.setRetryAllowed(retryable);
        if (successful) {
            vo.setRecommendedGovernanceStatus(AsyncTaskGovernanceStatus.RESOLVED.name());
            vo.setRecommendedOwner("SYSTEM");
            vo.setImpact("仅记录已成功任务的治理结论，不会重新投递消息或改变执行状态。");
            vo.setAllowedGovernanceStatuses(List.of(AsyncTaskGovernanceStatus.RESOLVED.name()));
        } else if ("UPSTREAM_UNAVAILABLE".equals(failureClass) && retryable) {
            vo.setRecommendedGovernanceStatus(AsyncTaskGovernanceStatus.RETRY_APPROVED.name());
            vo.setRecommendedOwner("PLATFORM_ONCALL");
            vo.setImpact("仅记录“已批准重试”的人工结论；仍需通过独立的重试预览和确认流程才能投递消息。");
            vo.setAllowedGovernanceStatuses(List.of(
                    AsyncTaskGovernanceStatus.RETRY_APPROVED.name(),
                    AsyncTaskGovernanceStatus.WONT_RETRY.name(),
                    AsyncTaskGovernanceStatus.MANUAL_ACTION_REQUIRED.name()));
        } else if (retryable) {
            vo.setRecommendedGovernanceStatus(AsyncTaskGovernanceStatus.MANUAL_ACTION_REQUIRED.name());
            vo.setRecommendedOwner(recommendedOwner(failureClass));
            vo.setImpact("仅记录人工处置结论，不会修改任务执行状态、删除死信或投递 MQ 消息。");
            vo.setAllowedGovernanceStatuses(List.of(
                    AsyncTaskGovernanceStatus.RETRY_APPROVED.name(),
                    AsyncTaskGovernanceStatus.WONT_RETRY.name(),
                    AsyncTaskGovernanceStatus.MANUAL_ACTION_REQUIRED.name()));
        } else {
            vo.setRecommendedGovernanceStatus(AsyncTaskGovernanceStatus.UNASSESSED.name());
            vo.setRecommendedOwner("SYSTEM");
            vo.setImpact("任务尚未进入可人工分类的终态；该预览不会修改执行状态或投递消息。");
            vo.setAllowedGovernanceStatuses(List.of());
        }
        vo.setPreviewHash(governancePreviewHash(task, failureClass));
        return vo;
    }

    private AdminTaskImpactPreviewVO deadLetterRecoverPreview(MessageDeadLetter dl) {
        AdminTaskImpactPreviewVO vo = new AdminTaskImpactPreviewVO();
        vo.setId(dl.getId());
        vo.setTargetType("DEAD_LETTER");
        vo.setBizType(dl.getBizType());
        vo.setBizId(dl.getBizId());
        vo.setUserId(dl.getUserId());
        vo.setCurrentStatus(dl.getHandleStatus());
        vo.setExecutable("UNHANDLED".equals(dl.getHandleStatus()));
        vo.setRiskLevel("HIGH");
        vo.setRequiredPermission(PERM_TASK_RETRY);
        vo.setRequiredNote("请填写依赖已恢复、允许重新投递的说明");
        vo.setImpact("将按 bizType 校验 payload 后重新投递 MQ，并把死信标记为 RECOVERED；可能触发重复 AI 调用、解析或索引同步。");
        return vo;
    }

    private RetryDispatch buildRetryDispatch(AsyncTask task, RetryIdentity identity) {
        if (!StringUtils.hasText(task.getMessageId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "async task messageId is empty");
        }
        String bizType = task.getBizType();
        if (BIZ_RESUME_PARSE.equals(bizType)) {
            ResumeParsePayload payload = readTaskPayload(task.getPayload(), ResumeParsePayload.class);
            if (payload == null || payload.getResumeId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task resume payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_PARSE),
                    bizType, resolveBizId(task.getBizId(), payload.getResumeId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_RESUME_OPTIMIZE.equals(bizType)) {
            ResumeOptimizePayload payload = readTaskPayload(task.getPayload(), ResumeOptimizePayload.class);
            if (payload == null || payload.getOptimizeRecordId() == null
                    || payload.getResumeId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task resume optimize payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_OPTIMIZE),
                    bizType, resolveBizId(task.getBizId(), payload.getOptimizeRecordId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_JOB_TARGET_PARSE.equals(bizType)) {
            JobTargetParsePayload payload = readTaskPayload(task.getPayload(), JobTargetParsePayload.class);
            if (payload == null || payload.getTargetJobId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task job target payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_JOB_TARGET_PARSE),
                    bizType, resolveBizId(task.getBizId(), payload.getTargetJobId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_RESUME_JOB_MATCH.equals(bizType)) {
            ResumeJobMatchPayload payload = readTaskPayload(task.getPayload(), ResumeJobMatchPayload.class);
            if (payload == null || payload.getReportId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task resume job match payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.JOB_MATCH, MqTopics.JOB_MATCH_TAG_ANALYZE),
                    bizType, resolveBizId(task.getBizId(), payload.getReportId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_QUESTION_GENERATE.equals(bizType) || BIZ_QUESTION_AI_GENERATE.equals(bizType)) {
            QuestionGeneratePayload payload = readTaskPayload(task.getPayload(), QuestionGeneratePayload.class);
            if (payload == null || !StringUtils.hasText(payload.getBatchId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task question payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_AI_GENERATE),
                    BIZ_QUESTION_GENERATE, resolveBizId(task.getBizId(), payload.getBatchId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_QUESTION_RECOMMENDATION_GENERATE.equals(bizType)) {
            QuestionRecommendationGeneratePayload payload =
                    readTaskPayload(task.getPayload(), QuestionRecommendationGeneratePayload.class);
            if (payload == null || payload.getBatchId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "async task question recommendation payload is invalid");
            }
            return retryDispatch(task, identity,
                    MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_RECOMMENDATION_GENERATE),
                    bizType, resolveBizId(task.getBizId(), payload.getBatchId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_INTERVIEW_REPORT.equals(bizType)) {
            InterviewReportPayload payload = readTaskPayload(task.getPayload(), InterviewReportPayload.class);
            if (payload == null || payload.getSessionId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task interview payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.INTERVIEW, MqTopics.INTERVIEW_TAG_REPORT),
                    bizType, resolveBizId(task.getBizId(), payload.getSessionId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_STUDY_PLAN_GENERATE.equals(bizType)) {
            StudyPlanGeneratePayload payload = readTaskPayload(task.getPayload(), StudyPlanGeneratePayload.class);
            if (payload == null || payload.getPlanId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task study plan payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.STUDY_PLAN, MqTopics.STUDY_PLAN_TAG_GENERATE),
                    bizType, resolveBizId(task.getBizId(), payload.getPlanId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_AGENT_DAILY_PLAN_GENERATE.equals(bizType)) {
            AgentDailyPlanPayload payload = readTaskPayload(task.getPayload(), AgentDailyPlanPayload.class);
            if (payload == null || payload.getRunId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task agent daily plan payload is invalid");
            }
            payload.setExecutionId(identity.executionId());
            payload.setParentExecutionId(identity.parentExecutionId());
            payload.setIdempotencyKey(identity.idempotencyKey());
            payload.setAttemptNo(identity.attemptNo());
            payload.setExecutionToken(identity.executionToken());
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.AGENT, MqTopics.AGENT_TAG_DAILY_PLAN),
                    bizType, resolveBizId(task.getBizId(), payload.getRunId()),
                    resolveUserId(task.getUserId(), payload.getUserId()), payload);
        }
        if (BIZ_SEARCH_SYNC.equals(bizType)) {
            SearchSyncPayload payload = readTaskPayload(task.getPayload(), SearchSyncPayload.class);
            if (payload == null || !StringUtils.hasText(payload.getIndexName())
                    || !StringUtils.hasText(payload.getDocId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "async task search payload is invalid");
            }
            return retryDispatch(task, identity, MqTopics.dest(MqTopics.SEARCH, resolveSearchTag(payload.getIndexName())),
                    bizType, resolveBizId(task.getBizId(), payload.getDocId()), task.getUserId(), payload);
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported async task bizType: " + bizType);
    }

    private <T> RetryDispatch retryDispatch(AsyncTask task, RetryIdentity identity,
                                            String destination, String bizType,
                                            String bizId, Long userId, T payload) {
        String payloadJson = serializePayload(payload);
        MqMessage<T> envelope = MqMessage.<T>builder()
                .messageId(identity.messageId())
                .traceId(identity.traceId())
                .bizType(bizType)
                .bizId(bizId)
                .userId(userId)
                .payload(payload)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        return new RetryDispatch(destination, envelope,
                new AsyncTaskService.ManualRetryAttempt(
                        identity.messageId(),
                        identity.traceId(),
                        identity.executionId(),
                        identity.parentExecutionId(),
                        identity.idempotencyKey(),
                        identity.attemptNo(),
                        payloadJson,
                        identity.retryPreviewHash()));
    }

    private RetryIdentity newRetryIdentity(AsyncTask task, String retryPreviewHash) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String executionId = "retry:" + task.getId() + ":" + token;
        String parentExecutionId = StringUtils.hasText(task.getExecutionId())
                ? task.getExecutionId()
                : "legacy-task:" + task.getId();
        int currentAttemptNo = Math.max(
                task.getAttemptNo() == null ? 1 : task.getAttemptNo(),
                Math.max(0, task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        int attemptNo = currentAttemptNo + 1;
        return new RetryIdentity(
                "admin-retry:" + task.getId() + ":" + token,
                "admin-retry-trace:" + token,
                executionId,
                parentExecutionId,
                "admin-retry:" + executionId,
                attemptNo,
                token,
                retryPreviewHash);
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "async task payload cannot be serialized");
        }
    }

    private Long resolveUserId(Long taskUserId, Long payloadUserId) {
        return taskUserId != null ? taskUserId : payloadUserId;
    }

    private AdminAsyncTaskVO toTaskVO(AsyncTask task) {
        AdminAsyncTaskVO vo = new AdminAsyncTaskVO();
        vo.setId(task.getId());
        vo.setMessageId(task.getMessageId());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setUserId(task.getUserId());
        vo.setTraceId(task.getTraceId());
        vo.setStatus(task.getStatus());
        vo.setExecutionId(task.getExecutionId());
        vo.setParentExecutionId(task.getParentExecutionId());
        vo.setRunId(task.getRunId());
        vo.setAttemptNo(task.getAttemptNo());
        vo.setIdempotencyKey(task.getIdempotencyKey());
        vo.setTerminalReasonCode(task.getTerminalReasonCode());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setMaxRetryCount(task.getMaxRetry());
        vo.setFailureReason(maskText(task.getFailureReason()));
        vo.setPayloadPreview(preview(task.getPayload()));
        vo.setPayloadHash(sha256Prefix(task.getPayload()));
        vo.setResultPreview(preview(task.getResult()));
        vo.setResultHash(sha256Prefix(task.getResult()));
        vo.setRawFieldsAvailable(StringUtils.hasText(task.getPayload()) || StringUtils.hasText(task.getResult()));
        vo.setGovernanceStatus(AsyncTaskGovernanceStatus.normalize(task.getGovernanceStatus()));
        vo.setGovernanceReason(maskText(task.getGovernanceReason()));
        vo.setGovernanceOwner(task.getGovernanceOwner());
        vo.setRetryPreviewHash(task.getRetryPreviewHash());
        vo.setFailureClass(failureClass(task));
        vo.setAgeMinutes(taskAgeMinutes(task, LocalDateTime.now()));
        vo.setGovernanceUpdatedAt(task.getGovernanceUpdatedAt());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private String failureClass(AsyncTask task) {
        String status = task == null ? "" : String.valueOf(task.getStatus()).toUpperCase();
        if ("SUCCESS".equals(status)) {
            return "NONE";
        }
        String reason = task == null ? "" : String.valueOf(task.getFailureReason()).toLowerCase();
        if (reason.contains("credential") || reason.contains("authorization")
                || reason.contains("auth") || reason.contains("token")) {
            return "AUTH_OR_CONFIGURATION";
        }
        if (reason.contains("json") || reason.contains("parse") || reason.contains("deserialize")
                || reason.contains("payload")) {
            return "PAYLOAD_CONTRACT";
        }
        if (reason.contains("timeout") || reason.contains("connection")
                || reason.contains("unavailable") || reason.contains("upstream")
                || reason.contains("502") || reason.contains("503")) {
            return "UPSTREAM_UNAVAILABLE";
        }
        if ("DEAD".equals(status) || "DEAD_LETTER".equals(status)) {
            return "RETRY_EXHAUSTED";
        }
        return StringUtils.hasText(reason) ? "UNCLASSIFIED_FAILURE" : "PENDING_ASSESSMENT";
    }

    private String recommendedOwner(String failureClass) {
        if ("AUTH_OR_CONFIGURATION".equals(failureClass)) {
            return "PLATFORM_ADMIN";
        }
        if ("PAYLOAD_CONTRACT".equals(failureClass)) {
            return "BUSINESS_ENGINEERING";
        }
        if ("UPSTREAM_UNAVAILABLE".equals(failureClass)) {
            return "PLATFORM_ONCALL";
        }
        return "BUSINESS_ENGINEERING";
    }

    private long taskAgeMinutes(AsyncTask task, LocalDateTime now) {
        LocalDateTime since = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        if (since == null || now == null || since.isAfter(now)) {
            return 0L;
        }
        return Duration.between(since, now).toMinutes();
    }

    private AdminDeadLetterVO toDeadLetterVO(MessageDeadLetter dl) {
        AdminDeadLetterVO vo = new AdminDeadLetterVO();
        vo.setId(dl.getId());
        vo.setMessageId(dl.getMessageId());
        vo.setBizType(dl.getBizType());
        vo.setBizId(dl.getBizId());
        vo.setUserId(dl.getUserId());
        vo.setTraceId(dl.getTraceId());
        vo.setPayloadPreview(preview(dl.getPayload()));
        vo.setPayloadHash(sha256Prefix(dl.getPayload()));
        vo.setLastFailureReason(maskText(dl.getLastFailureReason()));
        vo.setTotalRetry(dl.getTotalRetry());
        vo.setHandleStatus(dl.getHandleStatus());
        vo.setHandleNote(maskText(dl.getHandleNote()));
        vo.setHandlerUserId(dl.getHandlerUserId());
        vo.setRawFieldsAvailable(StringUtils.hasText(dl.getPayload()));
        vo.setCreatedAt(dl.getCreatedAt());
        vo.setUpdatedAt(dl.getUpdatedAt());
        return vo;
    }

    private String preview(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        String preview = normalized.length() <= 160 ? normalized : normalized.substring(0, 160) + "...";
        return maskText(preview);
    }

    private String maskText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String masked = EMAIL.matcher(value).replaceAll("***@***");
        masked = CHINA_MOBILE.matcher(masked).replaceAll("1**********");
        masked = ID_CARD.matcher(masked).replaceAll("******************");
        return JSON_SECRET.matcher(masked).replaceAll("$1******$2");
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

    private MessageDeadLetter getRecoverableDeadLetter(Long id) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        // 死信只允许从 UNHANDLED 恢复一次，防止管理员重复点击造成同一业务消息多次投递。
        if (!"UNHANDLED".equals(dl.getHandleStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only UNHANDLED dead letters can be recovered");
        }
        return dl;
    }

    private MessageDeadLetter getRecoverableDeadLetterCompat(Long id) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            AsyncTask task = asyncTaskMapper.selectById(id);
            if (task != null) {
                LambdaQueryWrapper<MessageDeadLetter> wrapper = new LambdaQueryWrapper<MessageDeadLetter>()
                        .eq(StringUtils.hasText(task.getBizType()), MessageDeadLetter::getBizType, task.getBizType())
                        .orderByDesc(MessageDeadLetter::getCreatedAt)
                        .last("limit 1");
                if (StringUtils.hasText(task.getMessageId()) && StringUtils.hasText(task.getBizId())) {
                    wrapper.and(nested -> nested
                            .eq(MessageDeadLetter::getMessageId, task.getMessageId())
                            .or()
                            .eq(MessageDeadLetter::getBizId, task.getBizId()));
                } else if (StringUtils.hasText(task.getMessageId())) {
                    wrapper.eq(MessageDeadLetter::getMessageId, task.getMessageId());
                } else if (StringUtils.hasText(task.getBizId())) {
                    wrapper.eq(MessageDeadLetter::getBizId, task.getBizId());
                } else {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter cannot be resolved from task");
                }
                dl = deadLetterMapper.selectOne(wrapper);
            }
        }
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        if (!"UNHANDLED".equals(dl.getHandleStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only UNHANDLED dead letters can be recovered");
        }
        return dl;
    }

    private MessageDeadLetter getIgnorableDeadLetter(Long id) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        if (!"UNHANDLED".equals(dl.getHandleStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only UNHANDLED dead letters can be ignored");
        }
        return dl;
    }

    private DeadLetterReplayDispatch buildDeadLetterReplayDispatch(MessageDeadLetter dl) {
        // 恢复死信时按 bizType 还原到原 Topic/Tag，payload 校验失败则拒绝恢复，避免投递脏消息。
        if (BIZ_RESUME_PARSE.equals(dl.getBizType())) {
            ResumeParsePayload payload = readPayload(dl.getPayload(), ResumeParsePayload.class);
            if (payload == null || payload.getResumeId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter resume payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_PARSE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getResumeId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_RESUME_OPTIMIZE.equals(dl.getBizType())) {
            ResumeOptimizePayload payload = readPayload(dl.getPayload(), ResumeOptimizePayload.class);
            if (payload == null || payload.getOptimizeRecordId() == null
                    || payload.getResumeId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter resume optimize payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_OPTIMIZE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getOptimizeRecordId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_JOB_TARGET_PARSE.equals(dl.getBizType())) {
            JobTargetParsePayload payload = readPayload(dl.getPayload(), JobTargetParsePayload.class);
            if (payload == null || payload.getTargetJobId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter job target payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_JOB_TARGET_PARSE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getTargetJobId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_RESUME_JOB_MATCH.equals(dl.getBizType())) {
            ResumeJobMatchPayload payload = readPayload(dl.getPayload(), ResumeJobMatchPayload.class);
            if (payload == null || payload.getReportId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter resume job match payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.JOB_MATCH, MqTopics.JOB_MATCH_TAG_ANALYZE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getReportId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_QUESTION_GENERATE.equals(dl.getBizType()) || BIZ_QUESTION_AI_GENERATE.equals(dl.getBizType())) {
            QuestionGeneratePayload payload = readPayload(dl.getPayload(), QuestionGeneratePayload.class);
            if (payload == null || !StringUtils.hasText(payload.getBatchId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter question payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_AI_GENERATE),
                    BIZ_QUESTION_GENERATE, resolveBizId(dl.getBizId(), payload.getBatchId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_QUESTION_RECOMMENDATION_GENERATE.equals(dl.getBizType())) {
            QuestionRecommendationGeneratePayload payload = readPayload(dl.getPayload(), QuestionRecommendationGeneratePayload.class);
            if (payload == null || payload.getBatchId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter question recommendation payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_RECOMMENDATION_GENERATE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getBatchId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_INTERVIEW_REPORT.equals(dl.getBizType())) {
            InterviewReportPayload payload = readPayload(dl.getPayload(), InterviewReportPayload.class);
            if (payload == null || payload.getSessionId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter interview payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.INTERVIEW, MqTopics.INTERVIEW_TAG_REPORT),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getSessionId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_STUDY_PLAN_GENERATE.equals(dl.getBizType())) {
            StudyPlanGeneratePayload payload = readPayload(dl.getPayload(), StudyPlanGeneratePayload.class);
            if (payload == null || payload.getPlanId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter study plan payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.STUDY_PLAN, MqTopics.STUDY_PLAN_TAG_GENERATE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getPlanId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_AGENT_DAILY_PLAN_GENERATE.equals(dl.getBizType())) {
            AgentDailyPlanPayload payload = readPayload(dl.getPayload(), AgentDailyPlanPayload.class);
            if (payload == null || payload.getRunId() == null || payload.getUserId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter agent daily plan payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.AGENT, MqTopics.AGENT_TAG_DAILY_PLAN),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getRunId()),
                    dl.getUserId(), payload);
        }
        if (BIZ_SEARCH_SYNC.equals(dl.getBizType())) {
            SearchSyncPayload payload = readPayload(dl.getPayload(), SearchSyncPayload.class);
            if (payload == null || !StringUtils.hasText(payload.getIndexName()) || !StringUtils.hasText(payload.getDocId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter search payload is invalid");
            }
            return deadLetterReplayDispatch(MqTopics.dest(MqTopics.SEARCH, resolveSearchTag(payload.getIndexName())),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getDocId()),
                    dl.getUserId(), payload);
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持的死信业务类型：" + dl.getBizType());
    }

    private <T> DeadLetterReplayDispatch deadLetterReplayDispatch(String destination, String bizType,
                                                                  String bizId, Long userId, T payload) {
        return new DeadLetterReplayDispatch(destination, bizType, bizId, userId, payload);
    }

    private String resolveBizId(String deadLetterBizId, Object payloadBizId) {
        // 老数据可能没有 bizId，恢复时用 payload 中的业务主键补齐消息键。
        return StringUtils.hasText(deadLetterBizId) ? deadLetterBizId : String.valueOf(payloadBizId);
    }

    private String resolveSearchTag(String indexName) {
        if (INDEX_QUESTION.equals(indexName)) {
            return MqTopics.SEARCH_TAG_QUESTION;
        }
        if (INDEX_RESUME.equals(indexName)) {
            return MqTopics.SEARCH_TAG_RESUME;
        }
        if (INDEX_INTERVIEW.equals(indexName)) {
            return MqTopics.SEARCH_TAG_INTERVIEW;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "暂不支持的搜索索引：" + indexName);
    }

    private <T> T readTaskPayload(String payload, Class<T> type) {
        if (!StringUtils.hasText(payload)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "async task payload is empty");
        }
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "async task payload cannot be parsed");
        }
    }

    private <T> T readPayload(String payload, Class<T> type) {
        if (!StringUtils.hasText(payload)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter payload is empty");
        }
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            // payload 不可解析时不要进入 MQ，避免消费者收到结构不确定的历史死信。
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter payload cannot be parsed");
        }
    }

    private void updateDeadLetterStatus(Long id, String status, String note, String expectedStatus) {
        MessageDeadLetter dl = deadLetterMapper.selectById(id);
        if (dl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter not found");
        }
        if (StringUtils.hasText(expectedStatus) && !expectedStatus.equals(dl.getHandleStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter status changed, please refresh and retry");
        }
        LambdaUpdateWrapper<MessageDeadLetter> wrapper = new LambdaUpdateWrapper<MessageDeadLetter>()
                .eq(MessageDeadLetter::getId, id)
                .eq(StringUtils.hasText(expectedStatus), MessageDeadLetter::getHandleStatus, expectedStatus)
                .set(MessageDeadLetter::getHandleStatus, status)
                .set(StringUtils.hasText(note), MessageDeadLetter::getHandleNote, note)
                .set(MessageDeadLetter::getUpdatedAt, LocalDateTime.now());
        Long handlerUserId = LoginUserContext.getUserId();
        if (handlerUserId != null) {
                // 记录处理人用于后续审计，未登录上下文下只更新状态与备注。
            wrapper.set(MessageDeadLetter::getHandlerUserId, handlerUserId);
        }
        int updated = deadLetterMapper.update(null, wrapper);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter status changed, please refresh and retry");
        }
    }

    private long defaultPage(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private String governancePreviewHash(AsyncTask task, String failureClass) {
        String source = String.join("|",
                String.valueOf(task.getId()),
                String.valueOf(task.getStatus()),
                String.valueOf(task.getUpdatedAt()),
                AsyncTaskGovernanceStatus.normalize(task.getGovernanceStatus()),
                String.valueOf(task.getFailureReason()),
                String.valueOf(task.getRetryCount()),
                String.valueOf(task.getMaxRetry()),
                failureClass);
        return sha256(source);
    }

    private String retryPreviewHash(AsyncTask task) {
        return retryPreviewHash(task, task.getUpdatedAt());
    }

    private String retryPreviewHash(AsyncTask task, LocalDateTime version) {
        String source = String.join("|",
                String.valueOf(task.getId()),
                String.valueOf(task.getStatus()),
                String.valueOf(task.getBizType()),
                String.valueOf(task.getBizId()),
                String.valueOf(task.getMessageId()),
                String.valueOf(task.getExecutionId()),
                String.valueOf(task.getParentExecutionId()),
                String.valueOf(task.getAttemptNo()),
                String.valueOf(task.getRetryCount()),
                String.valueOf(task.getMaxRetry()),
                String.valueOf(task.getTerminalReasonCode()),
                sha256Prefix(task.getFailureReason()),
                version == null ? "" : version.withNano(0).toString(),
                sha256Prefix(task.getPayload()));
        return sha256(source);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 100L);
    }

    private static final class RetryDispatch {

        private final String destination;
        private final MqMessage<?> envelope;
        private final AsyncTaskService.ManualRetryAttempt attempt;

        private RetryDispatch(String destination, MqMessage<?> envelope,
                              AsyncTaskService.ManualRetryAttempt attempt) {
            this.destination = destination;
            this.envelope = envelope;
            this.attempt = attempt;
        }
    }

    private record RetryIdentity(String messageId,
                                 String traceId,
                                 String executionId,
                                 String parentExecutionId,
                                 String idempotencyKey,
                                 int attemptNo,
                                 String executionToken,
                                 String retryPreviewHash) {
    }

    private static final class DeadLetterReplayDispatch {

        private final String destination;
        private final String bizType;
        private final String bizId;
        private final Long userId;
        private final Object payload;

        private DeadLetterReplayDispatch(String destination, String bizType,
                                         String bizId, Long userId, Object payload) {
            this.destination = destination;
            this.bizType = bizType;
            this.bizId = bizId;
            this.userId = userId;
            this.payload = payload;
        }
    }
}
