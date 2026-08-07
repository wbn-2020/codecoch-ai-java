package com.codecoachai.interview.streamingasr;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.config.InterviewVoiceProviderProperties;
import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import com.codecoachai.interview.voice.task.ProviderTaskTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StreamingAsrSessionServiceImpl implements StreamingAsrSessionService {

    private static final int MAX_CHUNK_BYTES = 1024 * 1024;
    private static final int MAX_CHUNK_BASE64_CHARS = ((MAX_CHUNK_BYTES + 2) / 3) * 4 + 8;
    private static final int MAX_ACTIVE_SESSIONS = 500;

    private final Map<String, StreamingAsrProvider> providers;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final String configuredProviderCode;

    public StreamingAsrSessionServiceImpl(List<StreamingAsrProvider> providers,
                                          InterviewVoiceProviderProperties properties) {
        this.providers = providers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                provider -> provider.providerCode().toUpperCase(Locale.ROOT),
                provider -> provider));
        this.configuredProviderCode = normalizeConfiguredProvider(properties.getStreamingAsr().getProvider());
    }

    @Override
    public StreamingAsrSessionVO open(StreamingAsrSessionCreateDTO dto) {
        if (configuredProviderCode == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "ASR_STREAM_PROVIDER_UNCONFIGURED: Streaming ASR provider is not configured; use text fallback");
        }
        String providerCode = configuredProviderCode;
        Long userId = requireUserId();
        StreamingAsrProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Streaming ASR provider is unavailable: " + providerCode);
        }
        cleanupExpiredSessions();
        if (sessions.size() >= MAX_ACTIVE_SESSIONS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Streaming ASR capacity is temporarily full");
        }
        String sessionId = "sasr-" + UUID.randomUUID().toString().replace("-", "");
        ProviderExecutionContext context = new ProviderExecutionContext(
                sessionId, Duration.ofMillis(dto.getTimeoutMs()));
        StreamingAsrProviderSession providerSession = provider.open(StreamingAsrOpenRequest.builder()
                .sessionId(sessionId)
                .language(dto.getLanguage())
                .sampleRateHz(dto.getSampleRateHz())
                .channels(dto.getChannels())
                .encoding(dto.getEncoding())
                .mockTranscript(dto.getMockTranscript())
                .mockTimestampsAvailable(Boolean.TRUE.equals(dto.getMockTimestampsAvailable()))
                .build(), context);
        SessionState state = new SessionState(sessionId, userId, providerCode, context, providerSession);
        sessions.put(sessionId, state);
        return toVO(state, providerSession.snapshot());
    }

    @Override
    public StreamingAsrSessionVO accept(String sessionId, StreamingAsrChunkDTO dto) {
        SessionState state = ownedSession(sessionId);
        if (state == null) {
            return missingSession(sessionId);
        }
        String audioBase64 = dto.getAudioBase64();
        if (audioBase64 == null || audioBase64.length() > MAX_CHUNK_BASE64_CHARS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio chunk size is invalid");
        }
        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(audioBase64);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "audioBase64 is invalid");
        }
        if (audio.length == 0 || audio.length > MAX_CHUNK_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio chunk size is invalid");
        }
        try {
            StreamingAsrSnapshot snapshot = state.providerSession.accept(StreamingAsrAudioChunk.builder()
                    .sequence(dto.getSequence())
                    .audio(audio)
                    .endOfStream(Boolean.TRUE.equals(dto.getEndOfStream()))
                    .build());
            return toVO(state, snapshot);
        } catch (ProviderTaskTimeoutException ex) {
            state.providerSession.cancel();
            return removeAndReturn(state, timedOut(state));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ex.getMessage());
        }
    }

    @Override
    public StreamingAsrSessionVO complete(String sessionId) {
        SessionState state = ownedSession(sessionId);
        if (state == null) {
            return missingSession(sessionId);
        }
        try {
            return removeAndReturn(state, toVO(state, state.providerSession.complete()));
        } catch (ProviderTaskTimeoutException ex) {
            state.providerSession.cancel();
            return removeAndReturn(state, timedOut(state));
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, ex.getMessage());
        }
    }

    @Override
    public StreamingAsrSessionVO get(String sessionId) {
        SessionState state = ownedSession(sessionId);
        if (state == null) {
            return missingSession(sessionId);
        }
        if (state.context.isTimedOut()) {
            state.providerSession.cancel();
            return removeAndReturn(state, timedOut(state));
        }
        return toVO(state, state.providerSession.snapshot());
    }

    @Override
    public StreamingAsrSessionVO cancel(String sessionId) {
        SessionState state = ownedSession(sessionId);
        if (state == null) {
            return missingSession(sessionId);
        }
        return removeAndReturn(state, toVO(state, state.providerSession.cancel()));
    }

    private SessionState ownedSession(String sessionId) {
        Long userId = requireUserId();
        SessionState state = sessions.get(sessionId);
        return state != null && state.userId.equals(userId) ? state : null;
    }

    private Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private StreamingAsrSessionVO timedOut(SessionState state) {
        StreamingAsrSessionVO vo = toVO(state, state.providerSession.snapshot());
        vo.setStatus("TIMED_OUT");
        vo.setErrorCode("ASR_STREAM_TIMEOUT");
        vo.setErrorMessage("Streaming ASR session exceeded its deadline");
        return vo;
    }

    private StreamingAsrSessionVO removeAndReturn(SessionState state, StreamingAsrSessionVO vo) {
        sessions.remove(state.sessionId, state);
        return vo;
    }

    @Scheduled(fixedDelayString = "${codecoachai.interview.voice.streaming-asr.cleanup-fixed-delay-ms:30000}")
    void cleanupExpiredSessions() {
        sessions.forEach((sessionId, state) -> {
            if (!state.context.isTimedOut()) {
                return;
            }
            try {
                state.providerSession.cancel();
            } catch (RuntimeException ex) {
                log.warn("Streaming ASR provider cancel failed during expired session cleanup sessionId={} provider={}",
                        sessionId, state.providerCode);
            } finally {
                sessions.remove(sessionId, state);
            }
        });
    }

    private StreamingAsrSessionVO missingSession(String sessionId) {
        StreamingAsrSessionVO vo = new StreamingAsrSessionVO();
        vo.setSessionId(sessionId);
        vo.setProvider(configuredProviderCode);
        vo.setStatus("RECOVERY_REQUIRED");
        vo.setErrorCode("ASR_STREAM_SESSION_LOST");
        vo.setErrorMessage("Streaming ASR session was lost after restart or expiration; reopen and use text fallback");
        return vo;
    }

    private StreamingAsrSessionVO toVO(SessionState state, StreamingAsrSnapshot snapshot) {
        StreamingAsrSessionVO vo = new StreamingAsrSessionVO();
        vo.setSessionId(state.sessionId);
        vo.setProvider(state.providerCode);
        vo.setStatus(snapshot.getStatus());
        vo.setPartialTranscript(snapshot.getPartialTranscript());
        vo.setFinalTranscript(snapshot.getFinalTranscript());
        vo.setTimestampMode(snapshot.getTimestampMode());
        vo.setWords(snapshot.getWords());
        vo.setAcceptedChunks(snapshot.getAcceptedChunks());
        vo.setAcceptedBytes(snapshot.getAcceptedBytes());
        vo.setErrorCode(snapshot.getErrorCode());
        vo.setErrorMessage(snapshot.getErrorMessage());
        vo.setDeadlineAt(LocalDateTime.ofInstant(state.context.deadline(), ZoneId.systemDefault()));
        return vo;
    }

    private String normalizeConfiguredProvider(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private record SessionState(
            String sessionId,
            Long userId,
            String providerCode,
            ProviderExecutionContext context,
            StreamingAsrProviderSession providerSession) {
    }
}
