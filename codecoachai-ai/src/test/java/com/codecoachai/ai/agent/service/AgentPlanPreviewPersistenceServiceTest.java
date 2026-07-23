package com.codecoachai.ai.agent.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeItem;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.mapper.AgentPlanChangeItemMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanChangeSetMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentReviewPlanSuggestionMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanItemMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanMapper;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeConflictException;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentPlanPreviewPersistenceServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(AgentReviewPlanSuggestion.class);
        initTableInfo(AgentRun.class);
        initTableInfo(AgentTask.class);
        initTableInfo(AgentWeekPlan.class);
    }

    @Mock
    private AgentReviewMapper reviewMapper;
    @Mock
    private AgentReviewPlanSuggestionMapper suggestionMapper;
    @Mock
    private AgentPlanChangeSetMapper changeSetMapper;
    @Mock
    private AgentPlanChangeItemMapper changeItemMapper;
    @Mock
    private AgentRunMapper runMapper;
    @Mock
    private AgentTaskMapper taskMapper;
    @Mock
    private AgentWeekPlanMapper weekPlanMapper;
    @Mock
    private AgentWeekPlanItemMapper weekPlanItemMapper;

    @Test
    void changedDailyTaskBaselineMarksPreviewStaleWithoutInsert() {
        AgentReview review = review();
        AgentReviewPlanSuggestion suggestion = suggestion();
        AgentTask originalTask = task("MEDIUM");
        AgentTask changedTask = task("HIGH");
        AgentPlanChangeSet changeSet = changeSet(review, suggestion, originalTask);
        AgentPlanChangeItem item = new AgentPlanChangeItem();
        item.setUserId(10L);
        item.setSuggestionId(301L);
        item.setSourceTaskId(101L);

        when(changeSetMapper.selectByPreviewRequestKey(10L, "request-key-hash")).thenReturn(null);
        when(reviewMapper.selectOwnedForUpdate(10L, 88L)).thenReturn(review);
        when(suggestionMapper.selectList(any())).thenReturn(List.of(suggestion));
        when(runMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(List.of(changedTask));
        when(weekPlanMapper.selectOne(any())).thenReturn(null);

        AgentPlanChangeConflictException exception = assertThrows(
                AgentPlanChangeConflictException.class,
                () -> service().persist(new AgentPlanPreviewPersistenceService.PreviewDraft(
                        changeSet,
                        List.of(item),
                        List.of(301L),
                        List.of(101L))));

        assertTrue(exception.getMessage().contains("PLAN_CHANGE_PREVIEW_STALE"));
        verify(changeSetMapper, never()).insert(any(AgentPlanChangeSet.class));
        verify(changeItemMapper, never()).insert(any(AgentPlanChangeItem.class));
    }

    private AgentPlanPreviewPersistenceService service() {
        return new AgentPlanPreviewPersistenceService(
                reviewMapper,
                suggestionMapper,
                changeSetMapper,
                changeItemMapper,
                runMapper,
                taskMapper,
                weekPlanMapper,
                weekPlanItemMapper);
    }

    private AgentReview review() {
        AgentReview review = new AgentReview();
        review.setId(88L);
        review.setUserId(10L);
        review.setTargetJobId(11L);
        review.setTargetScopeKey("JOB:11");
        review.setReviewVersion(2);
        review.setSourceSnapshotHash("snapshot-a");
        review.setDeleted(0);
        return review;
    }

    private AgentReviewPlanSuggestion suggestion() {
        AgentReviewPlanSuggestion suggestion = new AgentReviewPlanSuggestion();
        suggestion.setId(301L);
        suggestion.setUserId(10L);
        suggestion.setReviewId(88L);
        suggestion.setReviewVersion(2);
        suggestion.setSuggestionFingerprint("fingerprint-301");
        suggestion.setDecisionStatus("ACCEPTED");
        suggestion.setDecisionVersion(2);
        suggestion.setDeleted(0);
        return suggestion;
    }

    private AgentTask task(String priority) {
        AgentTask task = new AgentTask();
        task.setId(101L);
        task.setUserId(10L);
        task.setTargetJobId(11L);
        task.setTaskType("QUESTION_PRACTICE");
        task.setStatus("TODO");
        task.setPriority(priority);
        task.setEstimatedMinutes(30);
        task.setDueDate(LocalDate.of(2026, 7, 19));
        task.setDeleted(0);
        return task;
    }

    private AgentPlanChangeSet changeSet(AgentReview review,
                                         AgentReviewPlanSuggestion suggestion,
                                         AgentTask originalTask) {
        AgentPlanChangeSet changeSet = new AgentPlanChangeSet();
        changeSet.setUserId(10L);
        changeSet.setReviewId(88L);
        changeSet.setReviewVersion(2);
        changeSet.setTargetJobId(11L);
        changeSet.setTargetScopeKey("JOB:11");
        changeSet.setTargetDate(LocalDate.of(2026, 7, 19));
        changeSet.setSelectionHash(AgentAdaptivePlanHashUtils.selectionHash(List.of(suggestion)));
        changeSet.setSourceSnapshotHash(review.getSourceSnapshotHash());
        changeSet.setBaseDailyTaskHash(AgentAdaptivePlanHashUtils.taskBaselineHash(List.of(originalTask)));
        changeSet.setBaseWeekItemHash(AgentAdaptivePlanHashUtils.weekItemBaselineHash(List.of()));
        changeSet.setPreviewRequestKeyHash("request-key-hash");
        changeSet.setPreviewPayloadHash("payload-hash");
        return changeSet;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
