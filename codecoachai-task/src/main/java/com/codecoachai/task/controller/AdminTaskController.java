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

/**
 * 管理端异步任务与死信治理接口。
 * 负责查询任务执行状态、人工恢复死信消息和标记不可恢复消息。
 */
@Tag(name = "Task Admin")
@RestController
@RequestMapping("/admin/tasks")
@RequiredArgsConstructor
public class AdminTaskController {

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
        // 死信只允许从 UNHANDLED 恢复一次，防止管理员重复点击造成同一业务消息多次投递。
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
