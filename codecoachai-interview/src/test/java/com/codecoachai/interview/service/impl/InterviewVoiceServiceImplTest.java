package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.config.InterviewAsrProperties;
import com.codecoachai.interview.domain.dto.InterviewTranscriptConfirmDTO;
import com.codecoachai.interview.domain.dto.InterviewVoiceDiscardDTO;
import com.codecoachai.interview.domain.dto.InterviewVoiceSubmissionCreateDTO;
import com.codecoachai.interview.domain.dto.SubmitInterviewAnswerDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewTranscript;
import com.codecoachai.interview.domain.entity.InterviewVoiceSubmission;
import com.codecoachai.interview.domain.enums.InterviewTranscriptStatusEnum;
import com.codecoachai.interview.domain.enums.InterviewVoiceStatusEnum;
import com.codecoachai.interview.feign.FileFeignClient;
import com.codecoachai.interview.feign.vo.InnerFileInfoVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.InterviewTranscriptMapper;
import com.codecoachai.interview.mapper.InterviewVoiceSubmissionMapper;
import com.codecoachai.interview.service.AsrService;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class InterviewVoiceServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 3L;

    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewVoiceSubmissionMapper voiceSubmissionMapper;
    @Mock
    private InterviewTranscriptMapper transcriptMapper;
    @Mock
    private FileFeignClient fileFeignClient;
    @Mock
    private AsrService asrService;

    private InterviewAsrProperties properties;
    private InterviewVoiceServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(InterviewVoiceSubmission.class);
        initTableInfo(InterviewTranscript.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
        }
    }

    @BeforeEach
    void setUp() {
        properties = new InterviewAsrProperties();
        service = new InterviewVoiceServiceImpl(
                sessionMapper,
                messageMapper,
                voiceSubmissionMapper,
                transcriptMapper,
                fileFeignClient,
                asrService,
                properties,
                new TransactionTemplate(new NoOpTransactionManager()));
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createSubmissionRejectsImageMetadataEvenWhenFileBizTypeIsVoice() {
        when(sessionMapper.selectOne(any())).thenReturn(session("WAITING_ANSWER"));
        when(messageMapper.selectOne(any())).thenReturn(question(11L, 21L));
        InnerFileInfoVO file = InterviewVoiceAudioValidatorTest.audioFile("png", "image/png", 1024L);
        file.setBizType("INTERVIEW_VOICE");
        when(fileFeignClient.detail(9L, USER_ID, "INTERVIEW_VOICE")).thenReturn(Result.success(file));

        InterviewVoiceSubmissionCreateDTO dto = new InterviewVoiceSubmissionCreateDTO();
        dto.setFileId(9L);
        dto.setQuestionMessageId(11L);
        dto.setQuestionId(21L);
        dto.setAudioDurationMs(10_000L);
        dto.setMimeType("image/png");

        assertThrows(BusinessException.class, () -> service.createSubmission(SESSION_ID, dto));
        verify(voiceSubmissionMapper, never()).insert(any(InterviewVoiceSubmission.class));
    }

    @Test
    void confirmRejectsTranscriptWhenItIsNoLongerCurrentQuestion() {
        when(sessionMapper.selectOne(any())).thenReturn(session("WAITING_ANSWER"));
        when(transcriptMapper.selectOne(any())).thenReturn(transcript(31L, 41L, "voice text"));
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission(41L, 11L));
        when(messageMapper.selectOne(any())).thenReturn(question(12L, 22L));
        InterviewTranscriptConfirmDTO dto = new InterviewTranscriptConfirmDTO();
        dto.setConfirmedText("voice text");

        assertThrows(BusinessException.class,
                () -> service.confirmTranscript(SESSION_ID, 31L, dto));
        verify(transcriptMapper, never()).updateById(any(InterviewTranscript.class));
    }

    @Test
    void mixedTextKeepsConfirmedSegmentAndNormalizesAnswerSource() {
        InterviewTranscript transcript = transcript(31L, 41L, "voice text");
        transcript.setTranscriptStatus(InterviewTranscriptStatusEnum.CONFIRMED.name());
        when(transcriptMapper.selectOne(any())).thenReturn(transcript);
        InterviewVoiceSubmission submission = submission(41L, 11L);
        submission.setVoiceStatus(InterviewVoiceStatusEnum.CONFIRMED.name());
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission);
        SubmitInterviewAnswerDTO dto = new SubmitInterviewAnswerDTO();
        dto.setTranscriptId(31L);
        dto.setVoiceSubmissionId(41L);
        dto.setMessageId(11L);
        dto.setQuestionId(21L);
        dto.setAnswerContent("typed prefix\n\nvoice text\n\ntyped suffix");

        service.validateConfirmedTranscriptForAnswer(SESSION_ID, dto);

        assertEquals("VOICE_TRANSCRIPT_WITH_TEXT", dto.getAnswerSource());
        assertEquals("VOICE_TRANSCRIPT_WITH_TEXT", transcript.getAnswerSource());
        verify(transcriptMapper).updateById(transcript);
    }

    @Test
    void discardScrubsTranscriptAndTriggersPhysicalDeletionForUnsubmittedConfirmedVoice() {
        InterviewVoiceSubmission submission = submission(41L, 11L);
        submission.setVoiceStatus(InterviewVoiceStatusEnum.CONFIRMED.name());
        submission.setFileDeleteStatus("RETAINED");
        InterviewTranscript transcript = transcript(31L, 41L, "sensitive voice text");
        transcript.setDraftText("sensitive draft");
        transcript.setTranscriptStatus(InterviewTranscriptStatusEnum.CONFIRMED.name());
        when(voiceSubmissionMapper.selectOne(any())).thenReturn(submission);
        when(transcriptMapper.selectList(any())).thenReturn(List.of(transcript));
        when(sessionMapper.selectOne(any())).thenReturn(session("WAITING_ANSWER"));
        when(messageMapper.selectOne(any())).thenReturn(question(11L, 21L));
        when(fileFeignClient.delete(9L, USER_ID, "INTERVIEW_VOICE")).thenReturn(Result.success());
        InterviewVoiceDiscardDTO dto = new InterviewVoiceDiscardDTO();
        dto.setReason("MODE_SWITCH");

        service.discardSubmission(SESSION_ID, 41L, dto);

        assertEquals(InterviewVoiceStatusEnum.DISCARDED.name(), submission.getVoiceStatus());
        assertEquals("DELETE_PENDING", submission.getFileDeleteStatus());
        assertNull(transcript.getDraftText());
        assertNull(transcript.getConfirmedText());
        assertEquals(InterviewTranscriptStatusEnum.REJECTED.name(), transcript.getTranscriptStatus());
        verify(fileFeignClient).delete(9L, USER_ID, "INTERVIEW_VOICE");
        verify(voiceSubmissionMapper).update(any(), any(Wrapper.class));
    }

    private InterviewSession session(String status) {
        InterviewSession session = new InterviewSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setStatus(status);
        return session;
    }

    private InterviewMessage question(Long messageId, Long questionId) {
        InterviewMessage message = new InterviewMessage();
        message.setId(messageId);
        message.setSessionId(SESSION_ID);
        message.setQuestionId(questionId);
        message.setRole("AI");
        message.setMessageType("QUESTION");
        return message;
    }

    private InterviewVoiceSubmission submission(Long submissionId, Long questionMessageId) {
        InterviewVoiceSubmission submission = new InterviewVoiceSubmission();
        submission.setId(submissionId);
        submission.setUserId(USER_ID);
        submission.setSessionId(SESSION_ID);
        submission.setQuestionMessageId(questionMessageId);
        submission.setQuestionId(21L);
        submission.setFileId(9L);
        submission.setVoiceStatus(InterviewVoiceStatusEnum.TRANSCRIBED.name());
        return submission;
    }

    private InterviewTranscript transcript(Long transcriptId, Long submissionId, String confirmedText) {
        InterviewTranscript transcript = new InterviewTranscript();
        transcript.setId(transcriptId);
        transcript.setUserId(USER_ID);
        transcript.setSessionId(SESSION_ID);
        transcript.setVoiceSubmissionId(submissionId);
        transcript.setQuestionMessageId(11L);
        transcript.setQuestionId(21L);
        transcript.setConfirmedText(confirmedText);
        transcript.setFallback(Boolean.FALSE);
        return transcript;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
