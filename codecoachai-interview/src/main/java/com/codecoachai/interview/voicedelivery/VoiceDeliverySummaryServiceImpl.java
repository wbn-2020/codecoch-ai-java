package com.codecoachai.interview.voicedelivery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.interview.domain.entity.InterviewTranscript;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.domain.enums.InterviewTranscriptStatusEnum;
import com.codecoachai.interview.domain.enums.InterviewVoiceStatusEnum;
import com.codecoachai.interview.mapper.InterviewTranscriptMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VoiceDeliverySummaryServiceImpl implements VoiceDeliverySummaryService {

    static final int MAX_SESSION_COUNT = 100;

    private final VoiceDeliveryAnalysisMapper analysisMapper;
    private final InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    private final InterviewTranscriptMapper transcriptMapper;

    public VoiceDeliverySummaryServiceImpl(VoiceDeliveryAnalysisMapper analysisMapper,
                                           InterviewVoiceSubmissionMapper voiceSubmissionMapper,
                                           InterviewTranscriptMapper transcriptMapper) {
        this.analysisMapper = analysisMapper;
        this.voiceSubmissionMapper = voiceSubmissionMapper;
        this.transcriptMapper = transcriptMapper;
    }

    @Override
    public VoiceDeliverySummaryVO summary(Long userId, Long sessionId) {
        if (sessionId == null) {
            return missing(null);
        }
        return summaries(userId, List.of(sessionId)).get(sessionId);
    }

    @Override
    public Map<Long, VoiceDeliverySummaryVO> summaries(Long userId, List<Long> sessionIds) {
        List<Long> boundedIds = sessionIds == null
                ? List.of()
                : sessionIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(MAX_SESSION_COUNT)
                .toList();
        Map<Long, VoiceDeliverySummaryVO> result = new LinkedHashMap<>();
        for (Long sessionId : boundedIds) {
            result.put(sessionId, missing(sessionId));
        }
        if (userId == null || boundedIds.isEmpty()) {
            return result;
        }
        List<VoiceDeliveryAnalysis> analyses =
                analysisMapper.selectLatestBySessions(userId, boundedIds, MAX_SESSION_COUNT);
        if (analyses == null) {
            return result;
        }
        for (VoiceDeliveryAnalysis analysis : analyses) {
            if (analysis != null && result.containsKey(analysis.getSessionId())) {
                result.put(analysis.getSessionId(), toSummary(userId, analysis));
            }
        }
        return result;
    }

    private VoiceDeliverySummaryVO toSummary(Long userId, VoiceDeliveryAnalysis analysis) {
        VoiceDeliverySummaryVO summary = new VoiceDeliverySummaryVO();
        summary.setSessionId(analysis.getSessionId());
        summary.setAnalysisId(analysis.getId());
        summary.setStatus(analysis.getTaskStatus());
        boolean succeeded = "SUCCEEDED".equalsIgnoreCase(analysis.getTaskStatus());
        boolean hasAudioEvidence =
                analysis.getAudioDurationMs() != null && analysis.getAudioDurationMs() > 0;
        boolean hasEvidenceChain = succeeded && hasAudioEvidence && hasEvidenceChain(userId, analysis);
        boolean available = succeeded && hasAudioEvidence && hasEvidenceChain;
        summary.setAvailable(available);
        summary.setMissingReason(available
                ? null
                : succeeded && !hasAudioEvidence
                        ? "VOICE_DELIVERY_AUDIO_EVIDENCE_MISSING"
                        : succeeded && !hasEvidenceChain
                                ? "VOICE_DELIVERY_EVIDENCE_CHAIN_MISSING"
                                : missingReason(analysis));
        if (available) {
            summary.setAudioDurationMs(analysis.getAudioDurationMs());
            summary.setWordCount(analysis.getWordCount());
            summary.setSpeakingRatePerMinute(analysis.getSpeakingRatePerMinute());
            summary.setFillerCount(analysis.getFillerCount());
            summary.setPauseMetricsAvailable(Boolean.TRUE.equals(analysis.getPauseMetricsAvailable()));
            summary.setPauseCount(analysis.getPauseCount());
            summary.setAveragePauseMs(analysis.getAveragePauseMs());
            summary.setLongestPauseMs(analysis.getLongestPauseMs());
        } else {
            summary.setPauseMetricsAvailable(Boolean.FALSE);
        }
        summary.setWarningCodes(splitWarnings(analysis.getWarningCodes()));
        summary.setCompletedAt(analysis.getCompletedAt());
        return summary;
    }

    private boolean hasEvidenceChain(Long userId, VoiceDeliveryAnalysis analysis) {
        if (analysis.getVoiceSubmissionId() == null
                || !Objects.equals(userId, analysis.getUserId())
                || analysis.getSessionId() == null) {
            return false;
        }
        InterviewVoiceSubmission submission =
                voiceSubmissionMapper.selectOne(new LambdaQueryWrapper<InterviewVoiceSubmission>()
                        .eq(InterviewVoiceSubmission::getId, analysis.getVoiceSubmissionId())
                        .eq(InterviewVoiceSubmission::getSessionId, analysis.getSessionId())
                        .eq(InterviewVoiceSubmission::getUserId, userId)
                        .eq(InterviewVoiceSubmission::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (submission == null
                || !Objects.equals(analysis.getVoiceSubmissionId(), submission.getId())
                || !Objects.equals(analysis.getSessionId(), submission.getSessionId())
                || !Objects.equals(userId, submission.getUserId())
                || submission.getFileId() == null
                || !Objects.equals(analysis.getAudioDurationMs(), submission.getAudioDurationMs())
                || !InterviewVoiceStatusEnum.CONFIRMED.name().equals(submission.getVoiceStatus())) {
            return false;
        }
        List<InterviewTranscript> transcripts = transcriptMapper.selectList(
                new LambdaQueryWrapper<InterviewTranscript>()
                .eq(InterviewTranscript::getVoiceSubmissionId, submission.getId())
                .eq(InterviewTranscript::getSessionId, analysis.getSessionId())
                .eq(InterviewTranscript::getUserId, userId)
                .eq(InterviewTranscript::getTranscriptStatus, InterviewTranscriptStatusEnum.CONFIRMED.name())
                .eq(InterviewTranscript::getDeleted, CommonConstants.NO)
                .orderByAsc(InterviewTranscript::getId));
        if (transcripts == null || transcripts.size() != 1) {
            return false;
        }
        InterviewTranscript transcript = transcripts.get(0);
        return transcript != null
                && Objects.equals(submission.getId(), transcript.getVoiceSubmissionId())
                && Objects.equals(analysis.getSessionId(), transcript.getSessionId())
                && Objects.equals(userId, transcript.getUserId())
                && InterviewTranscriptStatusEnum.CONFIRMED.name().equals(transcript.getTranscriptStatus())
                && StringUtils.hasText(transcript.getConfirmedText());
    }

    private VoiceDeliverySummaryVO missing(Long sessionId) {
        VoiceDeliverySummaryVO summary = new VoiceDeliverySummaryVO();
        summary.setSessionId(sessionId);
        summary.setAvailable(Boolean.FALSE);
        summary.setStatus("NOT_ANALYZED");
        summary.setMissingReason("VOICE_DELIVERY_NOT_ANALYZED");
        summary.setPauseMetricsAvailable(Boolean.FALSE);
        summary.setWarningCodes(List.of());
        return summary;
    }

    private String missingReason(VoiceDeliveryAnalysis analysis) {
        if (analysis.getErrorCode() != null && !analysis.getErrorCode().isBlank()) {
            return analysis.getErrorCode();
        }
        return switch (String.valueOf(analysis.getTaskStatus()).toUpperCase()) {
            case "QUEUED", "RUNNING" -> "VOICE_DELIVERY_ANALYSIS_PENDING";
            case "CANCELLED" -> "VOICE_DELIVERY_ANALYSIS_CANCELLED";
            case "TIMED_OUT" -> "VOICE_DELIVERY_ANALYSIS_TIMED_OUT";
            default -> "VOICE_DELIVERY_ANALYSIS_UNAVAILABLE";
        };
    }

    private List<String> splitWarnings(String warnings) {
        return warnings == null || warnings.isBlank()
                ? List.of()
                : Arrays.stream(warnings.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
