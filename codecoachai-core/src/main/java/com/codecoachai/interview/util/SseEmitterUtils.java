package com.codecoachai.interview.util;

import com.codecoachai.interview.domain.vo.SseEventVO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public final class SseEmitterUtils {

    public static final long DEFAULT_TIMEOUT_MILLIS = 120_000L;
    private static final int CHUNK_SIZE = 48;

    private SseEmitterUtils() {
    }

    public static SseEmitter createEmitter(String requestId, AtomicBoolean active) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
        emitter.onCompletion(() -> active.set(false));
        emitter.onTimeout(() -> {
            active.set(false);
            completeQuietly(emitter, requestId, "timeout");
        });
        emitter.onError(ex -> {
            active.set(false);
            log.debug("SSE connection error, requestId={}", requestId, ex);
        });
        return emitter;
    }

    public static boolean send(SseEmitter emitter, AtomicBoolean active, String eventName, SseEventVO data) {
        if (!active.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException | IllegalStateException ex) {
            active.set(false);
            log.debug("SSE send failed, requestId={}, event={}", data == null ? null : data.getRequestId(), eventName, ex);
            return false;
        }
    }

    public static void complete(SseEmitter emitter, AtomicBoolean active) {
        if (active.getAndSet(false)) {
            emitter.complete();
        }
    }

    public static void completeQuietly(SseEmitter emitter, String requestId, String reason) {
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("SSE complete ignored, requestId={}, reason={}", requestId, reason, ex);
        }
    }

    public static List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            current.append(ch);
            if (current.length() >= CHUNK_SIZE || isSentenceEnd(ch)) {
                addChunk(chunks, current);
            }
        }
        addChunk(chunks, current);
        return chunks;
    }

    private static boolean isSentenceEnd(char ch) {
        return ch == '.' || ch == '?' || ch == '!' || ch == ';'
                || ch == '\n' || ch == '\r'
                || ch == '\u3002' || ch == '\uff1f' || ch == '\uff01' || ch == '\uff1b';
    }

    private static void addChunk(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        String chunk = current.toString();
        if (StringUtils.hasText(chunk)) {
            chunks.add(chunk);
        }
        current.setLength(0);
    }
}
