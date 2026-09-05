package com.codecoachai.interview.tts;

import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "codecoachai.interview.voice.tts",
        name = "provider",
        havingValue = "MOCK")
public class MockTtsProvider implements TtsProvider {

    private final long delayMs;

    public MockTtsProvider(@Value("${codecoachai.interview.tts.mock.delay-ms:0}") long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public String providerCode() {
        return "MOCK";
    }

    @Override
    public TtsSynthesisResult synthesize(TtsSynthesisRequest request, ProviderExecutionContext context) {
        context.checkActive();
        sleepCooperatively(context);
        context.checkActive();
        String payload = "MOCK_TTS|" + safe(request.getVoice(), "default") + "|" + request.getText();
        return TtsSynthesisResult.builder()
                .provider(providerCode())
                .contentType("application/x-codecoachai-tts-mock")
                .audio(payload.getBytes(StandardCharsets.UTF_8))
                .estimatedDurationMs(Math.max(300L, request.getText().length() * 120L))
                .build();
    }

    private void sleepCooperatively(ProviderExecutionContext context) {
        long remaining = delayMs;
        while (remaining > 0) {
            context.checkActive();
            long slice = Math.min(remaining, 20L);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                context.cancel();
                context.checkActive();
            }
            remaining -= slice;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
