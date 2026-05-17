package com.codecoachai.question.controller;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.SecurityAssert;
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
@Tag(name = "Admin AI Question SSE", description = "Admin SSE APIs for AI question generation. /inner/** AI APIs are internal only and must not be called by frontend clients.")
public class AdminAiQuestionSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;

    private final QuestionReviewService questionReviewService;
    private final Executor questionSseStreamExecutor;

    public AdminAiQuestionSseController(QuestionReviewService questionReviewService,
                                        @Qualifier("questionSseStreamExecutor") Executor questionSseStreamExecutor) {
        this.questionReviewService = questionReviewService;
        this.questionSseStreamExecutor = questionSseStreamExecutor;
    }

    @Operation(summary = "Stream AI question generation progress",
            description = "Admin SSE endpoint. Emits start/progress/result/done/error events for AI question generation. POST /admin/ai/questions/generate remains the synchronous fallback.")
    @GetMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter generate(@ModelAttribute AiQuestionGenerateRequestDTO dto) {
        SecurityAssert.requireAdmin();
        LoginUser loginUser = LoginUserContext.getLoginUser();
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        CompletableFuture.runAsync(() -> executeGenerate(emitter, active, requestId, loginUser, dto),
                questionSseStreamExecutor);
        return emitter;
    }

    private void executeGenerate(SseEmitter emitter, AtomicBoolean active, String requestId,
                                 LoginUser loginUser, AiQuestionGenerateRequestDTO dto) {
        try {
            LoginUserContext.setLoginUser(loginUser);
            if (!send(emitter, active, "start", event(requestId, "start", "AI question generation started"))) {
                return;
            }
            if (!sendProgress(emitter, active, requestId, "VALIDATE_REQUEST", "Validating generation request")) {
                return;
            }
            if (!sendProgress(emitter, active, requestId, "BUILD_PROMPT", "Preparing AI question generation prompt")) {
                return;
            }
            if (!sendProgress(emitter, active, requestId, "CALL_AI", "Calling AI question generation service")) {
                return;
            }
            AiQuestionGenerateResultVO result = questionReviewService.generate(dto);
            if (!sendProgress(emitter, active, requestId, "SAVE_REVIEW", "Saving generated questions to review pool")) {
                return;
            }
            if (!send(emitter, active, "result", resultEvent(requestId, result))) {
                return;
            }
            send(emitter, active, "done", doneEvent(requestId, result));
            complete(emitter, active);
        } catch (RuntimeException ex) {
            log.warn("AI question generation SSE failed, requestId={}", requestId, ex);
            send(emitter, active, "error", errorEvent(requestId));
            complete(emitter, active);
        } finally {
            LoginUserContext.clear();
        }
    }

    private boolean sendProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                 String stage, String message) {
        Map<String, Object> data = event(requestId, "progress", message);
        data.put("stage", stage);
        return send(emitter, active, "progress", data);
    }

    private Map<String, Object> resultEvent(String requestId, AiQuestionGenerateResultVO result) {
        Map<String, Object> data = event(requestId, "result", "AI question generation completed");
        data.put("batchId", result == null ? null : result.getBatchId());
        data.put("reviewIds", result == null ? null : result.getReviewIds());
        data.put("aiCallLogId", result == null ? null : result.getAiCallLogId());
        data.put("count", result == null ? null : result.getGeneratedCount());
        data.put("successCount", result == null ? null : result.getGeneratedCount());
        return data;
    }

    private Map<String, Object> doneEvent(String requestId, AiQuestionGenerateResultVO result) {
        Map<String, Object> data = event(requestId, "done", "AI question generation done");
        data.put("batchId", result == null ? null : result.getBatchId());
        return data;
    }

    private Map<String, Object> errorEvent(String requestId) {
        Map<String, Object> data = event(requestId, "error", "AI question generation failed. Please retry later.");
        data.put("aiCallLogId", null);
        return data;
    }

    private Map<String, Object> event(String requestId, String type, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("type", type);
        data.put("message", message);
        return data;
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
