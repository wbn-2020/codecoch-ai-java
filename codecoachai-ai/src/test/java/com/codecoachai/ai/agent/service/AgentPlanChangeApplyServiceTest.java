package com.codecoachai.ai.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.context.AgentReviewPlanWeekResult;
import com.codecoachai.ai.agent.domain.dto.AgentPlanChangeConfirmDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanTaskSnapshotDTO;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeItem;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeConfirmVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeSummaryVO;
import com.codecoachai.ai.agent.mapper.AgentPlanChangeItemMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanChangeSetMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewPlanSuggestionMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanItemMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanMapper;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentPlanChangeApplyServiceTest {

    @Mock
    private AgentPlanChangeSetMapper changeSetMapper;
    @Mock
    private AgentPlanChangeItemMapper changeItemMapper;
    @Mock
    private AgentReviewMapper reviewMapper;
    @Mock
    private AgentReviewPlanSuggestionMapper suggestionMapper;
    @Mock
    private AgentRunMapper runMapper;
    @Mock
    private AgentTaskMapper taskMapper;
    @Mock
    private AgentWeekPlanMapper weekPlanMapper;
    @Mock
    private AgentWeekPlanItemMapper weekPlanItemMapper;
    @Mock
    private AgentWeekPlanService weekPlanService;

    private AgentPlanChangeJsonCodec jsonCodec;
    private AgentPlanChangeApplyService service;

    @BeforeAll
    static void initTableInfo() {
        init(AgentPlanChangeSet.class);
        init(AgentPlanChangeItem.class);
        init(AgentReview.class);
        init(AgentReviewPlanSuggestion.class);
        init(AgentRun.class);
        init(AgentTask.class);
        init(AgentWeekPlan.class);
        init(AgentWeekPlanItem.class);
    }

    @BeforeEach
    void setUp() {
        jsonCodec = new AgentPlanChangeJsonCodec(new ObjectMapper().findAndRegisterModules());
        service = new AgentPlanChangeApplyService(
                changeSetMapper,
                changeItemMapper,
                reviewMapper,
                suggestionMapper,
                runMapper,
                taskMapper,
                weekPlanMapper,
                weekPlanItemMapper,
                weekPlanService,
                jsonCodec,
                fixedTimeProvider());
    }

    @Test
    void confirmWithoutDailyPlanOnlyPersistsWaitingStateAndNeverWritesTask() {
        LocalDate targetDate = LocalDate.of(2026, 7, 19);
        AgentReviewPlanSuggestion suggestion = acceptedSuggestion();
        AgentReview review = review();
        AgentPlanChangeItem item = changeItem(targetDate);
        AgentPlanChangeSet changeSet = changeSet(targetDate, suggestion);
        AgentPlanChangeConfirmDTO dto = confirmDTO(changeSet);

        when(changeSetMapper.selectOwnedForUpdate(10L, 501L)).thenReturn(changeSet);
        when(changeItemMapper.selectByChangeSetForUpdate(10L, 501L)).thenReturn(List.of(item));
        when(reviewMapper.selectOwnedForUpdate(10L, 88L)).thenReturn(review);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(suggestion));
        when(runMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(weekPlanMapper.selectOne(any())).thenReturn(null);
        when(changeSetMapper.updateById(any(AgentPlanChangeSet.class))).thenReturn(1);
        when(changeItemMapper.updateById(any(AgentPlanChangeItem.class))).thenReturn(1);
        AgentReviewPlanWeekResult weekResult = new AgentReviewPlanWeekResult();
        weekResult.setWeekPlanId(3001L);
        weekResult.setSnapshotVersion(1);
        when(weekPlanService.recordPendingReviewChange(10L, changeSet, List.of(item))).thenReturn(weekResult);

        AgentPlanChangeConfirmVO result = service.confirm(10L, 501L, dto);

        assertEquals("CONFIRMED_WAITING_PLAN", result.getStatus());
        assertEquals(1, result.getWaitingItemCount());
        verify(taskMapper, never()).insert(any(AgentTask.class));
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void appliedConfirmReplayReturnsStoredResultWithoutApplyingAgain() {
        LocalDate targetDate = LocalDate.of(2026, 7, 20);
        AgentReviewPlanSuggestion suggestion = acceptedSuggestion();
        AgentPlanChangeSet changeSet = changeSet(targetDate, suggestion);
        AgentPlanChangeConfirmDTO dto = confirmDTO(changeSet);
        AgentPlanChangeItem item = changeItem(targetDate);
        item.setApplyStatus("APPLIED");
        item.setAppliedRunId(801L);
        item.setApplyCount(1);
        changeSet.setStatus("APPLIED");
        changeSet.setConfirmRequestKeyHash(AgentAdaptivePlanHashUtils.sha256(dto.getIdempotencyKey()));
        changeSet.setConfirmPayloadHash(confirmPayloadHash(dto));

        when(changeSetMapper.selectOwnedForUpdate(10L, 501L)).thenReturn(changeSet);
        when(changeItemMapper.selectList(any())).thenReturn(List.of(item));

        AgentPlanChangeConfirmVO result = service.confirm(10L, 501L, dto);

        assertEquals("APPLIED", result.getStatus());
        assertEquals(1, result.getAppliedItemCount());
        verify(taskMapper, never()).insert(any(AgentTask.class));
        verify(taskMapper, never()).update(any(), any());
        verify(weekPlanService, never()).rebuildAfterReviewChange(any(), any(), any());
    }

    @Test
    void reconcileSelectsPartialRetriesButNeverTerminalAppliedSets() {
        AgentRun run = successfulRun(LocalDate.of(2026, 7, 20));
        when(changeSetMapper.selectList(any())).thenReturn(List.of());

        service.reconcileConfirmedChanges(10L, run);

        ArgumentCaptor<Wrapper<AgentPlanChangeSet>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(changeSetMapper).selectList(captor.capture());
        captor.getValue().getSqlSegment();
        Map<String, Object> values = ((AbstractWrapper<?, ?, ?>) captor.getValue()).getParamNameValuePairs();
        assertTrue(values.values().stream().anyMatch(value -> containsNestedValue(value, "PARTIALLY_APPLIED")));
        assertFalse(values.values().stream().anyMatch(value -> containsNestedValue(value, "APPLIED")));
    }

    @Test
    void partiallyAppliedRetrySkipsSuccessfulItemsAndRetriesOnlyFailedItem() {
        LocalDate targetDate = LocalDate.of(2026, 7, 20);
        AgentPlanChangeSet changeSet = changeSet(targetDate, acceptedSuggestion());
        changeSet.setStatus("PARTIALLY_APPLIED");
        AgentPlanChangeItem applied = changeItem(targetDate);
        applied.setApplyStatus("APPLIED");
        applied.setApplyCount(1);
        AgentPlanChangeItem duplicate = changeItem(targetDate);
        duplicate.setId(702L);
        duplicate.setApplyStatus("SKIPPED_DUPLICATE");
        duplicate.setApplyCount(1);
        AgentPlanChangeItem failed = changeItem(targetDate);
        failed.setId(703L);
        failed.setApplyStatus("FAILED");
        failed.setApplyCount(1);
        AgentRun run = successfulRun(targetDate);

        when(changeSetMapper.selectList(any())).thenReturn(List.of(changeSet));
        when(changeItemMapper.selectByChangeSetForUpdate(10L, 501L))
                .thenReturn(List.of(applied, duplicate, failed));
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.insert(any(AgentTask.class))).thenAnswer(invocation -> {
            AgentTask task = invocation.getArgument(0);
            task.setId(9001L);
            return 1;
        });
        when(changeItemMapper.updateById(any(AgentPlanChangeItem.class))).thenReturn(1);
        when(changeSetMapper.updateById(any(AgentPlanChangeSet.class))).thenReturn(1);

        service.reconcileConfirmedChanges(10L, run);

        assertEquals("APPLIED", changeSet.getStatus());
        assertEquals("APPLIED", applied.getApplyStatus());
        assertEquals(1, applied.getApplyCount());
        assertEquals("SKIPPED_DUPLICATE", duplicate.getApplyStatus());
        assertEquals(1, duplicate.getApplyCount());
        assertEquals("APPLIED", failed.getApplyStatus());
        assertEquals(2, failed.getApplyCount());
        verify(taskMapper, times(1)).insert(any(AgentTask.class));
        verify(changeItemMapper, times(1)).updateById(failed);
    }

    private AgentPlanChangeSet changeSet(LocalDate targetDate, AgentReviewPlanSuggestion suggestion) {
        AgentPlanChangeSet changeSet = new AgentPlanChangeSet();
        changeSet.setId(501L);
        changeSet.setUserId(10L);
        changeSet.setReviewId(88L);
        changeSet.setReviewVersion(2);
        changeSet.setTargetJobId(11L);
        changeSet.setTargetScopeKey("JOB:11");
        changeSet.setTargetDate(targetDate);
        changeSet.setStatus("PREVIEW_READY");
        changeSet.setSelectionHash(AgentAdaptivePlanHashUtils.selectionHash(List.of(suggestion)));
        changeSet.setSourceSnapshotHash("source-hash");
        changeSet.setBaseDailyTaskHash(AgentAdaptivePlanHashUtils.taskBaselineHash(List.of()));
        changeSet.setBaseWeekItemHash(AgentAdaptivePlanHashUtils.weekItemBaselineHash(List.of()));
        changeSet.setPreviewVersion(1);
        changeSet.setPreviewHash("preview-hash");
        changeSet.setPreviewSummaryJson(jsonCodec.write(Map.of(
                "summary", new AgentPlanChangeSummaryVO(),
                "warnings", List.of(),
                "blockers", List.of())));
        changeSet.setExpiresAt(LocalDateTime.of(2026, 7, 20, 12, 30));
        changeSet.setLockVersion(1);
        changeSet.setDeleted(0);
        return changeSet;
    }

    private AgentPlanChangeItem changeItem(LocalDate targetDate) {
        AgentPlanTaskSnapshotDTO after = new AgentPlanTaskSnapshotDTO();
        after.setTargetJobId(11L);
        after.setTaskType("QUESTION_PRACTICE");
        after.setTitle("复盘 Java 高频题");
        after.setPriority("MEDIUM");
        after.setEstimatedMinutes(30);
        after.setRelatedSkillCode("JAVA");
        after.setStatus("TODO");
        after.setDueDate(targetDate);

        AgentPlanChangeItem item = new AgentPlanChangeItem();
        item.setId(701L);
        item.setUserId(10L);
        item.setChangeSetId(501L);
        item.setSuggestionId(301L);
        item.setChangeType("ADD_TASK");
        item.setTargetDate(targetDate);
        item.setBeforeJson("null");
        item.setAfterJson(jsonCodec.write(after));
        item.setWarningCodesJson("[]");
        item.setValidationStatus("PASS");
        item.setApplyStatus("PENDING");
        item.setApplyCount(0);
        item.setDeleted(0);
        return item;
    }

    private AgentReview review() {
        AgentReview review = new AgentReview();
        review.setId(88L);
        review.setUserId(10L);
        review.setReviewType("DAILY");
        review.setReviewVersion(2);
        review.setSourceSnapshotHash("source-hash");
        review.setDeleted(0);
        return review;
    }

    private AgentReviewPlanSuggestion acceptedSuggestion() {
        AgentReviewPlanSuggestion suggestion = new AgentReviewPlanSuggestion();
        suggestion.setId(301L);
        suggestion.setUserId(10L);
        suggestion.setReviewId(88L);
        suggestion.setReviewVersion(2);
        suggestion.setSuggestionFingerprint("fingerprint-301");
        suggestion.setDecisionStatus("ACCEPTED");
        suggestion.setDecisionVersion(1);
        suggestion.setDeleted(0);
        return suggestion;
    }

    private AgentPlanChangeConfirmDTO confirmDTO(AgentPlanChangeSet changeSet) {
        AgentPlanChangeConfirmDTO dto = new AgentPlanChangeConfirmDTO();
        dto.setIdempotencyKey("confirm-key-501");
        dto.setPreviewVersion(changeSet.getPreviewVersion());
        dto.setPreviewHash(changeSet.getPreviewHash());
        dto.setAcknowledgedWarningCodes(List.of());
        return dto;
    }

    private String confirmPayloadHash(AgentPlanChangeConfirmDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previewVersion", dto.getPreviewVersion());
        payload.put("previewHash", dto.getPreviewHash());
        payload.put("acknowledgedWarningCodes", dto.getAcknowledgedWarningCodes());
        return AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(payload));
    }

    private AgentRun successfulRun(LocalDate targetDate) {
        AgentRun run = new AgentRun();
        run.setId(801L);
        run.setUserId(10L);
        run.setAgentType("JOB_COACH");
        run.setTargetJobId(11L);
        run.setPlanDate(targetDate);
        run.setStatus("SUCCESS");
        run.setDeleted(0);
        return run;
    }

    private static boolean containsNestedValue(Object candidate, Object expected) {
        if (expected.equals(candidate)) {
            return true;
        }
        if (candidate instanceof Collection<?> collection) {
            return collection.stream().anyMatch(value -> containsNestedValue(value, expected));
        }
        if (candidate instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(value -> containsNestedValue(value, expected));
        }
        return false;
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    private AgentBusinessTimeProvider fixedTimeProvider() {
        return new AgentBusinessTimeProvider(Clock.fixed(
                Instant.parse("2026-07-20T04:00:00Z"),
                ZoneId.of("Asia/Shanghai")));
    }
}
