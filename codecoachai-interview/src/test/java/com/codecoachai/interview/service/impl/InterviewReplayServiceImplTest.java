package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.dto.InterviewReplayCreateDTO;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.mapper.InterviewReplayMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.scenario.InterviewScenarioBindingResolver;
import com.codecoachai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class InterviewReplayServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long SOURCE_SESSION_ID = 100L;
    private static final Long SOURCE_REPORT_ID = 88L;

    @Mock
    private InterviewReplayMapper replayMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewScenarioBindingResolver bindingResolver;
    @Mock
    private InterviewService interviewService;

    private InterviewReplayServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewReplayServiceImpl(
                replayMapper, reportMapper, sessionMapper, bindingResolver,
                interviewService, new ObjectMapper());
        LoginUserContext.setLoginUser(
                LoginUser.builder().userId(USER_ID).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createsReplayCopyingFullConfigIncludingScenarioBinding() {
        when(replayMapper.selectOne(any())).thenReturn(null, (InterviewReplay) null);
        when(sessionMapper.selectById(SOURCE_SESSION_ID)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report());
        when(bindingResolver.reusableScenarioVersionId(SOURCE_SESSION_ID, USER_ID, true))
                .thenReturn(9001L);
        when(replayMapper.insert(any(InterviewReplay.class))).thenAnswer(invocation -> {
            InterviewReplay replay = invocation.getArgument(0);
            replay.setId(600L);
            return 1;
        });
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(210L);
        when(interviewService.create(any())).thenReturn(interview);
        when(replayMapper.updateById(any(InterviewReplay.class))).thenReturn(1);

        var result = service.create(SOURCE_SESSION_ID, request("replay-1"));

        assertEquals(600L, result.getId());
        assertEquals(210L, result.getTargetSessionId());
        assertEquals(9001L, result.getScenarioVersionId());
        assertEquals("CREATED", result.getStatus());

        ArgumentCaptor<CreateInterviewDTO> captor =
                ArgumentCaptor.forClass(CreateInterviewDTO.class);
        verify(interviewService).create(captor.capture());
        CreateInterviewDTO copied = captor.getValue();
        assertEquals(9001L, copied.getScenarioVersionId());
        assertEquals(300L, copied.getTargetJobId());
        assertEquals("HARD", copied.getDifficulty());
        assertEquals("PRESSURE", copied.getInterviewerStyle());
        assertEquals("MEDIUM", copied.getFollowUpIntensity());
        assertEquals("REPLAY", copied.getPracticeMode());
        assertEquals("INTERVIEW_REPLAY", copied.getRecommendationSource());
        assertEquals("999", copied.getApplicationPackageId());
        assertTrue(copied.getTitle().startsWith("同配置再练："));
    }

    @Test
    void sessionWithoutScenarioBindingReplaysWithoutScenario() {
        when(replayMapper.selectOne(any())).thenReturn(null, (InterviewReplay) null);
        when(sessionMapper.selectById(SOURCE_SESSION_ID)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report());
        when(bindingResolver.reusableScenarioVersionId(SOURCE_SESSION_ID, USER_ID, true))
                .thenReturn(null);
        when(replayMapper.insert(any(InterviewReplay.class))).thenReturn(1);
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(211L);
        when(interviewService.create(any())).thenReturn(interview);
        when(replayMapper.updateById(any(InterviewReplay.class))).thenReturn(1);

        var result = service.create(SOURCE_SESSION_ID, request("replay-2"));

        assertNull(result.getScenarioVersionId());
        assertEquals(211L, result.getTargetSessionId());
    }

    @Test
    void unpublishedScenarioVersionFailsInsteadOfSilentlyDegrading() {
        when(replayMapper.selectOne(any())).thenReturn(null);
        when(sessionMapper.selectById(SOURCE_SESSION_ID)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report());
        when(bindingResolver.reusableScenarioVersionId(SOURCE_SESSION_ID, USER_ID, true))
                .thenThrow(new BusinessException(
                        com.codecoachai.common.core.enums.ErrorCode.PARAM_ERROR,
                        "源场次绑定的场景版本已下线，无法同配置再练"));

        assertThrows(BusinessException.class,
                () -> service.create(SOURCE_SESSION_ID, request("replay-3")));
        verify(interviewService, never()).create(any());
    }

    @Test
    void idempotentReplayReturnsExistingWithoutCreatingInterview() {
        InterviewReplay existing = new InterviewReplay();
        existing.setId(601L);
        existing.setUserId(USER_ID);
        existing.setSourceSessionId(SOURCE_SESSION_ID);
        existing.setTargetSessionId(212L);
        existing.setStatus("CREATED");
        when(replayMapper.selectOne(any())).thenReturn(existing);

        var result = service.create(SOURCE_SESSION_ID, request("same-token"));

        assertEquals(212L, result.getTargetSessionId());
        assertTrue(result.getIdempotentReplay());
        verify(sessionMapper, never()).selectById(any(Long.class));
        verify(interviewService, never()).create(any());
    }

    @Test
    void sameIdempotencyKeyForDifferentSourceSessionIsRejected() {
        InterviewReplay existing = new InterviewReplay();
        existing.setId(602L);
        existing.setUserId(USER_ID);
        existing.setSourceSessionId(777L);
        when(replayMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class,
                () -> service.create(SOURCE_SESSION_ID, request("stolen-token")));
    }

    @Test
    void duplicateKeyRaceReturnsCommittedReplay() {
        InterviewReplay committed = new InterviewReplay();
        committed.setId(603L);
        committed.setUserId(USER_ID);
        committed.setSourceSessionId(SOURCE_SESSION_ID);
        committed.setTargetSessionId(213L);
        committed.setStatus("CREATED");
        when(replayMapper.selectOne(any())).thenReturn(null, committed);
        when(sessionMapper.selectById(SOURCE_SESSION_ID)).thenReturn(session());
        when(reportMapper.selectOne(any())).thenReturn(report());
        when(bindingResolver.reusableScenarioVersionId(SOURCE_SESSION_ID, USER_ID, true))
                .thenReturn(null);
        when(replayMapper.insert(any(InterviewReplay.class)))
                .thenThrow(new DuplicateKeyException("uk_interview_replay_user_token"));

        var result = service.create(SOURCE_SESSION_ID, request("race-token"));

        assertEquals(213L, result.getTargetSessionId());
        assertTrue(result.getIdempotentReplay());
        verify(interviewService, never()).create(any());
    }

    @Test
    void missingOrUngeneratedReportIsRejected() {
        when(replayMapper.selectOne(any())).thenReturn(null);
        when(sessionMapper.selectById(SOURCE_SESSION_ID)).thenReturn(session());
        InterviewReport notGenerated = report();
        notGenerated.setStatus("GENERATING");
        when(reportMapper.selectOne(any())).thenReturn(notGenerated);

        assertThrows(BusinessException.class,
                () -> service.create(SOURCE_SESSION_ID, request("no-report")));
        verify(interviewService, never()).create(any());
    }

    @Test
    void foreignSessionIsRejected() {
        when(replayMapper.selectOne(any())).thenReturn(null);
        InterviewSession foreign = session();
        foreign.setUserId(999L);
        when(sessionMapper.selectById(SOURCE_SESSION_ID)).thenReturn(foreign);

        assertThrows(BusinessException.class,
                () -> service.create(SOURCE_SESSION_ID, request("foreign")));
        verify(interviewService, never()).create(any());
    }

    private InterviewReplayCreateDTO request(String idempotencyKey) {
        InterviewReplayCreateDTO dto = new InterviewReplayCreateDTO();
        dto.setIdempotencyKey(idempotencyKey);
        return dto;
    }

    private InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setId(SOURCE_SESSION_ID);
        session.setUserId(USER_ID);
        session.setDeleted(0);
        session.setMode("TEXT");
        session.setTitle("Java 后端专项");
        session.setTargetJobId(300L);
        session.setApplicationPackageId(999L);
        session.setDifficulty("HARD");
        session.setInterviewerStyle("PRESSURE");
        session.setFollowUpIntensity("MEDIUM");
        session.setMaxQuestionCount(8);
        session.setTargetSkillCodes("[\"REDIS\",\"MQ\"]");
        session.setProjectEvidenceIds("[31,32]");
        return session;
    }

    private InterviewReport report() {
        InterviewReport report = new InterviewReport();
        report.setId(SOURCE_REPORT_ID);
        report.setSessionId(SOURCE_SESSION_ID);
        report.setUserId(USER_ID);
        report.setStatus("GENERATED");
        report.setRubricVersion("RUBRIC_ID:5");
        report.setDeleted(0);
        return report;
    }
}
