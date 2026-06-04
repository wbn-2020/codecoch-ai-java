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
import com.codecoachai.common.mq.payload.InterviewReportPayload;
import com.codecoachai.common.mq.payload.QuestionGeneratePayload;
import com.codecoachai.common.mq.payload.ResumeParsePayload;
import com.codecoachai.common.mq.payload.SearchSyncPayload;
import com.codecoachai.common.mq.producer.MqProducer;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.task.domain.dto.AdminTaskActionDTO;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.domain.vo.AdminAsyncTaskVO;
import com.codecoachai.task.domain.vo.AdminDeadLetterVO;
import com.codecoachai.task.domain.vo.AdminTaskImpactPreviewVO;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
    private static final String BIZ_QUESTION_GENERATE = "question.generate";
    private static final String BIZ_QUESTION_AI_GENERATE = "question.ai-generate";
    private static final String BIZ_INTERVIEW_REPORT = "interview.report";
    private static final String BIZ_SEARCH_SYNC = "search.sync";
    private static final String INDEX_QUESTION = "cc_question";
    private static final String INDEX_RESUME = "cc_resume";
    private static final String INDEX_INTERVIEW = "cc_interview";

    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageDeadLetterMapper deadLetterMapper;
    private final Optional<MqProducer> mqProducer;
    private final ObjectMapper objectMapper;
    private final AdminPermissionGuard permissionGuard;

    @Operation(summary = "Page async tasks")
    @GetMapping
    public Result<PageResult<AdminAsyncTaskVO>> pageTasks(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId) {
        permissionGuard.require(PERM_TASK_LIST);
        // type 是早期管理页字段，bizType 是当前实体字段；统一解析后再查询。
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
        return Result.success(PageResult.of(page.getRecords().stream().map(this::toTaskVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
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

    @Operation(summary = "Task status stats")
    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> stats() {
        permissionGuard.require(PERM_TASK_LIST);
        List<Map<String, Object>> counts = asyncTaskMapper.selectMaps(
                new QueryWrapper<AsyncTask>()
                        .select("status", "COUNT(*) AS count")
                        .groupBy("status"));
        return Result.success(counts);
    }

    @Operation(summary = "Preview failed async task retry impact")
    @GetMapping("/{id}/retry-preview")
    public Result<AdminTaskImpactPreviewVO> retryTaskPreview(@PathVariable Long id) {
        permissionGuard.require(PERM_TASK_RETRY);
        AsyncTask task = getTaskEntity(id);
        return Result.success(taskRetryPreview(task));
    }

    @Operation(summary = "Retry failed async task")
    @PostMapping("/{id}/retry")
    @OperationLog(module = "task", action = "RETRY_ASYNC_TASK", description = "重试失败异步任务")
    public Result<Void> retryTask(@PathVariable Long id,
                                  @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        requireActionNote(dto);
        AsyncTask task = getTaskEntity(id);
        if (!isRetryableTaskStatus(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Only FAILED/DEAD tasks can be retried");
        }
        // 这里仅把任务状态退回 PENDING；真正重新投递由对应业务补偿入口或死信恢复流程触发。
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
    public Result<PageResult<AdminDeadLetterVO>> pageDeadLetters(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize,
            @RequestParam(required = false) String handleStatus,
            @RequestParam(required = false) String bizType) {
        permissionGuard.require(PERM_TASK_LIST);
        Page<MessageDeadLetter> page = deadLetterMapper.selectPage(
                Page.of(pageNo, pageSize),
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
    @OperationLog(module = "task", action = "RECOVER_DEAD_LETTER", description = "恢复死信消息")
    public Result<Void> recoverDeadLetter(@PathVariable Long id,
                                          @RequestParam(required = false) String note,
                                          @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        String actionNote = requireActionNote(note, dto);
        MessageDeadLetter dl = getRecoverableDeadLetter(id);
        replayDeadLetter(dl);
        updateDeadLetterStatus(id, "RECOVERED", actionNote);
        return Result.success();
    }

    @Operation(summary = "Ignore dead letter")
    @PostMapping("/dead-letters/{id}/ignore")
    @OperationLog(module = "task", action = "IGNORE_DEAD_LETTER", description = "忽略死信消息")
    public Result<Void> ignoreDeadLetter(@PathVariable Long id,
                                         @RequestParam(required = false) String note,
                                         @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        updateDeadLetterStatus(id, "IGNORED", requireActionNote(note, dto));
        return Result.success();
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
    @OperationLog(module = "task", action = "RECOVER_DEAD_LETTER_COMPAT", description = "兼容入口恢复死信消息")
    public Result<Void> recoverDeadLetterCompat(@PathVariable Long id,
                                                @RequestParam(required = false) String note,
                                                @RequestBody(required = false) AdminTaskActionDTO dto) {
        permissionGuard.require(PERM_TASK_RETRY);
        String actionNote = requireActionNote(note, dto);
        MessageDeadLetter dl = getRecoverableDeadLetterCompat(id);
        replayDeadLetter(dl);
        updateDeadLetterStatus(dl.getId(), "RECOVERED", actionNote);
        return Result.success();
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

    private String requireActionNote(AdminTaskActionDTO dto) {
        return requireActionNote(null, dto);
    }

    private String requireActionNote(String note, AdminTaskActionDTO dto) {
        String resolved = StringUtils.hasText(note) ? note : dto == null ? null : dto.getNote();
        if (!StringUtils.hasText(resolved)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "operation note is required");
        }
        return resolved.trim();
    }

    private AdminTaskImpactPreviewVO taskRetryPreview(AsyncTask task) {
        AdminTaskImpactPreviewVO vo = new AdminTaskImpactPreviewVO();
        vo.setId(task.getId());
        vo.setTargetType("ASYNC_TASK");
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setUserId(task.getUserId());
        vo.setCurrentStatus(task.getStatus());
        vo.setExecutable(isRetryableTaskStatus(task.getStatus()));
        vo.setRiskLevel("MEDIUM");
        vo.setRequiredPermission(PERM_TASK_RETRY);
        vo.setRequiredNote("请填写失败原因已处理的说明");
        vo.setImpact("将任务状态重置为 PENDING，后续由对应补偿流程重新执行；非幂等业务可能产生重复 AI 调用或重复解析。");
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

    private AdminAsyncTaskVO toTaskVO(AsyncTask task) {
        AdminAsyncTaskVO vo = new AdminAsyncTaskVO();
        vo.setId(task.getId());
        vo.setMessageId(task.getMessageId());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setUserId(task.getUserId());
        vo.setTraceId(task.getTraceId());
        vo.setStatus(task.getStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setMaxRetryCount(task.getMaxRetry());
        vo.setFailureReason(maskText(task.getFailureReason()));
        vo.setPayloadPreview(preview(task.getPayload()));
        vo.setPayloadHash(sha256Prefix(task.getPayload()));
        vo.setResultPreview(preview(task.getResult()));
        vo.setResultHash(sha256Prefix(task.getResult()));
        vo.setRawFieldsAvailable(StringUtils.hasText(task.getPayload()) || StringUtils.hasText(task.getResult()));
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
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

    private void replayDeadLetter(MessageDeadLetter dl) {
        // 恢复死信时按 bizType 还原到原 Topic/Tag，payload 校验失败则拒绝恢复，避免投递脏消息。
        MqProducer producer = mqProducer.orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ producer is not available"));
        if (BIZ_RESUME_PARSE.equals(dl.getBizType())) {
            ResumeParsePayload payload = readPayload(dl.getPayload(), ResumeParsePayload.class);
            if (payload == null || payload.getResumeId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter resume payload is invalid");
            }
            producer.sendSync(MqTopics.dest(MqTopics.RESUME, MqTopics.RESUME_TAG_PARSE),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getResumeId()),
                    dl.getUserId(), payload);
            return;
        }
        if (BIZ_QUESTION_GENERATE.equals(dl.getBizType()) || BIZ_QUESTION_AI_GENERATE.equals(dl.getBizType())) {
            QuestionGeneratePayload payload = readPayload(dl.getPayload(), QuestionGeneratePayload.class);
            if (payload == null || payload.getBatchId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter question payload is invalid");
            }
            producer.sendSync(MqTopics.dest(MqTopics.QUESTION, MqTopics.QUESTION_TAG_AI_GENERATE),
                    BIZ_QUESTION_AI_GENERATE, resolveBizId(dl.getBizId(), payload.getBatchId()),
                    dl.getUserId(), payload);
            return;
        }
        if (BIZ_INTERVIEW_REPORT.equals(dl.getBizType())) {
            InterviewReportPayload payload = readPayload(dl.getPayload(), InterviewReportPayload.class);
            if (payload == null || payload.getSessionId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter interview payload is invalid");
            }
            producer.sendSync(MqTopics.dest(MqTopics.INTERVIEW, MqTopics.INTERVIEW_TAG_REPORT),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getSessionId()),
                    dl.getUserId(), payload);
            return;
        }
        if (BIZ_SEARCH_SYNC.equals(dl.getBizType())) {
            SearchSyncPayload payload = readPayload(dl.getPayload(), SearchSyncPayload.class);
            if (payload == null || !StringUtils.hasText(payload.getIndexName()) || !StringUtils.hasText(payload.getDocId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "dead letter search payload is invalid");
            }
            producer.sendSync(MqTopics.dest(MqTopics.SEARCH, resolveSearchTag(payload.getIndexName())),
                    dl.getBizType(), resolveBizId(dl.getBizId(), payload.getDocId()),
                    dl.getUserId(), payload);
            return;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported dead letter bizType: " + dl.getBizType());
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
        throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported search index: " + indexName);
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
            // 记录处理人用于后续审计，未登录上下文下只更新状态与备注。
            wrapper.set(MessageDeadLetter::getHandlerUserId, handlerUserId);
        }
        deadLetterMapper.update(null, wrapper);
    }
}
