package com.codecoachai.ai.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeItem;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentPlanPreviewPersistenceService {

    private final AgentReviewMapper reviewMapper;
    private final AgentReviewPlanSuggestionMapper suggestionMapper;
    private final AgentPlanChangeSetMapper changeSetMapper;
    private final AgentPlanChangeItemMapper changeItemMapper;
    private final AgentRunMapper runMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentWeekPlanMapper weekPlanMapper;
    private final AgentWeekPlanItemMapper weekPlanItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public PersistedPreview persist(PreviewDraft draft) {
        AgentPlanChangeSet requested = draft.changeSet();
        AgentPlanChangeSet existing = changeSetMapper.selectByPreviewRequestKey(
                requested.getUserId(), requested.getPreviewRequestKeyHash());
        if (existing != null) {
            if (!Objects.equals(existing.getPreviewPayloadHash(), requested.getPreviewPayloadHash())) {
                throw stale(AgentErrorCode.IDEMPOTENCY_KEY_REUSED + "：同一幂等键不能用于不同的预览请求。");
            }
            return new PersistedPreview(existing, loadItems(existing));
        }

        AgentReview review = reviewMapper.selectOwnedForUpdate(requested.getUserId(), requested.getReviewId());
        if (review == null
                || !Objects.equals(review.getReviewVersion(), requested.getReviewVersion())
                || !Objects.equals(review.getSourceSnapshotHash(), requested.getSourceSnapshotHash())) {
            throw stale("预览计算期间来源复盘已经变化。");
        }
        List<AgentReviewPlanSuggestion> suggestions = suggestionMapper.selectList(
                new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                        .eq(AgentReviewPlanSuggestion::getUserId, requested.getUserId())
                        .eq(AgentReviewPlanSuggestion::getReviewId, requested.getReviewId())
                        .eq(AgentReviewPlanSuggestion::getReviewVersion, requested.getReviewVersion())
                        .in(AgentReviewPlanSuggestion::getId, draft.suggestionIds())
                        .eq(AgentReviewPlanSuggestion::getDecisionStatus, "ACCEPTED")
                        .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                        .orderByAsc(AgentReviewPlanSuggestion::getId)
                        .last("FOR UPDATE"));
        if (suggestions.size() != draft.suggestionIds().size()
                || !Objects.equals(requested.getSelectionHash(),
                AgentAdaptivePlanHashUtils.selectionHash(suggestions))) {
            throw stale("预览计算期间建议决策已经变化。");
        }

        AgentRun run = latestRunForUpdate(requested);
        List<AgentTask> tasks = baselineTasksForUpdate(requested, draft.baselineTaskIds());
        AgentWeekPlan weekPlan = currentWeekPlanForUpdate(requested);
        List<AgentWeekPlanItem> weekItems = weekPlan == null ? List.of()
                : currentWeekItems(requested.getUserId(), weekPlan.getId());
        if (!Objects.equals(requested.getBaseDailyRunId(), run == null ? null : run.getId())
                || !Objects.equals(requested.getBaseDailyStatus(), run == null ? null : run.getStatus())
                || !Objects.equals(requested.getBaseDailyTaskHash(),
                AgentAdaptivePlanHashUtils.taskBaselineHash(tasks))
                || !Objects.equals(requested.getBaseWeekPlanId(), weekPlan == null ? null : weekPlan.getId())
                || !Objects.equals(requested.getBaseWeekSnapshotVersion(),
                weekPlan == null ? null : weekPlan.getSnapshotVersion())
                || !Objects.equals(requested.getBaseWeekItemHash(),
                AgentAdaptivePlanHashUtils.weekItemBaselineHash(weekItems))) {
            throw stale("预览计算期间日计划或周计划基线已经变化。");
        }

        try {
            changeSetMapper.insert(requested);
            for (AgentPlanChangeItem item : draft.items()) {
                item.setChangeSetId(requested.getId());
                changeItemMapper.insert(item);
            }
            return new PersistedPreview(requested, draft.items());
        } catch (DuplicateKeyException ex) {
            AgentPlanChangeSet concurrent = changeSetMapper.selectByPreviewRequestKey(
                    requested.getUserId(), requested.getPreviewRequestKeyHash());
            if (concurrent != null
                    && Objects.equals(concurrent.getPreviewPayloadHash(), requested.getPreviewPayloadHash())) {
                return new PersistedPreview(concurrent, loadItems(concurrent));
            }
            throw stale(AgentErrorCode.IDEMPOTENCY_KEY_REUSED + "：同一幂等键不能用于不同的预览请求。");
        }
    }

    private AgentRun latestRunForUpdate(AgentPlanChangeSet changeSet) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, changeSet.getUserId())
                .eq(AgentRun::getAgentType, "JOB_COACH")
                .eq(AgentRun::getPlanDate, changeSet.getTargetDate())
                .in(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name(), AgentRunStatusEnum.SUCCESS.name(),
                        AgentRunStatusEnum.FAILED.name())
                .eq(AgentRun::getDeleted, 0)
                .orderByDesc(AgentRun::getCreatedAt)
                .last("LIMIT 1 FOR UPDATE");
        if (changeSet.getTargetJobId() == null) {
            query.isNull(AgentRun::getTargetJobId);
        } else {
            query.eq(AgentRun::getTargetJobId, changeSet.getTargetJobId());
        }
        List<AgentRun> runs = runMapper.selectList(query);
        return runs == null || runs.isEmpty() ? null : runs.get(0);
    }

    private List<AgentTask> baselineTasksForUpdate(AgentPlanChangeSet changeSet,
                                                   List<Long> baselineTaskIds) {
        Set<Long> sourceIds = baselineTaskIds == null ? Set.of() : baselineTaskIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LambdaQueryWrapper<AgentTask> query = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, changeSet.getUserId())
                .eq(changeSet.getTargetJobId() != null, AgentTask::getTargetJobId, changeSet.getTargetJobId())
                .eq(AgentTask::getDeleted, 0)
                .and(wrapper -> {
                    wrapper.eq(AgentTask::getDueDate, changeSet.getTargetDate());
                    if (!sourceIds.isEmpty()) {
                        wrapper.or().in(AgentTask::getId, sourceIds);
                    }
                })
                .orderByAsc(AgentTask::getId)
                .last("FOR UPDATE");
        return taskMapper.selectList(query);
    }

    private AgentWeekPlan currentWeekPlanForUpdate(AgentPlanChangeSet changeSet) {
        LocalDate weekStart = changeSet.getTargetDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return weekPlanMapper.selectOne(new LambdaQueryWrapper<AgentWeekPlan>()
                .eq(AgentWeekPlan::getUserId, changeSet.getUserId())
                .eq(AgentWeekPlan::getTargetScopeKey, changeSet.getTargetScopeKey())
                .eq(AgentWeekPlan::getWeekStartDate, weekStart)
                .eq(AgentWeekPlan::getDeleted, 0)
                .orderByDesc(AgentWeekPlan::getUpdatedAt)
                .last("LIMIT 1 FOR UPDATE"));
    }

    private List<AgentWeekPlanItem> currentWeekItems(Long userId, Long weekPlanId) {
        return weekPlanItemMapper.selectList(new LambdaQueryWrapper<AgentWeekPlanItem>()
                .eq(AgentWeekPlanItem::getUserId, userId)
                .eq(AgentWeekPlanItem::getWeekPlanId, weekPlanId)
                .eq(AgentWeekPlanItem::getDeleted, 0)
                .orderByAsc(AgentWeekPlanItem::getId));
    }

    private List<AgentPlanChangeItem> loadItems(AgentPlanChangeSet changeSet) {
        return changeItemMapper.selectList(new LambdaQueryWrapper<AgentPlanChangeItem>()
                .eq(AgentPlanChangeItem::getUserId, changeSet.getUserId())
                .eq(AgentPlanChangeItem::getChangeSetId, changeSet.getId())
                .eq(AgentPlanChangeItem::getDeleted, 0)
                .orderByAsc(AgentPlanChangeItem::getId));
    }

    private AgentPlanChangeConflictException stale(String message) {
        String prefix = message.startsWith(AgentErrorCode.IDEMPOTENCY_KEY_REUSED)
                ? "" : AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE + "：";
        return new AgentPlanChangeConflictException(prefix + message);
    }

    public record PreviewDraft(
            AgentPlanChangeSet changeSet,
            List<AgentPlanChangeItem> items,
            List<Long> suggestionIds,
            List<Long> baselineTaskIds) {
    }

    public record PersistedPreview(
            AgentPlanChangeSet changeSet,
            List<AgentPlanChangeItem> items) {
    }
}
