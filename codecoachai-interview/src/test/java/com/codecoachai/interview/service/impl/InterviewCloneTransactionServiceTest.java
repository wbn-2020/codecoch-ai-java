package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.config.InterviewCloneClaimProperties;
import com.codecoachai.interview.domain.dto.CreateInterviewDTO;
import com.codecoachai.interview.domain.entity.InterviewRemediation;
import com.codecoachai.interview.domain.entity.InterviewReplay;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.vo.CreateInterviewVO;
import com.codecoachai.interview.mapper.InterviewRemediationMapper;
import com.codecoachai.interview.mapper.InterviewReplayMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class InterviewCloneTransactionServiceTest {

    @Mock
    private InterviewReplayMapper replayMapper;
    @Mock
    private InterviewRemediationMapper remediationMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewServiceImpl interviewService;

    private InterviewCloneTransactionService service;

    @BeforeAll
    static void initTableInfo() {
        init(InterviewReplay.class);
        init(InterviewRemediation.class);
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    entityClass);
        }
    }

    @BeforeEach
    void setUp() {
        InterviewCloneClaimProperties properties =
                new InterviewCloneClaimProperties();
        properties.setTimeout(Duration.ofMinutes(2));
        service = new InterviewCloneTransactionService(
                replayMapper,
                remediationMapper,
                sessionMapper,
                interviewService,
                properties);
    }

    @Test
    void completedReplayCanBeRecoveredWithCurrentReadBeforeSourceValidation() {
        InterviewReplay candidate = replay(null, null, null);
        InterviewReplay completed = replay(40L, 140L, "CREATED");
        when(replayMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-1"))
                .thenReturn(completed);

        InterviewReplay result = service.recoverCompletedReplay(candidate);

        assertEquals(140L, result.getTargetSessionId());
        verify(replayMapper)
                .selectActiveByIdempotencyKeyForUpdate(10L, "token-1");
    }

    @Test
    void completedReplayRecoveryRejectsDifferentSourceSession() {
        InterviewReplay candidate = replay(null, null, null);
        candidate.setSourceSessionId(101L);
        InterviewReplay completed = replay(40L, 140L, "CREATED");
        when(replayMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-1"))
                .thenReturn(completed);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.recoverCompletedReplay(candidate));

        assertEquals(
                ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(),
                error.getCode());
    }

    @Test
    void failedReplayIsNotRecoveredAsCompleted() {
        InterviewReplay candidate = replay(null, null, null);
        InterviewReplay failed = replay(40L, null, "FAILED");
        when(replayMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-1"))
                .thenReturn(failed);

        assertNull(service.recoverCompletedReplay(candidate));
    }

    @Test
    void completedRemediationRecoveryRejectsDifferentRequestFingerprint() {
        InterviewRemediation candidate =
                remediation(null, null, null);
        candidate.setPracticePurpose("不同的复练目标");
        InterviewRemediation completed =
                remediation(40L, 140L, "CREATED");
        when(remediationMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-2"))
                .thenReturn(completed);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.recoverCompletedRemediation(candidate));

        assertEquals(
                ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(),
                error.getCode());
    }

    @Test
    void duplicateReplayReadsWinnerWithCurrentReadAndReturnsCompletedRecord() {
        InterviewReplay candidate = replay(null, null, null);
        InterviewReplay winner = replay(41L, 88L, "CREATED");
        when(replayMapper.insert(candidate))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(replayMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-1"))
                .thenReturn(winner);

        var result = service.claimReplay(candidate);

        assertFalse(result.owner());
        assertEquals(88L, result.replay().getTargetSessionId());
        verify(replayMapper)
                .selectActiveByIdempotencyKeyForUpdate(10L, "token-1");
    }

    @Test
    void freshCreatingReplayReturnsExplicitConflictInsteadOfSuccess() {
        InterviewReplay candidate = replay(null, null, null);
        InterviewReplay creating = replay(42L, null, "CREATING");
        creating.setClaimToken("owner");
        creating.setClaimedAt(LocalDateTime.now());
        when(replayMapper.insert(candidate))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(replayMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-1"))
                .thenReturn(creating);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.claimReplay(candidate));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), error.getCode());
        assertEquals("CREATION_IN_PROGRESS", error.getMessage());
    }

    @Test
    void staleCreatingReplayCanBeTakenOverWithNewFencingToken() {
        InterviewReplay candidate = replay(null, null, null);
        InterviewReplay stale = replay(43L, null, "CREATING");
        stale.setClaimToken("old-owner");
        stale.setClaimedAt(LocalDateTime.now().minusMinutes(10));
        when(replayMapper.insert(candidate))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(replayMapper.selectActiveByIdempotencyKeyForUpdate(
                10L, "token-1"))
                .thenReturn(stale);
        when(replayMapper.replaceClaim(
                eq(stale), any(String.class), any(LocalDateTime.class)))
                .thenReturn(1);

        var result = service.claimReplay(candidate);

        assertTrue(result.owner());
        assertNotNull(result.claimToken());
        assertFalse("old-owner".equals(result.claimToken()));
        assertEquals("CREATING", result.replay().getStatus());
    }

    @Test
    void replayTargetCreationLocksClaimAndCompletesUsingSameToken() {
        InterviewReplay claimed = replay(44L, null, "CREATING");
        claimed.setClaimToken("claim-44");
        when(replayMapper.selectOwnedClaimForUpdate(44L, "claim-44"))
                .thenReturn(claimed);
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(144L);
        InterviewServiceImpl.InterviewClonePreparation preparation =
                clonePreparation();
        when(interviewService.createPreparedClone(preparation))
                .thenReturn(interview);
        when(replayMapper.markCreated(44L, "claim-44", 144L))
                .thenReturn(1);

        var result = service.createReplayTarget(
                44L, "claim-44", preparation);

        assertEquals(144L, result.replay().getTargetSessionId());
        assertEquals("CREATED", result.replay().getStatus());
        verify(replayMapper).selectOwnedClaimForUpdate(44L, "claim-44");
        verify(replayMapper).markCreated(44L, "claim-44", 144L);
    }

    @Test
    void remediationTargetCreationPersistsSourceContextBeforeCompletingClaim() {
        InterviewRemediation claimed = remediation(45L, null, "CREATING");
        claimed.setClaimToken("claim-45");
        when(remediationMapper.selectOwnedClaimForUpdate(45L, "claim-45"))
                .thenReturn(claimed);
        CreateInterviewVO interview = new CreateInterviewVO();
        interview.setId(145L);
        InterviewServiceImpl.InterviewClonePreparation preparation =
                clonePreparation();
        when(interviewService.createPreparedClone(preparation))
                .thenReturn(interview);
        when(sessionMapper.updateById(any(InterviewSession.class))).thenReturn(1);
        when(remediationMapper.markCreated(45L, "claim-45", 145L))
                .thenReturn(1);

        service.createRemediationTarget(
                45L, "claim-45", preparation);

        ArgumentCaptor<InterviewSession> patchCaptor =
                ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).updateById(patchCaptor.capture());
        assertEquals(145L, patchCaptor.getValue().getId());
        assertEquals(88L, patchCaptor.getValue().getSourceReportId());
        assertEquals("[7,9]", patchCaptor.getValue().getSourceRequirementIds());
        verify(remediationMapper).markCreated(45L, "claim-45", 145L);
    }

    @Test
    void mapperCurrentReadContractIncludesForUpdate() {
        InterviewReplayMapper mapper =
                mock(InterviewReplayMapper.class, CALLS_REAL_METHODS);
        doReturn(replay(46L, 146L, "CREATED"))
                .when(mapper)
                .selectOne(any(Wrapper.class));

        mapper.selectActiveByIdempotencyKeyForUpdate(10L, "token-1");

        ArgumentCaptor<Wrapper<InterviewReplay>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment()
                .toUpperCase()
                .contains("FOR UPDATE"));
    }

    private InterviewReplay replay(Long id, Long targetSessionId, String status) {
        InterviewReplay replay = new InterviewReplay();
        replay.setId(id);
        replay.setUserId(10L);
        replay.setSourceSessionId(100L);
        replay.setSourceReportId(88L);
        replay.setTargetSessionId(targetSessionId);
        replay.setStatus(status);
        replay.setIdempotencyKey("token-1");
        return replay;
    }

    private InterviewServiceImpl.InterviewClonePreparation clonePreparation() {
        InterviewSession source = new InterviewSession();
        source.setId(100L);
        source.setUserId(10L);
        source.setDeleted(0);
        return new InterviewServiceImpl.InterviewClonePreparation(null, source);
    }

    private InterviewRemediation remediation(
            Long id, Long targetSessionId, String status) {
        InterviewRemediation remediation = new InterviewRemediation();
        remediation.setId(id);
        remediation.setUserId(10L);
        remediation.setSourceReportId(88L);
        remediation.setSourceSessionId(100L);
        remediation.setTargetSessionId(targetSessionId);
        remediation.setSourceRequirementIds("[7,9]");
        remediation.setPracticePurpose("补强缓存一致性追问");
        remediation.setRemediationStrength("NORMAL");
        remediation.setStatus(status);
        remediation.setIdempotencyKey("token-2");
        return remediation;
    }
}
