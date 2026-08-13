package com.codecoachai.interview.voicedelivery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewTranscript;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.domain.enums.InterviewTranscriptStatusEnum;
import com.codecoachai.interview.domain.enums.InterviewVoiceStatusEnum;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewTranscriptMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import com.codecoachai.interview.voice.task.ProviderExecutionContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VoiceDeliveryServiceImpl implements VoiceDeliveryService {

    private final VoiceDeviceCheckMapper deviceCheckMapper;
    private final VoiceDeliveryAnalysisMapper analysisMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    private final InterviewTranscriptMapper transcriptMapper;
    private final VoiceDeliveryAnalyzer analyzer;
    private final Executor executor;
    private final Map<Long, RunningAnalysis> running = new ConcurrentHashMap<>();

    public VoiceDeliveryServiceImpl(VoiceDeviceCheckMapper deviceCheckMapper,
                                    VoiceDeliveryAnalysisMapper analysisMapper,
                                    InterviewSessionMapper sessionMapper,
                                    InterviewVoiceSubmissionMapper voiceSubmissionMapper,
                                    InterviewTranscriptMapper transcriptMapper,
                                    VoiceDeliveryAnalyzer analyzer,
                                    @Qualifier("interviewVoiceCapabilityExecutor") Executor executor) {
        this.deviceCheckMapper = deviceCheckMapper;
        this.analysisMapper = analysisMapper;
        this.sessionMapper = sessionMapper;
        this.voiceSubmissionMapper = voiceSubmissionMapper;
        this.transcriptMapper = transcriptMapper;
        this.analyzer = analyzer;
        this.executor = executor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceDeviceCheckVO createDeviceCheck(Long sessionId, VoiceDeviceCheckCreateDTO dto) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        List<String> warnings = deviceWarnings(dto);
        VoiceDeviceCheck check = new VoiceDeviceCheck();
        check.setUserId(userId);
        check.setSessionId(sessionId);
        check.setPermissionState(dto.getPermissionState().trim().toUpperCase(Locale.ROOT));
        check.setSampleRateHz(dto.getSampleRateHz());
        check.setChannels(dto.getChannels());
        check.setInputDetected(dto.getInputDetected());
        check.setEchoCancellation(dto.getEchoCancellation());
        check.setNoiseSuppression(dto.getNoiseSuppression());
        check.setAutoGainControl(dto.getAutoGainControl());
        check.setAverageRmsDbfs(dto.getAverageRmsDbfs());
        check.setClippingRatio(dto.getClippingRatio());
        check.setWarningCodes(joinWarnings(warnings));
        check.setCheckStatus(checkStatus(dto, warnings));
        deviceCheckMapper.insert(check);
        return toDeviceVO(check);
    }

    @Override
    public VoiceDeliveryAnalysisVO createAnalysis(Long sessionId, VoiceDeliveryAnalysisCreateDTO dto) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        requireOwnedDeviceCheck(dto.getDeviceCheckId(), sessionId, userId);
        InterviewVoiceSubmission submission =
                requireOwnedSubmission(dto.getVoiceSubmissionId(), sessionId, userId);
        InterviewTranscript transcript =
                requireConfirmedTranscript(submission.getId(), sessionId, userId);
        VoiceDeliveryAnalysisCreateDTO evidenceRequest = evidenceRequest(dto, submission, transcript);
        LocalDateTime deadline = LocalDateTime.now().plus(Duration.ofMillis(evidenceRequest.getTimeoutMs()));

        VoiceDeliveryAnalysis analysis = new VoiceDeliveryAnalysis();
        analysis.setUserId(userId);
        analysis.setSessionId(sessionId);
        analysis.setVoiceSubmissionId(submission.getId());
        analysis.setDeviceCheckId(evidenceRequest.getDeviceCheckId());
        analysis.setTaskStatus("QUEUED");
        analysis.setTimestampSource("NONE");
        analysis.setTimestampsAvailable(Boolean.FALSE);
        analysis.setPauseMetricsAvailable(Boolean.FALSE);
        analysis.setAudioDurationMs(submission.getAudioDurationMs());
        analysis.setDeadlineAt(deadline);
        analysisMapper.insert(analysis);

        ProviderExecutionContext context = new ProviderExecutionContext(
                "voice-delivery-" + analysis.getId(), Duration.ofMillis(evidenceRequest.getTimeoutMs()));
        RunningAnalysis state = new RunningAnalysis(analysis, context);
        running.put(analysis.getId(), state);
        CompletableFuture<Void> future;
        try {
            future = CompletableFuture.runAsync(() -> execute(evidenceRequest, state), executor)
                    .orTimeout(evidenceRequest.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            timeout(state);
                        }
                        running.remove(analysis.getId(), state);
                    });
        } catch (RejectedExecutionException ex) {
            running.remove(analysis.getId(), state);
            analysis.setTaskStatus("FAILED");
            analysis.setErrorCode("VOICE_DELIVERY_CAPACITY_FULL");
            analysis.setErrorMessage("Voice delivery capacity is temporarily full");
            analysis.setCompletedAt(LocalDateTime.now());
            analysisMapper.updateById(analysis);
            return toAnalysisVO(analysis);
        }
        state.future = future;
        return toAnalysisVO(analysis);
    }

    @Override
    public VoiceDeliveryAnalysisVO getAnalysis(Long sessionId, Long analysisId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        return toAnalysisVO(requireOwnedAnalysis(sessionId, analysisId, userId));
    }

    @Override
    public VoiceDeliveryAnalysisVO cancelAnalysis(Long sessionId, Long analysisId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        VoiceDeliveryAnalysis analysis = requireOwnedAnalysis(sessionId, analysisId, userId);
        RunningAnalysis state = running.get(analysisId);
        if (isActive(analysis.getTaskStatus())) {
            if (state != null) {
                state.context.cancel();
                if (state.future != null) {
                    state.future.cancel(true);
                }
            }
            analysis.setTaskStatus("CANCELLED");
            analysis.setCancelledAt(LocalDateTime.now());
            analysis.setCompletedAt(LocalDateTime.now());
            analysis.setErrorCode("VOICE_DELIVERY_CANCELLED");
            analysis.setErrorMessage("Voice delivery analysis was cancelled");
            analysisMapper.updateById(analysis);
        }
        return toAnalysisVO(analysis);
    }

    private void execute(VoiceDeliveryAnalysisCreateDTO dto, RunningAnalysis state) {
        VoiceDeliveryAnalysis analysis = state.analysis;
        synchronized (state) {
            if (!isActive(analysis.getTaskStatus())) {
                return;
            }
            analysis.setTaskStatus("RUNNING");
            analysisMapper.updateById(analysis);
        }
        try {
            VoiceDeliveryMetrics metrics = analyzer.analyze(dto, state.context);
            state.context.checkActive();
            synchronized (state) {
                if (!isActive(analysis.getTaskStatus())) {
                    return;
                }
                analysis.setWordCount(metrics.getWordCount());
                analysis.setSpeakingRatePerMinute(metrics.getSpeakingRatePerMinute());
                analysis.setFillerCount(metrics.getFillerCount());
                analysis.setTimestampsAvailable(Boolean.FALSE);
                analysis.setPauseMetricsAvailable(Boolean.FALSE);
                analysis.setPauseCount(null);
                analysis.setAveragePauseMs(null);
                analysis.setLongestPauseMs(null);
                analysis.setWarningCodes(joinWarnings(metrics.getWarningCodes()));
                analysis.setTaskStatus("SUCCEEDED");
                analysis.setCompletedAt(LocalDateTime.now());
                analysisMapper.updateById(analysis);
            }
        } catch (RuntimeException ex) {
            synchronized (state) {
                if (!isActive(analysis.getTaskStatus())) {
                    return;
                }
                analysis.setTaskStatus(state.context.isCancelled() ? "CANCELLED" : "FAILED");
                analysis.setErrorCode(state.context.isCancelled()
                        ? "VOICE_DELIVERY_CANCELLED" : "VOICE_DELIVERY_ANALYSIS_FAILED");
                analysis.setErrorMessage(state.context.isCancelled()
                        ? "Voice delivery analysis was cancelled" : "Voice delivery analysis failed");
                analysis.setCompletedAt(LocalDateTime.now());
                analysisMapper.updateById(analysis);
            }
        }
    }

    private void timeout(RunningAnalysis state) {
        synchronized (state) {
            VoiceDeliveryAnalysis analysis = state.analysis;
            if (!isActive(analysis.getTaskStatus())) {
                return;
            }
            state.context.cancel();
            analysis.setTaskStatus("TIMED_OUT");
            analysis.setErrorCode("VOICE_DELIVERY_TIMEOUT");
            analysis.setErrorMessage("Voice delivery analysis exceeded its deadline");
            analysis.setCompletedAt(LocalDateTime.now());
            analysisMapper.updateById(analysis);
        }
    }

    private List<String> deviceWarnings(VoiceDeviceCheckCreateDTO dto) {
        List<String> warnings = new ArrayList<>();
        if (!"GRANTED".equalsIgnoreCase(dto.getPermissionState())) {
            warnings.add("MICROPHONE_PERMISSION_NOT_GRANTED");
        }
        if (!Boolean.TRUE.equals(dto.getInputDetected())) {
            warnings.add("MICROPHONE_INPUT_NOT_DETECTED");
        }
        if (dto.getSampleRateHz() != null && dto.getSampleRateHz() < 16000) {
            warnings.add("SAMPLE_RATE_LOW");
        }
        if (dto.getAverageRmsDbfs() != null
                && dto.getAverageRmsDbfs().compareTo(new BigDecimal("-60")) < 0) {
            warnings.add("INPUT_LEVEL_LOW");
        }
        if (dto.getClippingRatio() != null
                && dto.getClippingRatio().compareTo(new BigDecimal("0.05")) > 0) {
            warnings.add("INPUT_CLIPPING_DETECTED");
        }
        return List.copyOf(warnings);
    }

    private String checkStatus(VoiceDeviceCheckCreateDTO dto, List<String> warnings) {
        if (!"GRANTED".equalsIgnoreCase(dto.getPermissionState())
                || !Boolean.TRUE.equals(dto.getInputDetected())) {
            return "FAIL";
        }
        return warnings.isEmpty() ? "PASS" : "WARN";
    }

    private VoiceDeliveryAnalysis requireOwnedAnalysis(Long sessionId, Long id, Long userId) {
        VoiceDeliveryAnalysis analysis = analysisMapper.selectOne(new LambdaQueryWrapper<VoiceDeliveryAnalysis>()
                .eq(VoiceDeliveryAnalysis::getId, id)
                .eq(VoiceDeliveryAnalysis::getSessionId, sessionId)
                .eq(VoiceDeliveryAnalysis::getUserId, userId)
                .eq(VoiceDeliveryAnalysis::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (analysis == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice delivery analysis does not exist");
        }
        return analysis;
    }

    private void requireOwnedDeviceCheck(Long id, Long sessionId, Long userId) {
        if (id == null) {
            return;
        }
        VoiceDeviceCheck check = deviceCheckMapper.selectOne(new LambdaQueryWrapper<VoiceDeviceCheck>()
                .eq(VoiceDeviceCheck::getId, id)
                .eq(VoiceDeviceCheck::getSessionId, sessionId)
                .eq(VoiceDeviceCheck::getUserId, userId)
                .eq(VoiceDeviceCheck::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (check == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice device check does not exist");
        }
    }

    private InterviewVoiceSubmission requireOwnedSubmission(Long id, Long sessionId, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission id is required");
        }
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
        if (submission.getFileId() == null
                || submission.getAudioDurationMs() == null
                || submission.getAudioDurationMs() <= 0
                || !InterviewVoiceStatusEnum.CONFIRMED.name().equals(submission.getVoiceStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Voice submission lacks confirmed audio evidence");
        }
        return submission;
    }

    private InterviewTranscript requireConfirmedTranscript(Long submissionId, Long sessionId, Long userId) {
        List<InterviewTranscript> transcripts = transcriptMapper.selectList(
                new LambdaQueryWrapper<InterviewTranscript>()
                .eq(InterviewTranscript::getVoiceSubmissionId, submissionId)
                .eq(InterviewTranscript::getSessionId, sessionId)
                .eq(InterviewTranscript::getUserId, userId)
                .eq(InterviewTranscript::getTranscriptStatus, InterviewTranscriptStatusEnum.CONFIRMED.name())
                .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                .orderByAsc(InterviewTranscript::getId));
        if (transcripts == null || transcripts.size() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Exactly one confirmed transcript evidence is required");
        }
        InterviewTranscript transcript = transcripts.get(0);
        if (transcript == null
                || !Objects.equals(userId, transcript.getUserId())
                || !Objects.equals(sessionId, transcript.getSessionId())
                || !Objects.equals(submissionId, transcript.getVoiceSubmissionId())
                || !InterviewTranscriptStatusEnum.CONFIRMED.name().equals(transcript.getTranscriptStatus())
                || !StringUtils.hasText(transcript.getConfirmedText())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Confirmed transcript evidence does not exist");
        }
        return transcript;
    }

    private VoiceDeliveryAnalysisCreateDTO evidenceRequest(
            VoiceDeliveryAnalysisCreateDTO request,
            InterviewVoiceSubmission submission,
            InterviewTranscript transcript) {
        VoiceDeliveryAnalysisCreateDTO evidenceRequest = new VoiceDeliveryAnalysisCreateDTO();
        evidenceRequest.setVoiceSubmissionId(submission.getId());
        evidenceRequest.setDeviceCheckId(request.getDeviceCheckId());
        evidenceRequest.setTranscript(transcript.getConfirmedText().trim());
        evidenceRequest.setAudioDurationMs(submission.getAudioDurationMs());
        evidenceRequest.setTimestampSource("NONE");
        evidenceRequest.setWordTimings(List.of());
        evidenceRequest.setTimeoutMs(request.getTimeoutMs());
        return evidenceRequest;
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

    private String joinWarnings(List<String> warnings) {
        return warnings == null || warnings.isEmpty() ? null : String.join(",", warnings);
    }

    private List<String> splitWarnings(String warnings) {
        return warnings == null || warnings.isBlank() ? List.of() : Arrays.asList(warnings.split(","));
    }

    private VoiceDeviceCheckVO toDeviceVO(VoiceDeviceCheck check) {
        VoiceDeviceCheckVO vo = new VoiceDeviceCheckVO();
        vo.setDeviceCheckId(check.getId());
        vo.setSessionId(check.getSessionId());
        vo.setPermissionState(check.getPermissionState());
        vo.setSampleRateHz(check.getSampleRateHz());
        vo.setChannels(check.getChannels());
        vo.setInputDetected(check.getInputDetected());
        vo.setEchoCancellation(check.getEchoCancellation());
        vo.setNoiseSuppression(check.getNoiseSuppression());
        vo.setAutoGainControl(check.getAutoGainControl());
        vo.setAverageRmsDbfs(check.getAverageRmsDbfs());
        vo.setClippingRatio(check.getClippingRatio());
        vo.setCheckStatus(check.getCheckStatus());
        vo.setWarningCodes(splitWarnings(check.getWarningCodes()));
        vo.setCreatedAt(check.getCreatedAt());
        return vo;
    }

    private VoiceDeliveryAnalysisVO toAnalysisVO(VoiceDeliveryAnalysis analysis) {
        VoiceDeliveryAnalysisVO vo = new VoiceDeliveryAnalysisVO();
        vo.setAnalysisId(analysis.getId());
        vo.setSessionId(analysis.getSessionId());
        vo.setVoiceSubmissionId(analysis.getVoiceSubmissionId());
        vo.setDeviceCheckId(analysis.getDeviceCheckId());
        vo.setTaskStatus(analysis.getTaskStatus());
        vo.setTimestampSource(analysis.getTimestampSource());
        vo.setTimestampsAvailable(analysis.getTimestampsAvailable());
        vo.setAudioDurationMs(analysis.getAudioDurationMs());
        vo.setWordCount(analysis.getWordCount());
        vo.setSpeakingRatePerMinute(analysis.getSpeakingRatePerMinute());
        vo.setFillerCount(analysis.getFillerCount());
        vo.setPauseMetricsAvailable(analysis.getPauseMetricsAvailable());
        vo.setPauseCount(analysis.getPauseCount());
        vo.setAveragePauseMs(analysis.getAveragePauseMs());
        vo.setLongestPauseMs(analysis.getLongestPauseMs());
        vo.setWarningCodes(splitWarnings(analysis.getWarningCodes()));
        vo.setDeadlineAt(analysis.getDeadlineAt());
        vo.setCompletedAt(analysis.getCompletedAt());
        vo.setCancelledAt(analysis.getCancelledAt());
        vo.setErrorCode(analysis.getErrorCode());
        vo.setErrorMessage(analysis.getErrorMessage());
        return vo;
    }

    private static final class RunningAnalysis {

        private final VoiceDeliveryAnalysis analysis;
        private final ProviderExecutionContext context;
        private volatile CompletableFuture<Void> future;

        private RunningAnalysis(VoiceDeliveryAnalysis analysis, ProviderExecutionContext context) {
            this.analysis = analysis;
            this.context = context;
        }
    }
}
