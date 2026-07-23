package com.codecoachai.interview.tts;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.config.InterviewVoiceProviderProperties;
import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import com.codecoachai.interview.voice.task.ProviderTaskCancelledException;
import com.codecoachai.interview.voice.task.ProviderTaskTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TtsTaskServiceImpl implements TtsTaskService {

    private static final int MAX_TASKS = 1000;
    private static final int MAX_RESULT_AUDIO_BYTES = 2 * 1024 * 1024;
    private static final Duration TERMINAL_TASK_TTL = Duration.ofMinutes(5);

    private final Map<String, TtsProvider> providers;
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final Executor executor;
    private final String configuredProviderCode;

    public TtsTaskServiceImpl(List<TtsProvider> providers,
                              @Qualifier("interviewVoiceCapabilityExecutor") Executor executor,
                              InterviewVoiceProviderProperties properties) {
        this.providers = providers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                provider -> provider.providerCode().toUpperCase(Locale.ROOT),
                provider -> provider));
        this.executor = executor;
        this.configuredProviderCode = normalizeConfiguredProvider(properties.getTts().getProvider());
    }

    @Override
    public TtsTaskVO create(TtsTaskCreateDTO dto) {
        cleanupTerminalTasks();
        if (tasks.size() >= MAX_TASKS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "TTS task capacity is temporarily full");
        }
        if (configuredProviderCode == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "TTS_PROVIDER_UNCONFIGURED: TTS provider is not configured; use text fallback");
        }
        String providerCode = configuredProviderCode;
        Long userId = requireUserId();
        TtsProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "TTS provider is unavailable: " + providerCode);
        }
        String taskId = "tts-" + UUID.randomUUID().toString().replace("-", "");
        Duration timeout = Duration.ofMillis(dto.getTimeoutMs());
        ProviderExecutionContext context = new ProviderExecutionContext(taskId, timeout);
        TaskState state = new TaskState(taskId, userId, providerCode, context);
        tasks.put(taskId, state);
        CompletableFuture<Void> future;
        try {
            future = CompletableFuture.runAsync(
                        () -> execute(provider, dto, state), executor)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null && state.isActive()) {
                        context.cancel();
                        state.fail("TTS_TIMEOUT", "TTS task exceeded its deadline", "TIMED_OUT");
                    }
                });
        } catch (RejectedExecutionException ex) {
            tasks.remove(taskId);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "TTS task capacity is temporarily full");
        }
        state.future = future;
        return state.toVO();
    }

    @Override
    public TtsTaskVO get(String taskId) {
        cleanupTerminalTasks();
        return requireOwnedTask(taskId).toVO();
    }

    @Override
    public TtsTaskVO cancel(String taskId) {
        TaskState state = requireOwnedTask(taskId);
        synchronized (state) {
            if (state.isActive()) {
                state.context.cancel();
                if (state.future != null) {
                    state.future.cancel(true);
                }
                state.status = "CANCELLED";
                state.errorCode = "TTS_CANCELLED";
                state.errorMessage = "TTS task was cancelled";
                state.completedAt = LocalDateTime.now();
            }
        }
        return state.toVO();
    }

    private void execute(TtsProvider provider, TtsTaskCreateDTO dto, TaskState state) {
        synchronized (state) {
            if (!state.isActive()) {
                return;
            }
            state.status = "RUNNING";
        }
        try {
            TtsSynthesisResult result = provider.synthesize(TtsSynthesisRequest.builder()
                    .requestId(state.taskId)
                    .text(dto.getText().trim())
                    .voice(dto.getVoice())
                    .locale(dto.getLocale())
                    .audioFormat(dto.getAudioFormat())
                    .build(), state.context);
            state.context.checkActive();
            if (result.getAudio() != null && result.getAudio().length > MAX_RESULT_AUDIO_BYTES) {
                state.fail("TTS_AUDIO_TOO_LARGE", "TTS audio exceeded the in-memory result limit", "FAILED");
                return;
            }
            synchronized (state) {
                if (!state.isActive()) {
                    return;
                }
                state.result = result;
                state.status = "SUCCEEDED";
                state.completedAt = LocalDateTime.now();
            }
        } catch (ProviderTaskTimeoutException ex) {
            state.fail("TTS_TIMEOUT", ex.getMessage(), "TIMED_OUT");
        } catch (ProviderTaskCancelledException ex) {
            state.fail("TTS_CANCELLED", ex.getMessage(), "CANCELLED");
        } catch (RuntimeException ex) {
            state.fail("TTS_PROVIDER_FAILED", "TTS provider failed", "FAILED");
        }
    }

    private TaskState requireOwnedTask(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "TTS task does not exist");
        }
        if (!state.userId.equals(requireUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "TTS task does not exist");
        }
        return state;
    }

    private Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String normalizeConfiguredProvider(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private void cleanupTerminalTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minus(TERMINAL_TASK_TTL);
        tasks.entrySet().removeIf(entry -> {
            TaskState state = entry.getValue();
            return state.isTerminalOlderThan(cutoff);
        });
    }

    @RequiredArgsConstructor
    private static class TaskState {

        private final String taskId;
        private final Long userId;
        private final String provider;
        private final ProviderExecutionContext context;
        private volatile String status = "QUEUED";
        private volatile TtsSynthesisResult result;
        private volatile String errorCode;
        private volatile String errorMessage;
        private volatile LocalDateTime completedAt;
        private volatile CompletableFuture<Void> future;

        private boolean isActive() {
            return "QUEUED".equals(status) || "RUNNING".equals(status);
        }

        private boolean isTerminalOlderThan(LocalDateTime cutoff) {
            return !isActive() && completedAt != null && completedAt.isBefore(cutoff);
        }

        private synchronized void fail(String code, String message, String failureStatus) {
            if (!isActive()) {
                return;
            }
            errorCode = code;
            errorMessage = message;
            status = failureStatus;
            completedAt = LocalDateTime.now();
        }

        private synchronized TtsTaskVO toVO() {
            TtsTaskVO vo = new TtsTaskVO();
            vo.setTaskId(taskId);
            vo.setProvider(provider);
            vo.setStatus(status);
            vo.setDeadlineAt(LocalDateTime.ofInstant(context.deadline(), ZoneId.systemDefault()));
            vo.setCompletedAt(completedAt);
            vo.setErrorCode(errorCode);
            vo.setErrorMessage(errorMessage);
            if (result != null) {
                vo.setContentType(result.getContentType());
                vo.setAudioBase64(Base64.getEncoder().encodeToString(result.getAudio()));
                vo.setEstimatedDurationMs(result.getEstimatedDurationMs());
            }
            return vo;
        }
    }
}
