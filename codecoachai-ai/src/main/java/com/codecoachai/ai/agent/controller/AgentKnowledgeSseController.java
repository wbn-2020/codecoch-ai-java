package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.domain.dto.KnowledgeAskDTO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeAskVO;
import com.codecoachai.ai.agent.domain.vo.knowledge.KnowledgeSearchResultVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.security.util.SecurityAssert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 个人知识库问答流式 SSE 接口。
 * 事件序列：references → token（多帧）→ citation → done；异常走 error 事件。
 * 同步接口 POST /agent/knowledge/ask 作为降级保留。
 */
@Slf4j
@RestController
@RequestMapping("/agent/knowledge")
@Tag(name = "Personal Knowledge Ask SSE")
public class AgentKnowledgeSseController {

    private static final long TIMEOUT_MILLIS = 120_000L;

    private final AgentV4OpsService agentV4OpsService;

    private final V4FeatureGate v4FeatureGate;

    public AgentKnowledgeSseController(AgentV4OpsService agentV4OpsService, V4FeatureGate v4FeatureGate) {
        this.agentV4OpsService = agentV4OpsService;
        this.v4FeatureGate = v4FeatureGate;
    }

    @Operation(summary = "Stream personal knowledge ask",
            description = "SSE endpoint. Emits references/token/citation/done/error. POST /agent/knowledge/ask remains the synchronous fallback.")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter askStream(@RequestBody KnowledgeAskDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        v4FeatureGate.requireKnowledgeEnabled();
        LoginUser loginUser = LoginUserContext.getLoginUser();
        String requestId = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        SseEmitter emitter = createEmitter(requestId, active);

        CompletableFuture.runAsync(() -> {
            try {
                LoginUserContext.setLoginUser(loginUser);
                send(emitter, active, "start", baseEvent(requestId, "Knowledge ask stream started"));
                agentV4OpsService.askKnowledgeStream(userId, dto, new AgentV4OpsService.KnowledgeAskStreamListener() {
                    @Override
                    public void onReferences(List<KnowledgeSearchResultVO> references) {
                        Map<String, Object> data = baseEvent(requestId, "references ready");
                        data.put("references", references);
                        data.put("referenceCount", references == null ? 0 : references.size());
                        send(emitter, active, "references", data);
                    }

                    @Override
                    public void onToken(String delta) {
                        Map<String, Object> data = baseEvent(requestId, null);
                        data.put("delta", delta);
                        send(emitter, active, "token", data);
                    }

                    @Override
                    public void onCitation(KnowledgeAskVO result) {
                        Map<String, Object> data = baseEvent(requestId, "citation validated");
                        data.put("answer", result.getAnswer());
                        data.put("citationValid", result.getCitationValid());
                        data.put("answerGrounded", result.getAnswerGrounded());
                        data.put("citedReferenceNumbers", result.getCitedReferenceNumbers());
                        data.put("invalidReferenceNumbers", result.getInvalidReferenceNumbers());
                        data.put("unsupportedSentences", result.getUnsupportedSentences());
                        data.put("citationWarning", result.getCitationWarning());
                        data.put("insufficientReferences", result.getInsufficientReferences());
                        send(emitter, active, "citation", data);
                    }

                    @Override
                    public void onDone(Long aiCallLogId) {
                        Map<String, Object> data = baseEvent(requestId, "completed");
                        data.put("aiCallLogId", aiCallLogId);
                        send(emitter, active, "done", data);
                        complete(emitter, active);
                    }

                    @Override
                    public void onError(String message) {
                        Map<String, Object> data = baseEvent(requestId,
                                message == null || message.isBlank() ? "Knowledge ask failed" : message);
                        data.put("code", "KNOWLEDGE_ASK_STREAM_FAILED");
                        send(emitter, active, "error", data);
                        complete(emitter, active);
                    }
                });
                // 兜底：若服务未触发 done/error，仍关闭连接
                complete(emitter, active);
            } catch (RuntimeException ex) {
                log.warn("Knowledge ask SSE failed, requestId={}", requestId, ex);
                Map<String, Object> data = baseEvent(requestId, "Knowledge ask failed");
                data.put("code", "KNOWLEDGE_ASK_STREAM_FAILED");
                send(emitter, active, "error", data);
                complete(emitter, active);
            } finally {
                LoginUserContext.clear();
            }
        });
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
            log.debug("Knowledge ask SSE connection error, requestId={}", requestId, ex);
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
            log.debug("Knowledge ask SSE send failed, event={}", event, ex);
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
            log.debug("Knowledge ask SSE complete ignored, requestId={}, reason={}", requestId, reason, ex);
        }
    }
}
