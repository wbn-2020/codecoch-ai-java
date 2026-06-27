package com.codecoachai.question.controller;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
import com.codecoachai.common.security.admin.AdminOperationConfirmationGuard;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.question.domain.dto.AiQuestionGenerateRequestDTO;
import com.codecoachai.question.domain.vo.AiQuestionGenerateResultVO;
import com.codecoachai.question.service.QuestionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ai/sse/admin/questions")
@Tag(name = "后台 AI 出题进度", description = "后台 AI 出题的实时进度接口。/inner/** AI 接口仅供内部服务调用。")
public class AdminAiQuestionSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;
    private static final String PERM_QUESTION_GENERATE = "admin:question:generate";

    private final QuestionReviewService questionReviewService;
    private final Executor questionSseStreamExecutor;
    private final AdminPermissionGuard adminPermissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    public AdminAiQuestionSseController(QuestionReviewService questionReviewService,
                                        @Qualifier("questionSseStreamExecutor") Executor questionSseStreamExecutor,
                                        AdminPermissionGuard adminPermissionGuard,
                                        AdminOperationConfirmationGuard operationConfirmationGuard) {
        this.questionReviewService = questionReviewService;
        this.questionSseStreamExecutor = questionSseStreamExecutor;
        this.adminPermissionGuard = adminPermissionGuard;
        this.operationConfirmationGuard = operationConfirmationGuard;
    }

    @Operation(summary = "实时返回 AI 出题进度",
            description = "后台 SSE 接口，返回出题的开始、进度、结果、完成和失败事件。POST /admin/ai/questions/generate 保留同步兜底。")
    @OperationLog(module = "question", action = "SSE_GENERATE_AI_QUESTION", description = "Stream AI question draft generation", logArgs = false, logResponse = false)
    @GetMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter generate(@ModelAttribute AiQuestionGenerateRequestDTO dto) {
        adminPermissionGuard.require(PERM_QUESTION_GENERATE);
        String lockKey = requireConfirmedGenerate(dto);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        try {
            submitGenerate(emitter, active, requestId, loginUser, dto, lockKey);
        } catch (RejectedExecutionException ex) {
            log.warn("AI question generation SSE task rejected, requestId={}", requestId, ex);
            operationConfirmationGuard.release(lockKey);
            send(emitter, active, "error", errorEvent(requestId));
            complete(emitter, active);
        }
        return emitter;
    }

    private void submitGenerate(SseEmitter emitter, AtomicBoolean active, String requestId,
                                LoginUser loginUser, AiQuestionGenerateRequestDTO dto, String lockKey) {
        CompletableFuture.runAsync(() -> executeGenerate(emitter, active, requestId, loginUser, dto, lockKey),
                questionSseStreamExecutor);
    }

    private void executeGenerate(SseEmitter emitter, AtomicBoolean active, String requestId,
                                 LoginUser loginUser, AiQuestionGenerateRequestDTO dto, String lockKey) {
        boolean generationAttempted = false;
        try {
            LoginUserContext.setLoginUser(loginUser);
            if (!send(emitter, active, "start", event(requestId, "start", "AI 出题已开始"))) {
                return;
            }
            if (!sendProgress(emitter, active, requestId, "VALIDATE_REQUEST", "正在校验出题参数")) {
                return;
            }
            if (!sendProgress(emitter, active, requestId, "BUILD_PROMPT", "正在整理出题要求")) {
                return;
            }
            if (!sendProgress(emitter, active, requestId, "CALL_AI", "正在生成题目")) {
                return;
            }
            generationAttempted = true;
            AiQuestionGenerateResultVO result = questionReviewService.generate(dto);
            if (!sendProgress(emitter, active, requestId, "SAVE_REVIEW", "正在保存待审核题目")) {
                return;
            }
            if (!send(emitter, active, "metadata", metadataEvent(requestId, result))) {
                return;
            }
            if (!send(emitter, active, "result", resultEvent(requestId, result))) {
                return;
            }
            send(emitter, active, "done", doneEvent(requestId, result));
            complete(emitter, active);
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            log.warn("AI question generation SSE failed, requestId={}", requestId, ex);
            send(emitter, active, "error", errorEvent(requestId));
            complete(emitter, active);
        } finally {
            if (!generationAttempted && !active.get()) {
                operationConfirmationGuard.release(lockKey);
            }
            LoginUserContext.clear();
        }
    }

    private String requireConfirmedGenerate(AiQuestionGenerateRequestDTO dto) {
        return operationConfirmationGuard.requireConfirmed(
                "question-ai-generate-sse",
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey());
    }

    private boolean sendProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                 String stage, String message) {
        if (!send(emitter, active, "delta", deltaEvent(requestId, stage, message))) {
            return false;
        }
        if (!send(emitter, active, "metadata", stageMetadataEvent(requestId, stage, message))) {
            return false;
        }
        Map<String, Object> data = event(requestId, "progress", message);
        data.put("stage", stage);
        return send(emitter, active, "progress", data);
    }

    private Map<String, Object> deltaEvent(String requestId, String stage, String message) {
        Map<String, Object> data = event(requestId, "delta", message);
        data.put("stage", stage);
        data.put("content", message);
        data.put("metadata", stageMetadata(stage, "PROCESSING"));
        return data;
    }

    private Map<String, Object> stageMetadataEvent(String requestId, String stage, String message) {
        Map<String, Object> data = event(requestId, "metadata", message);
        data.put("stage", stage);
        data.put("metadata", stageMetadata(stage, "PROCESSING"));
        return data;
    }

    private Map<String, Object> resultEvent(String requestId, AiQuestionGenerateResultVO result) {
        Map<String, Object> data = event(requestId, "result", "题目生成已完成");
        data.put("batchId", result == null ? null : result.getBatchId());
        data.put("reviewIds", result == null ? null : result.getReviewIds());
        data.put("aiCallLogId", result == null ? null : result.getAiCallLogId());
        data.put("count", result == null ? null : result.getGeneratedCount());
        data.put("successCount", result == null ? null : result.getGeneratedCount());
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> doneEvent(String requestId, AiQuestionGenerateResultVO result) {
        Map<String, Object> data = event(requestId, "done", "题目生成已完成");
        data.put("batchId", result == null ? null : result.getBatchId());
        data.put("result", result);
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> metadataEvent(String requestId, AiQuestionGenerateResultVO result) {
        Map<String, Object> data = event(requestId, "metadata", "题目生成结果已记录");
        data.put("batchId", result == null ? null : result.getBatchId());
        data.put("reviewIds", result == null ? null : result.getReviewIds());
        data.put("aiCallLogId", result == null ? null : result.getAiCallLogId());
        data.put("count", result == null ? null : result.getGeneratedCount());
        data.put("successCount", result == null ? null : result.getGeneratedCount());
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> errorEvent(String requestId) {
        Map<String, Object> data = event(requestId, "error", "题目生成暂时失败，请稍后重试。");
        data.put("aiCallLogId", null);
        data.put("code", "AI_QUESTION_GENERATE_FAILED");
        data.put("metadata", stageMetadata("ERROR", "FAILED"));
        return data;
    }

    private Map<String, Object> event(String requestId, String type, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("type", type);
        data.put("message", message);
        return data;
    }

    private Map<String, Object> resultMetadata(AiQuestionGenerateResultVO result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result == null) {
            return metadata;
        }
        metadata.put("batchId", result.getBatchId());
        metadata.put("reviewIds", result.getReviewIds());
        metadata.put("aiCallLogId", result.getAiCallLogId());
        metadata.put("count", result.getGeneratedCount());
        metadata.put("successCount", result.getGeneratedCount());
        metadata.put("status", "SUCCESS");
        return metadata;
    }

    private Map<String, Object> stageMetadata(String stage, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", stage);
        metadata.put("status", status);
        return metadata;
    }

    private SseEmitter createEmitter(String requestId, AtomicBoolean active) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onCompletion(() -> active.set(false));
        emitter.onTimeout(() -> {
            active.set(false);
            completeQuietly(emitter, requestId, "timeout");
        });
        emitter.onError(ex -> {
            active.set(false);
            log.debug("AI question generation SSE connection error, requestId={}", requestId, ex);
        });
        return emitter;
    }

    private boolean send(SseEmitter emitter, AtomicBoolean active, String event, Object data) {
        if (!active.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            return true;
        } catch (IOException | IllegalStateException ex) {
            active.set(false);
            log.debug("AI question generation SSE send failed, event={}", event, ex);
            return false;
        }
    }

    private void complete(SseEmitter emitter, AtomicBoolean active) {
        if (active.getAndSet(false)) {
            emitter.complete();
        }
    }

    private void completeQuietly(SseEmitter emitter, String requestId, String reason) {
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("AI question generation SSE complete ignored, requestId={}, reason={}", requestId, reason, ex);
        }
    }
}
