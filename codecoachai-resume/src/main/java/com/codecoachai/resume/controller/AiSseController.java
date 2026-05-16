package com.codecoachai.resume.controller;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.service.ResumeService;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ai/sse")
public class AiSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;
    private static final int CHUNK_SIZE = 48;

    private final ResumeService resumeService;
    private final Executor resumeSseStreamExecutor;

    public AiSseController(ResumeService resumeService,
                           @Qualifier("resumeSseStreamExecutor") Executor resumeSseStreamExecutor) {
        this.resumeService = resumeService;
        this.resumeSseStreamExecutor = resumeSseStreamExecutor;
    }

    @GetMapping(value = "/resume-optimize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeOptimize(@RequestParam Long resumeId,
                                     @RequestParam(required = false) String targetPosition,
                                     @RequestParam(required = false) Integer experienceYears,
                                     @RequestParam(required = false) String industryDirection) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        CompletableFuture.runAsync(() -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                send(emitter, active, "start", Map.of(
                        "requestId", requestId,
                        "message", "resume optimize stream started",
                        "resumeId", resumeId));
                ResumeOptimizeRequestDTO dto = new ResumeOptimizeRequestDTO();
                dto.setTargetPosition(targetPosition);
                dto.setExperienceYears(experienceYears);
                dto.setIndustryDirection(industryDirection);
                ResumeOptimizeSubmitVO result = resumeService.optimizeResume(resumeId, dto);
                String fullContent = optimizeContent(result);
                sendChunks(emitter, active, requestId, fullContent);
                send(emitter, active, "metadata", metadata(requestId, result));
                send(emitter, active, "done", Map.of("requestId", requestId, "fullContent", fullContent));
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Resume SSE stream task failed, requestId={}", requestId, ex);
                send(emitter, active, "error", Map.of(
                        "requestId", requestId,
                        "code", "SSE_STREAM_ERROR",
                        "message", "Streaming failed. Please retry later."));
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        }, resumeSseStreamExecutor);
        return emitter;
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

    private String optimizeContent(ResumeOptimizeSubmitVO result) {
        if (result == null) {
            return "";
        }
        if (result.getResultJson() != null) {
            return result.getResultJson().toString();
        }
        if (StringUtils.hasText(result.getErrorMessage())) {
            return "Resume optimization failed";
        }
        return "Resume optimization submitted";
    }

    private Map<String, Object> metadata(String requestId, ResumeOptimizeSubmitVO result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        if (result == null) {
            return metadata;
        }
        metadata.put("resumeId", result.getResumeId());
        metadata.put("optimizeRecordId", result.getOptimizeRecordId());
        metadata.put("optimizeStatus", result.getOptimizeStatus());
        metadata.put("errorMessage", result.getErrorMessage());
        return metadata;
    }

    private void sendChunks(SseEmitter emitter, AtomicBoolean active, String requestId, String content) {
        String value = content == null ? "" : content;
        int index = 1;
        for (int start = 0; start < value.length(); start += CHUNK_SIZE) {
            int end = Math.min(value.length(), start + CHUNK_SIZE);
            Map<String, Object> event = Map.of(
                    "requestId", requestId,
                    "index", index++,
                    "content", value.substring(start, end));
            if (!send(emitter, active, "chunk", event)) {
                return;
            }
            if (!send(emitter, active, "delta", event)) {
                return;
            }
        }
        if (value.isEmpty()) {
            Map<String, Object> event = Map.of("requestId", requestId, "index", index, "content", "");
            send(emitter, active, "chunk", event);
            send(emitter, active, "delta", event);
        }
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
