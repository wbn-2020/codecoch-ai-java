package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ResumeSuggestionBatchAcceptDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionCreateDTO;
import com.codecoachai.resume.domain.dto.ResumeSuggestionDecisionDTO;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeSuggestion;
import com.codecoachai.resume.domain.entity.ResumeSuggestionDecision;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.vo.ResumeSuggestionVO;
import com.codecoachai.resume.mapper.ResumeSuggestionDecisionMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionMapper;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeSuggestionServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private ResumeSuggestionMapper suggestionMapper;
    @Mock
    private ResumeSuggestionDecisionMapper decisionMapper;
    @Mock
    private ResumeVersionSnapshotManager snapshotManager;
    @Mock
    private ResumeSearchSyncOutboxService searchSyncOutboxService;

    private ResumeSuggestionServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(ResumeSuggestion.class);
        init(ResumeSuggestionDecision.class);
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("resume-user").build());
        service = new ResumeSuggestionServiceImpl(
                suggestionMapper, decisionMapper, snapshotManager, searchSyncOutboxService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createRejectsStaleSourceAnchorWithDedicatedCode() {
        ResumeVersion source = version(2L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Current text");
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(source);
        when(snapshotManager.readSnapshot(source)).thenReturn(snapshot);
        when(snapshotManager.sectionText(snapshot, "summary")).thenReturn("Current text");

        ResumeSuggestionCreateDTO dto = new ResumeSuggestionCreateDTO();
        dto.setSourceResumeVersionId(2L);
        dto.setSectionKey("summary");
        dto.setAnchorStart(0);
        dto.setAnchorEnd(8);
        dto.setOriginalText("Previous");
        dto.setSuggestedText("Improved");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(dto));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
    }

    @Test
    void decideRejectsIdempotencyKeyReusedForAnotherSuggestionAsRelationConflict() {
        ResumeSuggestionDecision existing = new ResumeSuggestionDecision();
        existing.setSuggestionId(8L);
        existing.setDecisionType("ACCEPT");
        when(decisionMapper.selectOne(any())).thenReturn(existing);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("ACCEPT");
        dto.setIdempotencyKey("shared-key");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.decide(9L, dto));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
    }

    @Test
    void decideRejectsIdempotencyPayloadMismatchAsRelationConflict() {
        ResumeSuggestionDecision existing = new ResumeSuggestionDecision();
        existing.setSuggestionId(9L);
        existing.setDecisionType("REJECT");
        when(decisionMapper.selectOne(any())).thenReturn(existing);
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion());

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("ACCEPT");
        dto.setIdempotencyKey("payload-key");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.decide(9L, dto));

        assertEquals(ErrorCode.RESOURCE_RELATION_CONFLICT.getCode(), exception.getCode());
    }

    @Test
    void detailReportsMissingSuggestionWithNotFoundCode() {
        when(suggestionMapper.selectOne(any())).thenReturn(null);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.detail(404L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void listAllowsOmittedStatusFilter() {
        when(suggestionMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), service.list(17L, null));
    }

    @Test
    void acceptCreatesPatchedVersionAndDecisionEvent() {
        ResumeSuggestion suggestion = suggestion();
        ResumeVersion source = version(2L);
        ResumeVersion accepted = version(3L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built stable APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(source);
        when(snapshotManager.readSnapshot(source)).thenReturn(snapshot);
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 2L, snapshot, "SUGGESTION_ACCEPT", 9L, "Suggestion #9"))
                .thenReturn(accepted);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("ACCEPT");
        dto.setIdempotencyKey("accept-9");
        ResumeSuggestionVO result = service.decide(9L, dto);

        assertEquals("ACCEPTED", result.getStatus());
        assertEquals(3L, result.getAppliedResumeVersionId());
        verify(snapshotManager).replaceSectionText(snapshot, "summary", 0, 18,
                "Built stable APIs.", "Built resilient APIs.");
        ArgumentCaptor<ResumeSuggestionDecision> decision = ArgumentCaptor.forClass(ResumeSuggestionDecision.class);
        verify(decisionMapper).insert(decision.capture());
        assertEquals("ACCEPT", decision.getValue().getDecisionType());
        assertEquals(3L, decision.getValue().getResultResumeVersionId());
    }

    @Test
    void acceptEditedTextPersistsExactAcceptedContentForUndo() {
        ResumeSuggestion suggestion = suggestion();
        ResumeVersion source = version(2L);
        ResumeVersion accepted = version(3L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built stable APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(source);
        when(snapshotManager.readSnapshot(source)).thenReturn(snapshot);
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 2L, snapshot, "SUGGESTION_ACCEPT", 9L, "Suggestion #9"))
                .thenReturn(accepted);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("ACCEPT");
        dto.setEditedText("Built secure and resilient APIs.");
        dto.setIdempotencyKey("accept-edited-9");

        ResumeSuggestionVO result = service.decide(9L, dto);

        assertEquals("Built secure and resilient APIs.", result.getAcceptedText());
        verify(snapshotManager).replaceSectionText(snapshot, "summary", 0, 18,
                "Built stable APIs.", "Built secure and resilient APIs.");
    }

    @Test
    void acceptRejectsWhenSuggestionSourceIsNoLongerCurrent() {
        ResumeSuggestion suggestion = suggestion();
        ResumeVersion source = version(2L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built stable APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(suggestion);
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(source);
        when(snapshotManager.readSnapshot(source)).thenReturn(snapshot);
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 2L, snapshot, "SUGGESTION_ACCEPT", 9L, "Suggestion #9"))
                .thenThrow(new BusinessException(
                        ErrorCode.STALE_SOURCE_VERSION,
                        "Resume has a newer current version"));

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("ACCEPT");
        dto.setIdempotencyKey("accept-stale-source");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.decide(9L, dto));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        verify(suggestionMapper, never()).update(any(), any());
        verify(decisionMapper, never()).insert(any(ResumeSuggestionDecision.class));
        verify(searchSyncOutboxService, never()).enqueue(any(), any(), any());
    }

    @Test
    void batchAcceptCreatesOneVersionForTwoLowRiskSuggestions() {
        ResumeSuggestion first = suggestion(9L, 0, 5, "Built", "Created");
        ResumeSuggestion second = suggestion(10L, 6, 12, "stable", "robust");
        ResumeVersion source = version(2L);
        ResumeVersion accepted = version(3L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built stable APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of());
        when(suggestionMapper.selectList(any())).thenReturn(List.of(first, second));
        when(suggestionMapper.selectOne(any())).thenAnswer(invocation -> first);
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(source);
        when(snapshotManager.readSnapshot(source)).thenReturn(snapshot);
        when(snapshotManager.sectionText(snapshot, "summary"))
                .thenReturn("Built stable APIs.", "Built stable APIs.");
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 2L, snapshot, "SUGGESTION_BATCH_ACCEPT", 2L,
                "Batch accept 2 suggestions")).thenReturn(accepted);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionBatchAcceptDTO dto = new ResumeSuggestionBatchAcceptDTO();
        dto.setSuggestionIds(List.of(9L, 10L));
        dto.setIdempotencyKey("batch-accept");

        List<ResumeSuggestionVO> result = service.acceptLowRiskBatch(dto);

        assertEquals(2, result.size());
        verify(snapshotManager).insertAndApplyIfCurrent(
                resume, 2L, snapshot, "SUGGESTION_BATCH_ACCEPT", 2L,
                "Batch accept 2 suggestions");
        ArgumentCaptor<ResumeSuggestionDecision> decisions =
                ArgumentCaptor.forClass(ResumeSuggestionDecision.class);
        verify(decisionMapper, org.mockito.Mockito.times(2)).insert(decisions.capture());
        assertEquals(List.of(3L, 3L), decisions.getAllValues().stream()
                .map(ResumeSuggestionDecision::getResultResumeVersionId).toList());
    }

    @Test
    void batchAcceptGuardStaleHasNoSuggestionDecisionOrOutboxWrites() {
        ResumeSuggestion first = suggestion(9L, 0, 5, "Built", "Created");
        ResumeSuggestion second = suggestion(10L, 6, 12, "stable", "robust");
        ResumeVersion source = version(2L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built stable APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(suggestionMapper.selectList(any())).thenReturn(List.of(first, second));
        when(snapshotManager.ownedVersion(2L, USER_ID)).thenReturn(source);
        when(snapshotManager.readSnapshot(source)).thenReturn(snapshot);
        when(snapshotManager.sectionText(snapshot, "summary"))
                .thenReturn("Built stable APIs.", "Built stable APIs.");
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 2L, snapshot, "SUGGESTION_BATCH_ACCEPT", 2L,
                "Batch accept 2 suggestions"))
                .thenThrow(new BusinessException(
                        ErrorCode.STALE_SOURCE_VERSION,
                        "Resume content no longer matches the current version"));

        ResumeSuggestionBatchAcceptDTO dto = new ResumeSuggestionBatchAcceptDTO();
        dto.setSuggestionIds(List.of(9L, 10L));
        dto.setIdempotencyKey("batch-stale");

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.acceptLowRiskBatch(dto));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        verify(suggestionMapper, never()).update(any(), any());
        verify(decisionMapper, never()).insert(any(ResumeSuggestionDecision.class));
        verify(searchSyncOutboxService, never()).enqueue(any(), any(), any());
    }

    @Test
    void undoBatchSuggestionAdjustsAnchorForEarlierLengthChanges() {
        ResumeSuggestion earlier = suggestion(9L, 0, 5, "Built", "Created");
        earlier.setStatus("ACCEPTED");
        earlier.setAcceptedText("Created");
        earlier.setAppliedResumeVersionId(3L);
        ResumeSuggestion target = suggestion(10L, 6, 12, "stable", "robust");
        target.setStatus("ACCEPTED");
        target.setAcceptedText("robust");
        target.setAppliedResumeVersionId(3L);
        ResumeVersion applied = version(3L);
        ResumeVersion undone = version(4L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Created robust APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(target);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(earlier, target));
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(snapshotManager.ownedVersion(3L, USER_ID)).thenReturn(applied);
        when(snapshotManager.readSnapshot(applied)).thenReturn(snapshot);
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 3L, snapshot, "SUGGESTION_UNDO", 10L, "Undo suggestion #10"))
                .thenReturn(undone);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("UNDO");
        dto.setIdempotencyKey("undo-batch-10");

        service.decide(10L, dto);

        verify(snapshotManager).replaceSectionText(snapshot, "summary", 8, 14, "robust", "stable");
    }

    @Test
    void undoLocksResumeBeforeSelectingLatestBatchSnapshot() {
        ResumeSuggestion target = suggestion(10L, 6, 12, "stable", "robust");
        target.setStatus("ACCEPTED");
        target.setAcceptedText("robust");
        target.setAppliedResumeVersionId(3L);
        ResumeVersion applied = version(3L);
        ResumeVersion undone = version(4L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built robust APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(target);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(target));
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.ownedVersion(3L, USER_ID)).thenReturn(applied);
        when(snapshotManager.readSnapshot(applied)).thenReturn(snapshot);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 3L, snapshot, "SUGGESTION_UNDO", 10L, "Undo suggestion #10"))
                .thenReturn(undone);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("UNDO");
        dto.setIdempotencyKey("undo-lock-before-snapshot");

        service.decide(10L, dto);

        InOrder order = inOrder(snapshotManager, suggestionMapper);
        order.verify(snapshotManager).ownedResume(1L, USER_ID);
        order.verify(snapshotManager).lockOwnedResume(resume);
        order.verify(suggestionMapper).selectList(any());
        order.verify(snapshotManager).ownedVersion(3L, USER_ID);
        order.verify(snapshotManager).readSnapshot(applied);
    }

    @Test
    void undoRejectsWhenIndependentVersionSupersededAppliedBatchVersion() {
        ResumeSuggestion target = suggestion(10L, 6, 12, "stable", "robust");
        target.setStatus("ACCEPTED");
        target.setAcceptedText("robust");
        target.setAppliedResumeVersionId(3L);
        ResumeVersion applied = version(3L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built robust APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(target);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(target), List.of(target));
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.ownedVersion(3L, USER_ID)).thenReturn(applied);
        when(snapshotManager.readSnapshot(applied)).thenReturn(snapshot);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 3L, snapshot, "SUGGESTION_UNDO", 10L, "Undo suggestion #10"))
                .thenThrow(new BusinessException(
                        ErrorCode.STALE_SOURCE_VERSION,
                        "Resume has a newer current version"));

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("UNDO");
        dto.setIdempotencyKey("undo-after-independent-version");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.decide(10L, dto));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        verify(suggestionMapper, never()).update(any(), any());
        verify(decisionMapper, never()).insert(any(ResumeSuggestionDecision.class));
        verify(searchSyncOutboxService, never()).enqueue(any(), any(), any());
    }

    @Test
    void undoBatchSuggestionsConsecutivelyUsesLatestUndoVersionAndRestoredAnchor() {
        ResumeSuggestion earlier = suggestion(9L, 0, 5, "Built", "Created");
        earlier.setStatus("UNDONE");
        earlier.setAcceptedText("Created");
        earlier.setAppliedResumeVersionId(3L);
        earlier.setUndoResumeVersionId(4L);
        ResumeSuggestion target = suggestion(10L, 6, 12, "stable", "robust");
        target.setStatus("ACCEPTED");
        target.setAcceptedText("robust");
        target.setAppliedResumeVersionId(3L);
        ResumeVersion latestUndo = version(4L);
        ResumeVersion undone = version(5L);
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("summary", "Built robust APIs.");
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(target);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(earlier, target));
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(snapshotManager.ownedVersion(4L, USER_ID)).thenReturn(latestUndo);
        when(snapshotManager.readSnapshot(latestUndo)).thenReturn(snapshot);
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(
                resume, 4L, snapshot, "SUGGESTION_UNDO", 10L, "Undo suggestion #10"))
                .thenReturn(undone);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("UNDO");
        dto.setIdempotencyKey("undo-batch-10-after-9");

        service.decide(10L, dto);

        verify(snapshotManager).ownedVersion(4L, USER_ID);
        verify(snapshotManager).replaceSectionText(snapshot, "summary", 6, 12, "robust", "stable");
    }

    @Test
    void undoBatchSuggestionUsesLatestUndoVersionAcrossSections() {
        ResumeSuggestion summary = suggestion(9L, 0, 5, "Built", "Created systems");
        summary.setStatus("UNDONE");
        summary.setAppliedResumeVersionId(3L);
        summary.setUndoResumeVersionId(4L);
        ResumeSuggestion experience = suggestion(10L, 0, 6, "stable", "robust");
        experience.setSectionKey("experience");
        experience.setStatus("ACCEPTED");
        experience.setAppliedResumeVersionId(3L);
        experience.setAcceptedText("robust");
        ObjectNode snapshot = new ObjectMapper().createObjectNode().put("experience", "robust delivery");
        ResumeVersion undone = version(5L);

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(suggestionMapper.selectOne(any())).thenReturn(experience, experience);
        when(suggestionMapper.selectList(any()))
                .thenReturn(List.of(summary, experience), List.of(experience));
        when(snapshotManager.ownedVersion(4L, USER_ID)).thenReturn(version(4L));
        when(snapshotManager.readSnapshot(any())).thenReturn(snapshot);
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(new Resume());
        when(snapshotManager.insertAndApplyIfCurrent(any(), any(), any(), any(), any(), any())).thenReturn(undone);
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        ResumeSuggestionDecisionDTO dto = new ResumeSuggestionDecisionDTO();
        dto.setDecisionType("UNDO");
        dto.setIdempotencyKey("undo-cross-section");

        service.decide(10L, dto);

        verify(snapshotManager).ownedVersion(4L, USER_ID);
    }

    @Test
    void batchAcceptThenUndoInBothOrdersRestoresExactOriginalSnapshotText() throws Exception {
        assertBatchUndoRestoresOriginal(List.of(9L, 10L));
        org.mockito.Mockito.reset(
                suggestionMapper, decisionMapper, snapshotManager, searchSyncOutboxService);
        assertBatchUndoRestoresOriginal(List.of(10L, 9L));
    }

    private void assertBatchUndoRestoresOriginal(List<Long> undoOrder) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String originalText = "Built stable APIs.";
        ResumeSuggestion first = suggestion(9L, 0, 5, "Built", "Created systems");
        ResumeSuggestion second = suggestion(10L, 6, 12, "stable", "reliable");
        Map<Long, ResumeSuggestion> suggestions = Map.of(9L, first, 10L, second);
        Map<Long, ResumeVersion> versions = new HashMap<>();
        ResumeVersion source = version(2L);
        source.setSnapshotJson(mapper.createObjectNode().put("summary", originalText).toString());
        versions.put(2L, source);
        AtomicLong nextVersionId = new AtomicLong(3L);
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);

        Queue<ResumeSuggestion> selectedSuggestions = new ArrayDeque<>();
        selectedSuggestions.add(first);
        selectedSuggestions.add(second);
        undoOrder.forEach(id -> {
            selectedSuggestions.add(suggestions.get(id));
            selectedSuggestions.add(suggestions.get(id));
        });

        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(decisionMapper.selectList(any())).thenReturn(List.of());
        when(suggestionMapper.selectList(any())).thenReturn(List.of(first, second));
        when(suggestionMapper.selectOne(any())).thenAnswer(invocation -> selectedSuggestions.remove());
        when(suggestionMapper.update(any(), any())).thenReturn(1);
        when(snapshotManager.ownedVersion(any(), any())).thenAnswer(invocation ->
                versions.get(invocation.getArgument(0, Long.class)));
        when(snapshotManager.readSnapshot(any())).thenAnswer(invocation ->
                (ObjectNode) mapper.readTree(invocation.getArgument(0, ResumeVersion.class).getSnapshotJson()));
        when(snapshotManager.sectionText(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(0, ObjectNode.class)
                        .path(invocation.getArgument(1, String.class)).asText());
        org.mockito.Mockito.doAnswer(invocation -> {
            ObjectNode snapshot = invocation.getArgument(0);
            String sectionKey = invocation.getArgument(1);
            int start = invocation.getArgument(2);
            int end = invocation.getArgument(3);
            String expected = invocation.getArgument(4);
            String replacement = invocation.getArgument(5);
            String current = snapshot.path(sectionKey).asText();
            assertEquals(expected, current.substring(start, end));
            snapshot.put(sectionKey, current.substring(0, start) + replacement + current.substring(end));
            return null;
        }).when(snapshotManager).replaceSectionText(any(), any(), any(Integer.class), any(Integer.class), any(), any());
        when(snapshotManager.ownedResume(1L, USER_ID)).thenReturn(resume);
        when(snapshotManager.insertAndApplyIfCurrent(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            ResumeVersion version = version(nextVersionId.getAndIncrement());
            version.setSnapshotJson(invocation.getArgument(2, ObjectNode.class).toString());
            versions.put(version.getId(), version);
            return version;
        });

        ResumeSuggestionBatchAcceptDTO batch = new ResumeSuggestionBatchAcceptDTO();
        batch.setSuggestionIds(List.of(9L, 10L));
        batch.setIdempotencyKey("batch-real-snapshot-" + undoOrder);
        service.acceptLowRiskBatch(batch);

        first.setStatus("ACCEPTED");
        first.setAcceptedText(first.getSuggestedText());
        first.setAppliedResumeVersionId(3L);
        second.setStatus("ACCEPTED");
        second.setAcceptedText(second.getSuggestedText());
        second.setAppliedResumeVersionId(3L);

        for (Long suggestionId : undoOrder) {
            ResumeSuggestionDecisionDTO undo = new ResumeSuggestionDecisionDTO();
            undo.setDecisionType("UNDO");
            undo.setIdempotencyKey("undo-" + undoOrder + "-" + suggestionId);
            service.decide(suggestionId, undo);
        }

        ResumeVersion finalVersion = versions.get(5L);
        assertEquals(originalText, mapper.readTree(finalVersion.getSnapshotJson()).path("summary").asText());
    }

    private ResumeSuggestion suggestion() {
        return suggestion(9L, 0, 18, "Built stable APIs.", "Built resilient APIs.");
    }

    private ResumeSuggestion suggestion(Long id, int start, int end, String original, String suggested) {
        ResumeSuggestion suggestion = new ResumeSuggestion();
        suggestion.setId(id);
        suggestion.setUserId(USER_ID);
        suggestion.setResumeId(1L);
        suggestion.setSourceResumeVersionId(2L);
        suggestion.setSectionKey("summary");
        suggestion.setAnchorStart(start);
        suggestion.setAnchorEnd(end);
        suggestion.setOriginalText(original);
        suggestion.setSuggestedText(suggested);
        suggestion.setAnchorTextHash(com.codecoachai.resume.export.ResumeArtifactHashes.sha256(original));
        suggestion.setRiskLevel("LOW");
        suggestion.setStatus("PENDING");
        suggestion.setDecisionVersion(0);
        return suggestion;
    }

    private ResumeVersion version(Long id) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setUserId(USER_ID);
        version.setResumeId(1L);
        return version;
    }
}
