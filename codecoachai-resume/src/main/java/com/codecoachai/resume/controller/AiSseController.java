package com.codecoachai.resume.controller;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.service.ResumeService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ai/sse")
@Tag(name = "AI SSE")
public class AiSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;

    private final ResumeService resumeService;
    private final Executor resumeSseStreamExecutor;

    public AiSseController(ResumeService resumeService,
                           @Qualifier("resumeSseStreamExecutor") Executor resumeSseStreamExecutor) {
        this.resumeService = resumeService;
        this.resumeSseStreamExecutor = resumeSseStreamExecutor;
    }

    @Operation(summary = "Stream resume optimization progress",
            description = "User SSE endpoint. Emits start/progress/result/done/error events for resume optimization. POST /resumes/{id}/optimize remains the synchronous fallback.")
    @GetMapping(value = "/resume-optimize", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter resumeOptimize(@RequestParam Long resumeId,
                                     @RequestParam(required = false) String targetPosition,
                                     @RequestParam(required = false) String targetCompany,
                                     @RequestParam(required = false) String extraRequirements,
                                     @RequestParam(required = false) String optimizeFocus,
                                     @RequestParam(required = false) Integer experienceYears,
                                     @RequestParam(required = false) String industryDirection) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        CompletableFuture.runAsync(() -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                if (!send(emitter, active, "start", event(requestId, "start", "简历优化开始", resumeId, null, null, null))) {
                    return;
                }
                if (!sendProgress(emitter, active, requestId, resumeId, "LOAD_RESUME", "正在校验简历归属")) {
                    return;
                }
                if (!sendProgress(emitter, active, requestId, resumeId, "BUILD_PROMPT", "正在准备简历优化上下文")) {
                    return;
                }
                if (!sendProgress(emitter, active, requestId, resumeId, "CALL_AI", "正在调用 AI 生成优化建议")) {
                    return;
                }
                ResumeOptimizeRequestDTO dto = new ResumeOptimizeRequestDTO();
                dto.setTargetPosition(targetPosition);
                dto.setTargetCompany(targetCompany);
                dto.setExtraRequirements(extraRequirements);
                dto.setOptimizeFocus(optimizeFocus);
                dto.setExperienceYears(experienceYears);
                dto.setIndustryDirection(industryDirection);
                ResumeOptimizeSubmitVO result = resumeService.optimizeResume(resumeId, dto);
                if (!sendProgress(emitter, active, requestId, resumeId, "SAVE_RECORD", "正在保存简历优化记录")) {
                    return;
                }
                if (result == null || ResumeOptimizeStatus.FAILED.getCode().equals(result.getOptimizeStatus())) {
                    send(emitter, active, "error", errorEvent(requestId, resumeId, result));
                    complete(emitter, active);
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
                log.warn("Resume SSE stream task failed, requestId={}", requestId, ex);
                send(emitter, active, "error", errorEvent(requestId, resumeId, null));
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        }, resumeSseStreamExecutor);
        return emitter;
    }

    private boolean sendProgress(SseEmitter emitter, AtomicBoolean active, String requestId,
                                 Long resumeId, String stage, String message) {
        if (!send(emitter, active, "delta", deltaEvent(requestId, resumeId, stage, message))) {
            return false;
        }
        if (!send(emitter, active, "metadata", stageMetadataEvent(requestId, resumeId, stage, message))) {
            return false;
        }
        Map<String, Object> data = event(requestId, "progress", message, resumeId, null, null, null);
        data.put("stage", stage);
        return send(emitter, active, "progress", data);
    }

    private Map<String, Object> deltaEvent(String requestId, Long resumeId, String stage, String message) {
        Map<String, Object> data = event(requestId, "delta", message, resumeId, null, null, null);
        data.put("stage", stage);
        data.put("content", message);
        data.put("metadata", stageMetadata(stage, "PROCESSING"));
        return data;
    }

    private Map<String, Object> stageMetadataEvent(String requestId, Long resumeId, String stage, String message) {
        Map<String, Object> data = event(requestId, "metadata", message, resumeId, null, null, null);
        data.put("stage", stage);
        data.put("metadata", stageMetadata(stage, "PROCESSING"));
        return data;
    }

    private Map<String, Object> resultEvent(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> data = event(requestId, "result", "简历优化结果已生成",
                result.getResumeId(), result.getOptimizeRecordId(), result.getAiCallLogId(), result.getResultJson());
        data.put("optimizeStatus", result.getOptimizeStatus());
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> doneEvent(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> data = event(requestId, "done", "简历优化完成",
                result.getResumeId(), result.getOptimizeRecordId(), result.getAiCallLogId(), null);
        data.put("result", result.getResultJson());
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> metadataEvent(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> data = event(requestId, "metadata", "简历优化结果元数据",
                result.getResumeId(), result.getOptimizeRecordId(), result.getAiCallLogId(), null);
        data.put("metadata", resultMetadata(result));
        return data;
    }

    private Map<String, Object> errorEvent(String requestId, Long resumeId, ResumeOptimizeSubmitVO result) {
        Long resolvedResumeId = result == null ? resumeId : result.getResumeId();
        Long recordId = result == null ? null : result.getOptimizeRecordId();
        Long aiCallLogId = result == null ? null : result.getAiCallLogId();
        Map<String, Object> data = event(requestId, "error", "简历优化失败，请稍后重试",
                resolvedResumeId, recordId, aiCallLogId, null);
        data.put("code", "RESUME_OPTIMIZE_FAILED");
        data.put("metadata", result == null ? stageMetadata("ERROR", "FAILED") : resultMetadata(result));
        return data;
    }

    private Map<String, Object> resultMetadata(ResumeOptimizeSubmitVO result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result == null) {
            return metadata;
        }
        metadata.put("resumeId", result.getResumeId());
        metadata.put("recordId", result.getOptimizeRecordId());
        metadata.put("aiCallLogId", result.getAiCallLogId());
        metadata.put("status", result.getOptimizeStatus());
        metadata.put("optimizeStatus", result.getOptimizeStatus());
        return metadata;
    }

    private Map<String, Object> stageMetadata(String stage, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", stage);
        metadata.put("status", status);
        return metadata;
    }

    private Map<String, Object> event(String requestId, String type, String message, Long resumeId,
                                      Long recordId, Long aiCallLogId, Object result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("type", type);
        data.put("message", message);
        data.put("resumeId", resumeId);
        data.put("recordId", recordId);
        data.put("aiCallLogId", aiCallLogId);
        if (result != null) {
            data.put("result", result);
        }
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
            log.debug("Resume SSE connection error, requestId={}", requestId, ex);
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
            log.debug("Resume SSE send failed, event={}", event, ex);
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
            log.debug("Resume SSE complete ignored, requestId={}, reason={}", requestId, reason, ex);
        }
    }
}
