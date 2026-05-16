package com.codecoachai.resume.controller;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeOptimizeRequestDTO;
import com.codecoachai.resume.domain.vo.ResumeOptimizeSubmitVO;
import com.codecoachai.resume.service.ResumeService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/sse")
public class AiSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;
    private static final int CHUNK_SIZE = 24;

    private final ResumeService resumeService;

    @GetMapping(value = "/resume-optimize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeOptimize(@RequestParam Long resumeId,
                                     @RequestParam(required = false) String targetPosition,
                                     @RequestParam(required = false) Integer experienceYears,
                                     @RequestParam(required = false) String industryDirection) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        LoginUser loginUser = LoginUserContext.getLoginUser();
        CompletableFuture.runAsync(() -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                send(emitter, "start", Map.of("message", "resume optimize stream started", "resumeId", resumeId));
                ResumeOptimizeRequestDTO dto = new ResumeOptimizeRequestDTO();
                dto.setTargetPosition(targetPosition);
                dto.setExperienceYears(experienceYears);
                dto.setIndustryDirection(industryDirection);
                ResumeOptimizeSubmitVO result = resumeService.optimizeResume(resumeId, dto);
                String fullContent = optimizeContent(result);
                sendDeltas(emitter, fullContent);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("resumeId", result.getResumeId());
                metadata.put("optimizeRecordId", result.getOptimizeRecordId());
                metadata.put("optimizeStatus", result.getOptimizeStatus());
                metadata.put("errorMessage", result.getErrorMessage());
                send(emitter, "metadata", metadata);
                send(emitter, "done", Map.of("fullContent", fullContent));
                emitter.complete();
            } catch (RuntimeException ex) {
                sendSilently(emitter, "error",
                        Map.of("code", "SSE_STREAM_ERROR", "message", "Streaming failed. Please retry later."));
                emitter.complete();
            } finally {
                LoginUserContext.clear();
            }
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

    private void sendDeltas(SseEmitter emitter, String content) {
        String value = content == null ? "" : content;
        int index = 1;
        for (int start = 0; start < value.length(); start += CHUNK_SIZE) {
            int end = Math.min(value.length(), start + CHUNK_SIZE);
            send(emitter, "delta", Map.of("index", index++, "content", value.substring(start, end)));
        }
        if (value.isEmpty()) {
            send(emitter, "delta", Map.of("index", index, "content", ""));
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void sendSilently(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ignored) {
            // Client disconnected; there is nothing else to clean up for this fallback stream.
        }
    }
}
