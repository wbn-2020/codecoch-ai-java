package com.codecoachai.interview.audioretention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.feign.FileFeignClient;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioRetentionServiceImpl implements AudioRetentionService {

    private static final String BIZ_TYPE_INTERVIEW_VOICE = "INTERVIEW_VOICE";
    private static final Duration MAX_RETENTION = Duration.ofDays(90);

    private final InterviewAudioRetentionRecordMapper retentionMapper;
    private final InterviewAudioCleanupRecordMapper cleanupMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    private final FileFeignClient fileFeignClient;
    private final Executor executor;
    private final Map<String, RunningCleanup> running = new ConcurrentHashMap<>();

    public AudioRetentionServiceImpl(InterviewAudioRetentionRecordMapper retentionMapper,
                                     InterviewAudioCleanupRecordMapper cleanupMapper,
                                     InterviewSessionMapper sessionMapper,
                                     InterviewVoiceSubmissionMapper voiceSubmissionMapper,
                                     FileFeignClient fileFeignClient,
                                     @Qualifier("interviewVoiceCapabilityExecutor") Executor executor) {
        this.retentionMapper = retentionMapper;
        this.cleanupMapper = cleanupMapper;
        this.sessionMapper = sessionMapper;
        this.voiceSubmissionMapper = voiceSubmissionMapper;
        this.fileFeignClient = fileFeignClient;
        this.executor = executor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioRetentionVO register(Long sessionId, AudioRetentionRegisterDTO dto) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        InterviewVoiceSubmission submission = requireOwnedSubmission(
                dto.getVoiceSubmissionId(), sessionId, userId);
        if (!submission.getFileId().equals(dto.getFileId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "fileId does not match voice submission");
        }
        LocalDateTime now = LocalDateTime.now();
        if (dto.getRetainUntil().isAfter(now.plus(MAX_RETENTION))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio retention cannot exceed 90 days");
        }
        InterviewAudioRetentionRecord existing = retentionMapper.selectOne(
                new LambdaQueryWrapper<InterviewAudioRetentionRecord>()
                        .eq(InterviewAudioRetentionRecord::getVoiceSubmissionId, submission.getId())
                        .eq(InterviewAudioRetentionRecord::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (existing != null) {
            return toRetentionVO(existing);
        }
        InterviewAudioRetentionRecord record = new InterviewAudioRetentionRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setVoiceSubmissionId(submission.getId());
        record.setFileId(submission.getFileId());
        record.setPolicyCode(dto.getPolicyCode().trim().toUpperCase(Locale.ROOT));
        record.setRetainUntil(dto.getRetainUntil());
        record.setLegalHold(Boolean.FALSE);
        record.setRetentionStatus("RETAINED");
        retentionMapper.insert(record);
        return toRetentionVO(record);
    }

    @Override
    public AudioRetentionVO get(Long sessionId, Long retentionRecordId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        return toRetentionVO(requireOwnedRetention(sessionId, retentionRecordId, userId));
    }

    @Override
    public AudioCleanupTaskVO requestCleanup(Long sessionId, Long retentionRecordId, AudioCleanupRequestDTO dto) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        InterviewAudioRetentionRecord retention = requireOwnedRetention(sessionId, retentionRecordId, userId);
        if (Boolean.TRUE.equals(retention.getLegalHold())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio is under legal hold");
        }
        if (LocalDateTime.now().isBefore(retention.getRetainUntil())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio retention period has not expired");
        }
        InterviewAudioCleanupRecord latest = cleanupMapper.selectOne(
                new LambdaQueryWrapper<InterviewAudioCleanupRecord>()
                        .eq(InterviewAudioCleanupRecord::getRetentionRecordId, retention.getId())
                        .orderByDesc(InterviewAudioCleanupRecord::getAttemptNo)
                        .last("limit 1"));
        if ("DELETED".equals(retention.getRetentionStatus())) {
            if (latest != null && "SUCCEEDED".equals(latest.getCleanupStatus())) {
                return toCleanupVO(latest);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "Audio retention is deleted without a successful cleanup record");
        }
        int attemptNo = latest == null ? 1 : latest.getAttemptNo() + 1;
        String taskId = "audio-cleanup-" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime deadline = LocalDateTime.now().plus(Duration.ofMillis(dto.getTimeoutMs()));
        InterviewAudioCleanupRecord cleanup = new InterviewAudioCleanupRecord();
        cleanup.setRetentionRecordId(retention.getId());
        cleanup.setCleanupTaskId(taskId);
        cleanup.setAttemptNo(attemptNo);
        cleanup.setCleanupStatus("QUEUED");
        cleanup.setProviderCode("FILE_SERVICE");
        cleanup.setDeadlineAt(deadline);
        cleanupMapper.insert(cleanup);

        retention.setRetentionStatus("CLEANUP_PENDING");
        retention.setCleanupRequestedAt(LocalDateTime.now());
        retention.setLastErrorCode(null);
        retention.setLastErrorMessage(null);
        retentionMapper.updateById(retention);

        ProviderExecutionContext context = new ProviderExecutionContext(taskId, Duration.ofMillis(dto.getTimeoutMs()));
        RunningCleanup state = new RunningCleanup(retention, cleanup, context);
        running.put(taskId, state);
        CompletableFuture<Void> future;
        try {
            future = CompletableFuture.runAsync(() -> executeCleanup(state), executor)
                    .orTimeout(dto.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            timeoutCleanup(state);
                        }
                        running.remove(taskId, state);
                    });
        } catch (RejectedExecutionException ex) {
            running.remove(taskId, state);
            failCleanup(state, "FAILED", "AUDIO_CLEANUP_CAPACITY_FULL",
                    "Audio cleanup capacity is temporarily full");
            return toCleanupVO(cleanup);
        }
        state.future = future;
        return toCleanupVO(cleanup);
    }

    @Override
    public AudioCleanupTaskVO getCleanupTask(Long sessionId, String cleanupTaskId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        InterviewAudioCleanupRecord cleanup = requireCleanup(cleanupTaskId);
        requireOwnedRetention(sessionId, cleanup.getRetentionRecordId(), userId);
        return toCleanupVO(cleanup);
    }

    @Override
    public AudioCleanupTaskVO cancelCleanup(Long sessionId, String cleanupTaskId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        InterviewAudioCleanupRecord cleanup = requireCleanup(cleanupTaskId);
        InterviewAudioRetentionRecord retention = requireOwnedRetention(
                sessionId, cleanup.getRetentionRecordId(), userId);
        if (isActive(cleanup.getCleanupStatus())) {
            RunningCleanup state = running.get(cleanupTaskId);
            if (state != null) {
                state.context.cancel();
                if (state.future != null) {
                    state.future.cancel(true);
                }
            }
            cleanup.setCleanupStatus("CANCELLED");
            cleanup.setCompletedAt(LocalDateTime.now());
            cleanup.setErrorCode("AUDIO_CLEANUP_CANCELLED");
            cleanup.setErrorMessage("Audio cleanup task was cancelled");
            cleanupMapper.updateById(cleanup);
            retention.setRetentionStatus("CANCELLED");
            retention.setLastErrorCode(cleanup.getErrorCode());
            retention.setLastErrorMessage(cleanup.getErrorMessage());
            retentionMapper.updateById(retention);
        }
        return toCleanupVO(cleanup);
    }

    private void executeCleanup(RunningCleanup state) {
        synchronized (state) {
            if (!isActive(state.cleanup.getCleanupStatus())) {
                return;
            }
            state.cleanup.setCleanupStatus("RUNNING");
            state.cleanup.setStartedAt(LocalDateTime.now());
            cleanupMapper.updateById(state.cleanup);
        }
        try {
            state.context.checkActive();
            deleteRemoteFileOrConfirmAbsent(state.retention);
            state.context.checkActive();
            synchronized (state) {
                if (!isActive(state.cleanup.getCleanupStatus())) {
                    return;
                }
                LocalDateTime now = LocalDateTime.now();
                state.cleanup.setCleanupStatus("SUCCEEDED");
                state.cleanup.setCompletedAt(now);
                cleanupMapper.updateById(state.cleanup);
                state.retention.setRetentionStatus("DELETED");
                state.retention.setCleanupCompletedAt(now);
                state.retention.setLastErrorCode(null);
                state.retention.setLastErrorMessage(null);
                retentionMapper.updateById(state.retention);
                voiceSubmissionMapper.update(null, new LambdaUpdateWrapper<InterviewVoiceSubmission>()
                        .eq(InterviewVoiceSubmission::getId, state.retention.getVoiceSubmissionId())
                        .eq(InterviewVoiceSubmission::getUserId, state.retention.getUserId())
                        .set(InterviewVoiceSubmission::getFileDeleteStatus, "DELETED")
                        .set(InterviewVoiceSubmission::getFileDeletedAt, now)
                        .set(InterviewVoiceSubmission::getFileDeleteError, null));
            }
        } catch (RuntimeException ex) {
            failCleanup(state, state.context.isCancelled() ? "CANCELLED" : "FAILED",
                    state.context.isCancelled() ? "AUDIO_CLEANUP_CANCELLED" : "AUDIO_CLEANUP_FAILED",
                    state.context.isCancelled()
                            ? "Audio cleanup task was cancelled" : "Audio cleanup provider failed");
        }
    }

    private void deleteRemoteFileOrConfirmAbsent(InterviewAudioRetentionRecord retention) {
        Result<Void> deletion = fileFeignClient.delete(
                retention.getFileId(), retention.getUserId(), BIZ_TYPE_INTERVIEW_VOICE);
        if (deletion != null && deletion.isSuccess()) {
            return;
        }
        if (isRemoteResourceUnavailable(deletion)) {
            Result<?> detail = fileFeignClient.detail(
                    retention.getFileId(), retention.getUserId(), BIZ_TYPE_INTERVIEW_VOICE);
            if (isRemoteResourceUnavailable(detail)) {
                return;
            }
        }
        FeignResultUtils.unwrap(deletion);
    }

    private boolean isRemoteResourceUnavailable(Result<?> result) {
        return result != null
                && !result.isSuccess()
                && Integer.valueOf(ErrorCode.PARAM_ERROR.getCode()).equals(result.getCode());
    }

    private void timeoutCleanup(RunningCleanup state) {
        synchronized (state) {
            if (!isActive(state.cleanup.getCleanupStatus())) {
                return;
            }
            state.context.cancel();
            failCleanup(state, "TIMED_OUT", "AUDIO_CLEANUP_TIMEOUT",
                    "Audio cleanup task exceeded its deadline");
        }
    }

    private void failCleanup(RunningCleanup state, String status, String code, String message) {
        synchronized (state) {
            if (!isActive(state.cleanup.getCleanupStatus())) {
                return;
            }
            state.cleanup.setCleanupStatus(status);
            state.cleanup.setCompletedAt(LocalDateTime.now());
            state.cleanup.setErrorCode(code);
            state.cleanup.setErrorMessage(message);
            cleanupMapper.updateById(state.cleanup);
            state.retention.setRetentionStatus("CANCELLED".equals(status) ? "CANCELLED" : "CLEANUP_FAILED");
            state.retention.setLastErrorCode(code);
            state.retention.setLastErrorMessage(message);
            retentionMapper.updateById(state.retention);
        }
    }

    private InterviewAudioCleanupRecord requireCleanup(String taskId) {
        InterviewAudioCleanupRecord cleanup = cleanupMapper.selectOne(
                new LambdaQueryWrapper<InterviewAudioCleanupRecord>()
                        .eq(InterviewAudioCleanupRecord::getCleanupTaskId, taskId)
                        .eq(InterviewAudioCleanupRecord::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (cleanup == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio cleanup task does not exist");
        }
        return cleanup;
    }

    private InterviewAudioRetentionRecord requireOwnedRetention(Long sessionId, Long id, Long userId) {
        InterviewAudioRetentionRecord record = retentionMapper.selectOne(
                new LambdaQueryWrapper<InterviewAudioRetentionRecord>()
                        .eq(InterviewAudioRetentionRecord::getId, id)
                        .eq(InterviewAudioRetentionRecord::getSessionId, sessionId)
                        .eq(InterviewAudioRetentionRecord::getUserId, userId)
                        .eq(InterviewAudioRetentionRecord::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Audio retention record does not exist");
        }
        return record;
    }

    private InterviewVoiceSubmission requireOwnedSubmission(Long id, Long sessionId, Long userId) {
        InterviewVoiceSubmission submission = voiceSubmissionMapper.selectOne(
                new LambdaQueryWrapper<InterviewVoiceSubmission>()
                        .eq(InterviewVoiceSubmission::getId, id)
                        .eq(InterviewVoiceSubmission::getSessionId, sessionId)
                        .eq(InterviewVoiceSubmission::getUserId, userId)
                        .eq(InterviewVoiceSubmission::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (submission == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission does not exist");
        }
        return submission;
    }

    private void requireOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = sessionMapper.selectOne(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Interview session does not exist");
        }
    }

    private Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private boolean isActive(String status) {
        return "QUEUED".equals(status) || "RUNNING".equals(status);
    }

    private AudioRetentionVO toRetentionVO(InterviewAudioRetentionRecord record) {
        AudioRetentionVO vo = new AudioRetentionVO();
        vo.setRetentionRecordId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setVoiceSubmissionId(record.getVoiceSubmissionId());
        vo.setFileId(record.getFileId());
        vo.setPolicyCode(record.getPolicyCode());
        vo.setRetainUntil(record.getRetainUntil());
        vo.setLegalHold(record.getLegalHold());
        vo.setRetentionStatus(record.getRetentionStatus());
        vo.setCleanupRequestedAt(record.getCleanupRequestedAt());
        vo.setCleanupCompletedAt(record.getCleanupCompletedAt());
        vo.setLastErrorCode(record.getLastErrorCode());
        vo.setLastErrorMessage(record.getLastErrorMessage());
        return vo;
    }

    private AudioCleanupTaskVO toCleanupVO(InterviewAudioCleanupRecord record) {
        AudioCleanupTaskVO vo = new AudioCleanupTaskVO();
        vo.setCleanupTaskId(record.getCleanupTaskId());
        vo.setRetentionRecordId(record.getRetentionRecordId());
        vo.setAttemptNo(record.getAttemptNo());
        vo.setCleanupStatus(record.getCleanupStatus());
        vo.setDeadlineAt(record.getDeadlineAt());
        vo.setStartedAt(record.getStartedAt());
        vo.setCompletedAt(record.getCompletedAt());
        vo.setErrorCode(record.getErrorCode());
        vo.setErrorMessage(record.getErrorMessage());
        return vo;
    }

    private static final class RunningCleanup {

        private final InterviewAudioRetentionRecord retention;
        private final InterviewAudioCleanupRecord cleanup;
        private final ProviderExecutionContext context;
        private volatile CompletableFuture<Void> future;

        private RunningCleanup(InterviewAudioRetentionRecord retention,
                               InterviewAudioCleanupRecord cleanup,
                               ProviderExecutionContext context) {
            this.retention = retention;
            this.cleanup = cleanup;
            this.context = context;
        }
    }
}
