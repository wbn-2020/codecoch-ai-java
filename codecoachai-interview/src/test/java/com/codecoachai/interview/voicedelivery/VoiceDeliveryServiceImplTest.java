package com.codecoachai.interview.voicedelivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
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
import java.util.List;
import java.util.concurrent.Executor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceDeliveryServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 3L;
    private static final Long SUBMISSION_ID = 41L;

    @Mock
    private VoiceDeviceCheckMapper deviceCheckMapper;
    @Mock
    private VoiceDeliveryAnalysisMapper analysisMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    @Mock
    private InterviewTranscriptMapper transcriptMapper;
    @Mock
    private VoiceDeliveryAnalyzer analyzer;
    @Mock
    private Executor executor;

    @InjectMocks
    private VoiceDeliveryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(InterviewSession.class);
        init(InterviewVoiceSubmission.class);
        init(InterviewTranscript.class);
        init(VoiceDeliveryAnalysis.class);
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsAnalysisWhenVoiceSubmissionIdIsMissing() {
        when(sessionMapper.selectOne(any())).thenReturn(session());
        VoiceDeliveryAnalysisCreateDTO dto = request(null, 12000L, "client transcript");

        assertThrows(BusinessException.class, () -> service.createAnalysis(SESSION_ID, dto));

        verify(analysisMapper, never()).insert(any(VoiceDeliveryAnalysis.class));
        verify(analyzer, never()).analyze(any(), any());
    }

    @Test
    void usesServerSubmissionDurationAndConfirmedTranscriptInsteadOfClientClaims() {
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission(5000L));
        InterviewTranscript persistedTranscript = transcript(
                USER_ID, SESSION_ID, SUBMISSION_ID, "trusted confirmed transcript");
        lenient().when(transcriptMapper.selectOne(any())).thenReturn(persistedTranscript);
        lenient().when(transcriptMapper.selectList(any())).thenReturn(List.of(persistedTranscript));
        when(analysisMapper.insert(any(VoiceDeliveryAnalysis.class))).thenAnswer(invocation -> {
            invocation.<VoiceDeliveryAnalysis>getArgument(0).setId(91L);
            return 1;
        });
        when(analyzer.analyze(any(), any(ProviderExecutionContext.class))).thenReturn(metrics());
        VoiceDeliveryAnalysisCreateDTO dto = request(
                SUBMISSION_ID, 99999L, "untrusted client transcript");
        dto.setTimestampSource("FORGED_CLIENT");
        dto.setWordTimings(List.of(timing("forged", 0L, 4999L)));

        service.createAnalysis(SESSION_ID, dto);

        ArgumentCaptor<VoiceDeliveryAnalysisCreateDTO> requestCaptor =
                ArgumentCaptor.forClass(VoiceDeliveryAnalysisCreateDTO.class);
        verify(analyzer).analyze(requestCaptor.capture(), any(ProviderExecutionContext.class));
        assertEquals(5000L, requestCaptor.getValue().getAudioDurationMs());
        assertEquals("trusted confirmed transcript", requestCaptor.getValue().getTranscript());
        assertEquals("NONE", requestCaptor.getValue().getTimestampSource());
        assertEquals(List.of(), requestCaptor.getValue().getWordTimings());

        ArgumentCaptor<VoiceDeliveryAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(VoiceDeliveryAnalysis.class);
        verify(analysisMapper).insert(analysisCaptor.capture());
        assertEquals(5000L, analysisCaptor.getValue().getAudioDurationMs());
        assertEquals(SUBMISSION_ID, analysisCaptor.getValue().getVoiceSubmissionId());
        assertEquals("NONE", analysisCaptor.getValue().getTimestampSource());
        assertEquals(Boolean.FALSE, analysisCaptor.getValue().getTimestampsAvailable());
        assertEquals(Boolean.FALSE, analysisCaptor.getValue().getPauseMetricsAvailable());
    }

    @Test
    void rejectsTranscriptEvidenceThatDoesNotBelongToSubmissionOwner() {
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission(5000L));
        InterviewTranscript otherUserTranscript = transcript(
                99L, SESSION_ID, SUBMISSION_ID, "other user's transcript");
        lenient().when(transcriptMapper.selectOne(any())).thenReturn(otherUserTranscript);
        lenient().when(transcriptMapper.selectList(any())).thenReturn(List.of(otherUserTranscript));
        VoiceDeliveryAnalysisCreateDTO dto = request(
                SUBMISSION_ID, 5000L, "other user's transcript");

        assertThrows(BusinessException.class, () -> service.createAnalysis(SESSION_ID, dto));

        verify(analysisMapper, never()).insert(any(VoiceDeliveryAnalysis.class));
        verify(analyzer, never()).analyze(any(), any());
    }

    @Test
    void rejectsMultipleConfirmedTranscriptsForSameSubmission() {
        when(sessionMapper.selectOne(any())).thenReturn(session());
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission(5000L));
        InterviewTranscript first = transcript(
                USER_ID, SESSION_ID, SUBMISSION_ID, "first confirmed transcript");
        InterviewTranscript second = transcript(
                USER_ID, SESSION_ID, SUBMISSION_ID, "second confirmed transcript");
        second.setId(52L);
        lenient().when(transcriptMapper.selectOne(any())).thenReturn(first);
        lenient().when(transcriptMapper.selectList(any())).thenReturn(List.of(first, second));
        lenient().when(analysisMapper.insert(any(VoiceDeliveryAnalysis.class))).thenAnswer(invocation -> {
            invocation.<VoiceDeliveryAnalysis>getArgument(0).setId(92L);
            return 1;
        });
        lenient().when(analyzer.analyze(any(), any(ProviderExecutionContext.class))).thenReturn(metrics());
        VoiceDeliveryAnalysisCreateDTO dto = request(
                SUBMISSION_ID, 5000L, "ignored client transcript");

        assertThrows(BusinessException.class, () -> service.createAnalysis(SESSION_ID, dto));

        verify(analysisMapper, never()).insert(any(VoiceDeliveryAnalysis.class));
        verify(analyzer, never()).analyze(any(), any());
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        return session;
    }

    private InterviewVoiceSubmission submission(Long durationMs) {
        InterviewVoiceSubmission submission = new InterviewVoiceSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setUserId(USER_ID);
        submission.setSessionId(SESSION_ID);
        submission.setFileId(901L);
        submission.setAudioDurationMs(durationMs);
        submission.setVoiceStatus(InterviewVoiceStatusEnum.CONFIRMED.name());
        return submission;
    }

    private InterviewTranscript transcript(Long userId, Long sessionId, Long submissionId, String text) {
        InterviewTranscript transcript = new InterviewTranscript();
        transcript.setId(51L);
        transcript.setUserId(userId);
        transcript.setSessionId(sessionId);
        transcript.setVoiceSubmissionId(submissionId);
        transcript.setConfirmedText(text);
        transcript.setTranscriptStatus(InterviewTranscriptStatusEnum.CONFIRMED.name());
        return transcript;
    }

    private VoiceDeliveryAnalysisCreateDTO request(Long submissionId, Long durationMs, String transcript) {
        VoiceDeliveryAnalysisCreateDTO dto = new VoiceDeliveryAnalysisCreateDTO();
        dto.setVoiceSubmissionId(submissionId);
        dto.setTranscript(transcript);
        dto.setAudioDurationMs(durationMs);
        dto.setTimeoutMs(1000L);
        return dto;
    }

    private VoiceDeliveryMetrics metrics() {
        return VoiceDeliveryMetrics.builder()
                .wordCount(3)
                .speakingRatePerMinute(new BigDecimal("36.00"))
                .fillerCount(0)
                .timestampsAvailable(false)
                .pauseMetricsAvailable(false)
                .warningCodes(java.util.List.of("WORD_TIMESTAMPS_REQUIRED_FOR_PAUSE_METRICS"))
                .build();
    }

    private VoiceWordTimingDTO timing(String text, Long startMs, Long endMs) {
        VoiceWordTimingDTO timing = new VoiceWordTimingDTO();
        timing.setText(text);
        timing.setStartMs(startMs);
        timing.setEndMs(endMs);
        return timing;
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }
}
