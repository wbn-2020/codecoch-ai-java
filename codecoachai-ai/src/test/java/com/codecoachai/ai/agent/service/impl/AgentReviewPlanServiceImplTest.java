package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.dto.AgentReviewPlanDecisionDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewPlanDecisionItemDTO;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanDecisionRequest;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.mapper.AgentPlanChangeItemMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanChangeSetMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewPlanDecisionRequestMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewPlanSuggestionMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanItemMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanMapper;
import com.codecoachai.ai.agent.service.AgentPlanChangeApplyService;
import com.codecoachai.ai.agent.service.AgentPlanPreviewPersistenceService;
import com.codecoachai.ai.agent.service.AgentPlanPreviewPlanner;
import com.codecoachai.ai.agent.service.AgentPlanSourceAdapter;
import com.codecoachai.ai.agent.service.AgentReviewPlanService;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeJsonCodec;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AgentReviewPlanServiceImplTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(AgentReviewPlanSuggestion.class);
        initTableInfo(AgentPlanChangeSet.class);
    }

    @Mock
    private AgentReviewMapper reviewMapper;
    @Mock
    private AgentReviewPlanSuggestionMapper suggestionMapper;
    @Mock
    private AgentReviewPlanDecisionRequestMapper decisionRequestMapper;
    @Mock
    private AgentPlanChangeSetMapper changeSetMapper;
    @Mock
    private AgentPlanChangeItemMapper changeItemMapper;
    @Mock
    private AgentTaskMapper taskMapper;
    @Mock
    private AgentRunMapper runMapper;
    @Mock
    private AgentWeekPlanMapper weekPlanMapper;
    @Mock
    private AgentWeekPlanItemMapper weekPlanItemMapper;
    @Mock
    private AgentPlanPreviewPlanner previewPlanner;
    @Mock
    private AgentPlanPreviewPersistenceService previewPersistenceService;
    @Mock
    private AgentPlanChangeApplyService applyService;

    @Test
    void sameDecisionRequestReplaysWithoutSecondWrite() {
        AgentReview review = review(88L, 10L, 2, "snapshot-a");
        AgentReviewPlanSuggestion suggestion = suggestion(301L, 10L, 88L, 2);
        Map<String, AgentReviewPlanDecisionRequest> requests = new HashMap<>();
        stubDecisionRequestStore(requests);
        when(reviewMapper.selectOwnedForUpdate(10L, 88L)).thenReturn(review);
        when(reviewMapper.selectById(88L)).thenReturn(review);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(suggestion));
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        AgentReviewPlanDecisionDTO dto = decisionRequest("decision-key-001", 2, 301L, "ACCEPTED", 1);

        AgentReviewPlanServiceImpl service = service();
        service.decide(10L, 88L, dto);
        service.decide(10L, 88L, dto);

        assertEquals("ACCEPTED", suggestion.getDecisionStatus());
        assertEquals(2, suggestion.getDecisionVersion());
        verify(suggestionMapper, times(1)).update(any(), any());
        verify(reviewMapper, times(1)).selectOwnedForUpdate(10L, 88L);
        verify(reviewMapper, times(1)).selectById(88L);
    }

    @Test
    void sameDecisionKeyWithDifferentPayloadReturnsConflict() {
        AgentReview review = review(88L, 10L, 2, "snapshot-a");
        AgentReviewPlanSuggestion suggestion = suggestion(301L, 10L, 88L, 2);
        Map<String, AgentReviewPlanDecisionRequest> requests = new HashMap<>();
        stubDecisionRequestStore(requests);
        when(reviewMapper.selectOwnedForUpdate(10L, 88L)).thenReturn(review);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(suggestion));
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        AgentReviewPlanServiceImpl service = service();
        service.decide(10L, 88L,
                decisionRequest("decision-key-002", 2, 301L, "ACCEPTED", 1));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.decide(
                10L,
                88L,
                decisionRequest("decision-key-002", 2, 301L, "IGNORED", 1)));

        assertTrue(exception.getMessage().contains("IDEMPOTENCY_KEY_REUSED"));
        verify(suggestionMapper, times(1)).update(any(), any());
        verify(reviewMapper, times(1)).selectOwnedForUpdate(10L, 88L);
    }

    @Test
    void sameDecisionKeyIsIsolatedAcrossUsers() {
        AgentReview firstReview = review(88L, 10L, 2, "snapshot-a");
        AgentReview secondReview = review(99L, 20L, 3, "snapshot-b");
        AgentReviewPlanSuggestion firstSuggestion = suggestion(301L, 10L, 88L, 2);
        AgentReviewPlanSuggestion secondSuggestion = suggestion(401L, 20L, 99L, 3);
        Map<String, AgentReviewPlanDecisionRequest> requests = new HashMap<>();
        stubDecisionRequestStore(requests);
        when(reviewMapper.selectOwnedForUpdate(10L, 88L)).thenReturn(firstReview);
        when(reviewMapper.selectOwnedForUpdate(20L, 99L)).thenReturn(secondReview);
        when(suggestionMapper.selectList(any())).thenReturn(
                List.of(firstSuggestion),
                List.of(firstSuggestion),
                List.of(secondSuggestion),
                List.of(secondSuggestion));
        when(suggestionMapper.update(any(), any())).thenReturn(1);

        AgentReviewPlanServiceImpl service = service();
        service.decide(10L, 88L,
                decisionRequest("shared-decision-key", 2, 301L, "ACCEPTED", 1));
        service.decide(20L, 99L,
                decisionRequest("shared-decision-key", 3, 401L, "ACCEPTED", 1));

        assertEquals(2, requests.size());
        ArgumentCaptor<String> keyHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(decisionRequestMapper, times(2)).insertIdempotencyRequest(
                anyLong(), anyLong(), keyHashCaptor.capture(), anyString(), anyString());
        assertEquals(keyHashCaptor.getAllValues().get(0), keyHashCaptor.getAllValues().get(1));
        verify(suggestionMapper, times(2)).update(any(), any());
    }

    @Test
    void lockedReviewWithSameSnapshotSkipsUpdateAndSuggestionMaterialization() {
        AgentReview current = review(88L, 10L, 4, "snapshot-a");
        AgentReview candidate = review(null, 10L, 99, "snapshot-a");
        when(reviewMapper.selectDailyForUpdate(10L, candidate.getReviewDate(), "JOB:11"))
                .thenReturn(current);

        AgentReview persisted = service().persistReviewWithSuggestions(candidate, List.of(), List.of());

        assertSame(current, persisted);
        verify(reviewMapper, never()).updateById(any(AgentReview.class));
        verify(suggestionMapper, never()).update(any(), any());
        verify(suggestionMapper, never()).insert(any(AgentReviewPlanSuggestion.class));
    }

    @Test
    void dailyReviewClaimReturnsReplayForCompletedMatchingSnapshot() {
        AgentReview current = review(88L, 10L, 4, "snapshot-a");
        current.setIdempotencyKey("DAILY:10:2026-07-18:11");
        AgentReview candidate = review(null, 10L, 1, "snapshot-a");
        candidate.setIdempotencyKey(current.getIdempotencyKey());
        when(reviewMapper.selectDailyForUpdate(10L, candidate.getReviewDate(), "JOB:11"))
                .thenReturn(current);

        AgentReviewPlanService.ReviewGenerationClaim claim = service().claimDailyReview(candidate);

        assertFalse(claim.shouldGenerate());
        assertFalse(claim.newlyClaimed());
        assertSame(current, claim.current());
        verify(reviewMapper).insertDailyGenerationClaim(
                10L, 11L, candidate.getReviewDate(), candidate.getIdempotencyKey(),
                "JOB:11", "snapshot-a");
    }

    @Test
    void reviewVersionAlwaysAdvancesFromLockedRow() {
        AgentReview firstLocked = review(88L, 10L, 4, "snapshot-a");
        AgentReview secondLocked = review(88L, 10L, 7, "snapshot-b");
        AgentReview firstCandidate = review(null, 10L, 99, "snapshot-b");
        AgentReview secondCandidate = review(null, 10L, 1, "snapshot-c");
        when(reviewMapper.selectDailyForUpdate(eq(10L), any(LocalDate.class), eq("JOB:11")))
                .thenReturn(firstLocked, secondLocked);
        when(reviewMapper.updateById(any(AgentReview.class))).thenReturn(1);
        when(suggestionMapper.selectCount(any())).thenReturn(1L);

        AgentReviewPlanServiceImpl service = service();
        service.persistReviewWithSuggestions(firstCandidate, List.of(), List.of());
        service.persistReviewWithSuggestions(secondCandidate, List.of(), List.of());

        ArgumentCaptor<AgentReview> reviewCaptor = ArgumentCaptor.forClass(AgentReview.class);
        verify(reviewMapper, times(2)).updateById(reviewCaptor.capture());
        assertEquals(5, reviewCaptor.getAllValues().get(0).getReviewVersion());
        assertEquals(8, reviewCaptor.getAllValues().get(1).getReviewVersion());
        verify(suggestionMapper, times(2)).selectCount(any());
    }

    private void stubDecisionRequestStore(Map<String, AgentReviewPlanDecisionRequest> requests) {
        when(decisionRequestMapper.insertIdempotencyRequest(
                anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    Long userId = invocation.getArgument(0);
                    Long reviewId = invocation.getArgument(1);
                    String requestKeyHash = invocation.getArgument(2);
                    String payloadHash = invocation.getArgument(3);
                    String requestId = invocation.getArgument(4);
                    String storageKey = userId + ":" + requestKeyHash;
                    if (requests.containsKey(storageKey)) {
                        throw new DuplicateKeyException("duplicate decision request");
                    }
                    AgentReviewPlanDecisionRequest request = new AgentReviewPlanDecisionRequest();
                    request.setId((long) requests.size() + 1);
                    request.setUserId(userId);
                    request.setReviewId(reviewId);
                    request.setDecisionRequestKeyHash(requestKeyHash);
                    request.setDecisionPayloadHash(payloadHash);
                    request.setRequestId(requestId);
                    request.setDeleted(0);
                    requests.put(storageKey, request);
                    return 1;
                });
        when(decisionRequestMapper.selectByUserAndKeyForUpdate(anyLong(), anyString()))
                .thenAnswer(invocation -> requests.get(
                        invocation.getArgument(0) + ":" + invocation.getArgument(1)));
    }

    private AgentReviewPlanServiceImpl service() {
        return new AgentReviewPlanServiceImpl(
                reviewMapper,
                suggestionMapper,
                decisionRequestMapper,
                changeSetMapper,
                changeItemMapper,
                taskMapper,
                runMapper,
                weekPlanMapper,
                weekPlanItemMapper,
                previewPlanner,
                new AgentPlanSourceAdapter(),
                previewPersistenceService,
                applyService,
                new AgentPlanChangeJsonCodec(new ObjectMapper().findAndRegisterModules()),
                new AgentBusinessTimeProvider(Clock.fixed(
                        Instant.parse("2026-07-20T04:00:00Z"),
                        ZoneId.of("Asia/Shanghai"))));
    }

    private AgentReview review(Long id, Long userId, Integer version, String snapshotHash) {
        AgentReview review = new AgentReview();
        review.setId(id);
        review.setUserId(userId);
        review.setTargetJobId(11L);
        review.setReviewDate(LocalDate.of(2026, 7, 18));
        review.setReviewType("DAILY");
        review.setTargetScopeKey("JOB:11");
        review.setReviewVersion(version);
        review.setSourceSnapshotHash(snapshotHash);
        review.setConfidenceLevel("HIGH");
        review.setFallback(false);
        review.setDeleted(0);
        return review;
    }

    private AgentReviewPlanSuggestion suggestion(Long id, Long userId, Long reviewId, Integer reviewVersion) {
        AgentReviewPlanSuggestion suggestion = new AgentReviewPlanSuggestion();
        suggestion.setId(id);
        suggestion.setUserId(userId);
        suggestion.setReviewId(reviewId);
        suggestion.setReviewVersion(reviewVersion);
        suggestion.setSuggestionFingerprint("");
        suggestion.setTitle("继续完成任务");
        suggestion.setContent("保留未完成任务");
        suggestion.setIntentType("CARRY_OVER");
        suggestion.setTargetScope("NEXT_DAY");
        suggestion.setDecisionStatus("PENDING");
        suggestion.setDecisionVersion(1);
        suggestion.setConfidenceLevel("HIGH");
        suggestion.setFallback(false);
        suggestion.setDeleted(0);
        return suggestion;
    }

    private AgentReviewPlanDecisionDTO decisionRequest(String key,
                                                       Integer reviewVersion,
                                                       Long suggestionId,
                                                       String decision,
                                                       Integer decisionVersion) {
        AgentReviewPlanDecisionItemDTO item = new AgentReviewPlanDecisionItemDTO();
        item.setSuggestionId(suggestionId);
        item.setDecision(decision);
        item.setExpectedDecisionVersion(decisionVersion);
        item.setReason("用户确认");
        AgentReviewPlanDecisionDTO dto = new AgentReviewPlanDecisionDTO();
        dto.setRequestId("request-" + suggestionId);
        dto.setIdempotencyKey(key);
        dto.setExpectedReviewVersion(reviewVersion);
        dto.setDecisions(List.of(item));
        return dto;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
