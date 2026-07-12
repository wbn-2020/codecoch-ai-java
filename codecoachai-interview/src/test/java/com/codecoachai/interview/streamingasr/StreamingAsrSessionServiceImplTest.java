package com.codecoachai.interview.streamingasr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.config.InterviewVoiceProviderProperties;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class StreamingAsrSessionServiceImplTest {

    private StreamingAsrSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(7L).build());
        service = serviceWithProvider("MOCK");
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void completedSessionIsRemovedFromInMemoryRegistry() {
        StreamingAsrSessionVO opened = service.open(new StreamingAsrSessionCreateDTO());
        StreamingAsrChunkDTO chunk = new StreamingAsrChunkDTO();
        chunk.setSequence(0L);
        chunk.setAudioBase64(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        service.accept(opened.getSessionId(), chunk);

        StreamingAsrSessionVO completed = service.complete(opened.getSessionId());
        StreamingAsrSessionVO removed = service.get(opened.getSessionId());

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("RECOVERY_REQUIRED", removed.getStatus());
        assertEquals("ASR_STREAM_SESSION_LOST", removed.getErrorCode());
    }

    @Test
    void oversizedBase64IsRejectedBeforeDecode() {
        StreamingAsrSessionVO opened = service.open(new StreamingAsrSessionCreateDTO());
        StreamingAsrChunkDTO chunk = new StreamingAsrChunkDTO();
        chunk.setSequence(0L);
        chunk.setAudioBase64("A".repeat(1_500_000));

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.accept(opened.getSessionId(), chunk));
    }

    @Test
    void blankClientProviderDoesNotSilentlyEnableMockStreamingAsr() {
        service = serviceWithProvider(null);
        StreamingAsrSessionCreateDTO dto = new StreamingAsrSessionCreateDTO();
        dto.setProvider(null);

        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.open(dto));

        assertTrue(error.getMessage().contains("ASR_STREAM_PROVIDER_UNCONFIGURED"));
    }

    @Test
    void deployConfigurationOverridesClientStreamingProviderSelection() {
        StreamingAsrSessionCreateDTO dto = new StreamingAsrSessionCreateDTO();
        dto.setProvider("CLIENT_OVERRIDE");

        StreamingAsrSessionVO opened = service.open(dto);

        assertEquals("MOCK", opened.getProvider());
    }

    @Test
    void missingSessionReturnsStableRecoverableStatusAfterRestart() {
        StreamingAsrSessionVO missing = service.get("sasr-lost-after-restart");

        assertEquals("RECOVERY_REQUIRED", missing.getStatus());
        assertEquals("ASR_STREAM_SESSION_LOST", missing.getErrorCode());
        assertTrue(missing.getErrorMessage().contains("restart"));
    }

    @Test
    void expiredSessionCleanupIsScheduledWithoutAnotherOpenRequest() throws Exception {
        Scheduled scheduled = StreamingAsrSessionServiceImpl.class
                .getDeclaredMethod("cleanupExpiredSessions")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
    }

    @Test
    void scheduledCleanupRemovesExpiredSessionWithoutAnotherOpenRequest() throws Exception {
        StreamingAsrSessionCreateDTO dto = new StreamingAsrSessionCreateDTO();
        dto.setTimeoutMs(1L);
        StreamingAsrSessionVO opened = service.open(dto);
        Thread.sleep(10L);

        service.cleanupExpiredSessions();
        StreamingAsrSessionVO removed = service.get(opened.getSessionId());

        assertEquals("RECOVERY_REQUIRED", removed.getStatus());
        assertEquals("ASR_STREAM_SESSION_LOST", removed.getErrorCode());
    }

    @Test
    void scheduledCleanupRemovesExpiredSessionEvenWhenProviderCancelFails() throws Exception {
        StreamingAsrProvider throwingProvider = throwingCancelProvider();
        service = serviceWithProvider("THROWING", throwingProvider);
        StreamingAsrSessionCreateDTO dto = new StreamingAsrSessionCreateDTO();
        dto.setTimeoutMs(50L);
        StreamingAsrSessionVO opened = service.open(dto);
        Thread.sleep(75L);

        assertDoesNotThrow(service::cleanupExpiredSessions);
        assertEquals("RECOVERY_REQUIRED", service.get(opened.getSessionId()).getStatus());
    }

    @Test
    void missingSessionStillRequiresAuthentication() {
        LoginUserContext.clear();

        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.get("sasr-missing"));

        assertEquals(41000, error.getCode());
    }

    private StreamingAsrSessionServiceImpl serviceWithProvider(String provider) {
        return serviceWithProvider(provider, new MockStreamingAsrProvider());
    }

    private StreamingAsrSessionServiceImpl serviceWithProvider(String provider,
                                                               StreamingAsrProvider asrProvider) {
        InterviewVoiceProviderProperties properties = new InterviewVoiceProviderProperties();
        properties.getStreamingAsr().setProvider(provider);
        return new StreamingAsrSessionServiceImpl(List.of(asrProvider), properties);
    }

    private StreamingAsrProvider throwingCancelProvider() {
        return new StreamingAsrProvider() {
            @Override
            public String providerCode() {
                return "THROWING";
            }

            @Override
            public StreamingAsrProviderSession open(StreamingAsrOpenRequest request,
                                                    com.codecoachai.interview.voice.task.ProviderExecutionContext context) {
                StreamingAsrProviderSession delegate = new MockStreamingAsrProvider().open(request, context);
                return new StreamingAsrProviderSession() {
                    @Override
                    public StreamingAsrSnapshot accept(StreamingAsrAudioChunk chunk) {
                        return delegate.accept(chunk);
                    }

                    @Override
                    public StreamingAsrSnapshot complete() {
                        return delegate.complete();
                    }

                    @Override
                    public StreamingAsrSnapshot cancel() {
                        throw new IllegalStateException("provider cancel failed");
                    }

                    @Override
                    public StreamingAsrSnapshot snapshot() {
                        return delegate.snapshot();
                    }
                };
            }
        };
    }
}
