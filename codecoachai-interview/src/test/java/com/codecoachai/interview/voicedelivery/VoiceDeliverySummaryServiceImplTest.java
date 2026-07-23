package com.codecoachai.interview.voicedelivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.interview.domain.entity.InterviewTranscript;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.domain.enums.InterviewTranscriptStatusEnum;
import com.codecoachai.interview.domain.enums.InterviewVoiceStatusEnum;
import com.codecoachai.interview.mapper.InterviewTranscriptMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceDeliverySummaryServiceImplTest {

    @Mock
    private VoiceDeliveryAnalysisMapper analysisMapper;
    @Mock
    private InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    @Mock
    private InterviewTranscriptMapper transcriptMapper;

    @InjectMocks
    private VoiceDeliverySummaryServiceImpl service;

    @Test
    void summarizesLatestAnalysisPerSessionAndKeepsMissingDataExplicit() {
        VoiceDeliveryAnalysis succeeded = analysis(21L, 101L, "SUCCEEDED");
        succeeded.setVoiceSubmissionId(201L);
        succeeded.setAudioDurationMs(90000L);
        succeeded.setWordCount(240);
        succeeded.setSpeakingRatePerMinute(new BigDecimal("160.50"));
        succeeded.setFillerCount(3);
        succeeded.setPauseMetricsAvailable(Boolean.FALSE);
        succeeded.setWarningCodes("WORD_TIMESTAMPS_UNAVAILABLE");
        succeeded.setCompletedAt(LocalDateTime.of(2026, 7, 11, 10, 30));

        VoiceDeliveryAnalysis failed = analysis(22L, 102L, "FAILED");
        failed.setErrorCode("VOICE_DELIVERY_ANALYSIS_FAILED");
        failed.setCompletedAt(LocalDateTime.of(2026, 7, 11, 11, 0));

        when(analysisMapper.selectLatestBySessions(9L, List.of(101L, 102L, 103L), 100))
                .thenReturn(List.of(succeeded, failed));
        stubValidEvidence(201L, 101L, 90000L, "trusted transcript");

        Map<Long, VoiceDeliverySummaryVO> summaries =
                service.summaries(9L, List.of(101L, 102L, 103L));

        VoiceDeliverySummaryVO success = summaries.get(101L);
        assertTrue(success.getAvailable());
        assertEquals("SUCCEEDED", success.getStatus());
        assertEquals(new BigDecimal("160.50"), success.getSpeakingRatePerMinute());
        assertFalse(success.getPauseMetricsAvailable());
        assertEquals(List.of("WORD_TIMESTAMPS_UNAVAILABLE"), success.getWarningCodes());

        VoiceDeliverySummaryVO failure = summaries.get(102L);
        assertFalse(failure.getAvailable());
        assertEquals("FAILED", failure.getStatus());
        assertEquals("VOICE_DELIVERY_ANALYSIS_FAILED", failure.getMissingReason());

        VoiceDeliverySummaryVO missing = summaries.get(103L);
        assertFalse(missing.getAvailable());
        assertEquals("NOT_ANALYZED", missing.getStatus());
        assertEquals("VOICE_DELIVERY_NOT_ANALYZED", missing.getMissingReason());
        assertNull(missing.getSpeakingRatePerMinute());

        verify(analysisMapper).selectLatestBySessions(9L, List.of(101L, 102L, 103L), 100);
    }

    @Test
    void capsAndDeduplicatesSessionIdsBeforeQuerying() {
        List<Long> requested = java.util.stream.LongStream.rangeClosed(1, 130)
                .boxed()
                .flatMap(id -> java.util.stream.Stream.of(id, id))
                .toList();
        List<Long> expected = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
        when(analysisMapper.selectLatestBySessions(9L, expected, 100)).thenReturn(List.of());

        Map<Long, VoiceDeliverySummaryVO> summaries = service.summaries(9L, requested);

        assertEquals(100, summaries.size());
        verify(analysisMapper).selectLatestBySessions(9L, expected, 100);
    }

    @Test
    void doesNotExposeSuccessfulTextOnlyAnalysisAsVoiceEvidence() {
        VoiceDeliveryAnalysis textOnly = analysis(23L, 104L, "SUCCEEDED");
        textOnly.setWordCount(20);
        textOnly.setFillerCount(0);
        textOnly.setPauseMetricsAvailable(Boolean.FALSE);

        when(analysisMapper.selectLatestBySessions(9L, List.of(104L), 100))
                .thenReturn(List.of(textOnly));

        VoiceDeliverySummaryVO summary = service.summary(9L, 104L);

        assertFalse(summary.getAvailable());
        assertEquals("VOICE_DELIVERY_AUDIO_EVIDENCE_MISSING", summary.getMissingReason());
        assertNull(summary.getSpeakingRatePerMinute());
        assertNull(summary.getFillerCount());
    }

    @Test
    void doesNotExposeHistoricalSuccessWithoutValidSubmissionTranscriptEvidence() {
        VoiceDeliveryAnalysis historical = analysis(24L, 105L, "SUCCEEDED");
        historical.setVoiceSubmissionId(204L);
        historical.setAudioDurationMs(30000L);
        historical.setWordCount(80);
        historical.setSpeakingRatePerMinute(new BigDecimal("160.00"));
        historical.setFillerCount(1);
        when(analysisMapper.selectLatestBySessions(9L, List.of(105L), 100))
                .thenReturn(List.of(historical));

        VoiceDeliverySummaryVO summary = service.summary(9L, 105L);

        assertFalse(summary.getAvailable());
        assertEquals("VOICE_DELIVERY_EVIDENCE_CHAIN_MISSING", summary.getMissingReason());
        assertNull(summary.getSpeakingRatePerMinute());
        assertNull(summary.getFillerCount());
    }

    @Test
    void doesNotExposeHistoricalSuccessWithMultipleConfirmedTranscripts() {
        VoiceDeliveryAnalysis historical = analysis(25L, 106L, "SUCCEEDED");
        historical.setVoiceSubmissionId(205L);
        historical.setAudioDurationMs(30000L);
        historical.setWordCount(80);
        historical.setSpeakingRatePerMinute(new BigDecimal("160.00"));
        historical.setFillerCount(1);
        when(analysisMapper.selectLatestBySessions(9L, List.of(106L), 100))
                .thenReturn(List.of(historical));
        InterviewVoiceSubmission submission = submission(205L, 106L, 30000L);
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission);
        InterviewTranscript first = transcript(301L, 205L, 106L, "first evidence");
        InterviewTranscript second = transcript(302L, 205L, 106L, "second evidence");
        lenient().when(transcriptMapper.selectOne(any())).thenReturn(first);
        lenient().when(transcriptMapper.selectList(any())).thenReturn(List.of(first, second));

        VoiceDeliverySummaryVO summary = service.summary(9L, 106L);

        assertFalse(summary.getAvailable());
        assertEquals("VOICE_DELIVERY_EVIDENCE_CHAIN_MISSING", summary.getMissingReason());
        assertNull(summary.getSpeakingRatePerMinute());
    }

    @Test
    void doesNotExposeHistoricalSuccessWithBlankConfirmedTranscript() {
        VoiceDeliveryAnalysis historical = analysis(26L, 107L, "SUCCEEDED");
        historical.setVoiceSubmissionId(206L);
        historical.setAudioDurationMs(30000L);
        historical.setWordCount(80);
        when(analysisMapper.selectLatestBySessions(9L, List.of(107L), 100))
                .thenReturn(List.of(historical));
        when(voiceSubmissionMapper.selectOne(any()))
                .thenReturn(submission(206L, 107L, 30000L));
        InterviewTranscript blank = transcript(303L, 206L, 107L, "   ");
        lenient().when(transcriptMapper.selectOne(any())).thenReturn(blank);
        lenient().when(transcriptMapper.selectList(any())).thenReturn(List.of(blank));

        VoiceDeliverySummaryVO summary = service.summary(9L, 107L);

        assertFalse(summary.getAvailable());
        assertEquals("VOICE_DELIVERY_EVIDENCE_CHAIN_MISSING", summary.getMissingReason());
        assertNull(summary.getWordCount());
    }

    @Test
    void doesNotExposeHistoricalSuccessWhenSubmissionDurationOrStatusDrifts() {
        VoiceDeliveryAnalysis durationDrift = analysis(27L, 108L, "SUCCEEDED");
        durationDrift.setVoiceSubmissionId(207L);
        durationDrift.setAudioDurationMs(30000L);
        VoiceDeliveryAnalysis statusDrift = analysis(28L, 109L, "SUCCEEDED");
        statusDrift.setVoiceSubmissionId(208L);
        statusDrift.setAudioDurationMs(40000L);
        when(analysisMapper.selectLatestBySessions(9L, List.of(108L, 109L), 100))
                .thenReturn(List.of(durationDrift, statusDrift));
        InterviewVoiceSubmission wrongDuration = submission(207L, 108L, 29999L);
        InterviewVoiceSubmission wrongStatus = submission(208L, 109L, 40000L);
        wrongStatus.setVoiceStatus(InterviewVoiceStatusEnum.TRANSCRIBED.name());
        when(voiceSubmissionMapper.selectOne(any()))
                .thenReturn(wrongDuration, wrongStatus);

        Map<Long, VoiceDeliverySummaryVO> summaries =
                service.summaries(9L, List.of(108L, 109L));

        assertFalse(summaries.get(108L).getAvailable());
        assertFalse(summaries.get(109L).getAvailable());
        assertEquals("VOICE_DELIVERY_EVIDENCE_CHAIN_MISSING", summaries.get(108L).getMissingReason());
        assertEquals("VOICE_DELIVERY_EVIDENCE_CHAIN_MISSING", summaries.get(109L).getMissingReason());
    }

    private VoiceDeliveryAnalysis analysis(Long id, Long sessionId, String status) {
        VoiceDeliveryAnalysis analysis = new VoiceDeliveryAnalysis();
        analysis.setId(id);
        analysis.setUserId(9L);
        analysis.setSessionId(sessionId);
        analysis.setTaskStatus(status);
        return analysis;
    }

    private void stubValidEvidence(Long submissionId, Long sessionId, Long durationMs, String confirmedText) {
        InterviewVoiceSubmission submission = submission(submissionId, sessionId, durationMs);
        lenient().when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission);

        InterviewTranscript transcript = transcript(301L, submissionId, sessionId, confirmedText);
        lenient().when(transcriptMapper.selectOne(any())).thenReturn(transcript);
        lenient().when(transcriptMapper.selectList(any())).thenReturn(List.of(transcript));
    }

    private InterviewVoiceSubmission submission(Long submissionId, Long sessionId, Long durationMs) {
        InterviewVoiceSubmission submission = new InterviewVoiceSubmission();
        submission.setId(submissionId);
        submission.setUserId(9L);
        submission.setSessionId(sessionId);
        submission.setFileId(901L);
        submission.setAudioDurationMs(durationMs);
        submission.setVoiceStatus(InterviewVoiceStatusEnum.CONFIRMED.name());
        return submission;
    }

    private InterviewTranscript transcript(Long transcriptId,
                                           Long submissionId,
                                           Long sessionId,
                                           String confirmedText) {
        InterviewTranscript transcript = new InterviewTranscript();
        transcript.setId(transcriptId);
        transcript.setUserId(9L);
        transcript.setSessionId(sessionId);
        transcript.setVoiceSubmissionId(submissionId);
        transcript.setConfirmedText(confirmedText);
        transcript.setTranscriptStatus(InterviewTranscriptStatusEnum.CONFIRMED.name());
        return transcript;
    }
}
