package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.EvaluateAnswerDTO;
import com.codecoachai.ai.domain.vo.EvaluateAnswerVO;
import com.codecoachai.ai.service.AiService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/inner/ai/interview")
public class InnerInterviewAiStreamController {

    private static final long TIMEOUT_MILLIS = 120_000L;

    private final AiService aiService;

    private final Executor aiSseStreamExecutor;

    public InnerInterviewAiStreamController(AiService aiService,
                                            @Qualifier("aiSseStreamExecutor") Executor aiSseStreamExecutor) {
        this.aiService = aiService;
        this.aiSseStreamExecutor = aiSseStreamExecutor;
    }

    @PostMapping(value = "/evaluate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter evaluateStream(@RequestBody EvaluateAnswerDTO dto) {
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);
        try {
            CompletableFuture.runAsync(() -> {
            try {
                send(emitter, active, "start", baseEvent(requestId, "answer evaluation stream started"));
                AtomicBoolean emittedToken = new AtomicBoolean(false);
                EvaluateAnswerVO result = aiService.evaluateStream(dto, token -> {
                    emittedToken.set(true);
                    Map<String, Object> data = baseEvent(requestId, token);
                    data.put("type", "token");
                    data.put("content", token);
                    send(emitter, active, "token", data);
                });
                Map<String, Object> resultEvent = baseEvent(requestId, "answer evaluation completed");
                resultEvent.put("type", "result");
                resultEvent.put("result", result);
                resultEvent.put("aiCallLogId", result == null ? null : result.getAiCallLogId());
                resultEvent.put("tokenStreamed", emittedToken.get());
                send(emitter, active, "result", resultEvent);

                Map<String, Object> doneEvent = baseEvent(requestId, "answer evaluation stream completed");
                doneEvent.put("type", "done");
                doneEvent.put("aiCallLogId", result == null ? null : result.getAiCallLogId());
                doneEvent.put("tokenStreamed", emittedToken.get());
                send(emitter, active, "done", doneEvent);
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Inner interview answer evaluation stream failed, requestId={}", requestId, ex);
                Map<String, Object> error = baseEvent(requestId, "answer evaluation stream failed");
                error.put("type", "error");
                error.put("code", "INTERVIEW_ANSWER_EVALUATE_STREAM_FAILED");
                send(emitter, active, "error", error);
                complete(emitter, active);
            }
            }, aiSseStreamExecutor);
        } catch (RejectedExecutionException ex) {
            log.warn("Inner interview answer evaluation stream executor rejected, requestId={}", requestId, ex);
            Map<String, Object> error = baseEvent(requestId, "answer evaluation stream is busy, please retry later");
            error.put("type", "error");
            error.put("code", "INTERVIEW_ANSWER_EVALUATE_STREAM_FAILED");
            send(emitter, active, "error", error);
            complete(emitter, active);
        }
        return emitter;
    }

    private Map<String, Object> baseEvent(String requestId, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        if (message != null) {
            data.put("message", message);
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
            log.debug("Inner interview answer evaluation stream error, requestId={}", requestId, ex);
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
            log.debug("Inner interview answer evaluation stream send failed, event={}", event, ex);
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
            log.debug("Inner interview answer evaluation stream complete ignored, requestId={}, reason={}",
                    requestId, reason, ex);
        }
    }
}
