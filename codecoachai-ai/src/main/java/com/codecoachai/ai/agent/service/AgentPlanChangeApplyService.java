package com.codecoachai.ai.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeConfirmVO;
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
import com.codecoachai.ai.agent.service.support.AgentPlanChangeConflictException;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeJsonCodec;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentPlanChangeApplyService {

    private static final String STATUS_PREVIEW_READY = "PREVIEW_READY";
    private static final String STATUS_STALE = "STALE";
    private static final String STATUS_WAITING = "CONFIRMED_WAITING_PLAN";
    private static final String STATUS_APPLYING = "APPLYING";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_PARTIALLY_APPLIED = "PARTIALLY_APPLIED";
    private static final String STATUS_APPLY_FAILED = "APPLY_FAILED";
    private static final String ITEM_PENDING = "PENDING";
    private static final String ITEM_WAITING = "WAITING_PLAN";
    private static final String ITEM_APPLIED = "APPLIED";
    private static final String ITEM_DUPLICATE = "SKIPPED_DUPLICATE";
    private static final String ITEM_FAILED = "FAILED";
    private static final String ORIGIN_REVIEW_CONFIRMED = "REVIEW_CONFIRMED";
    private static final List<String> OPEN_TASK_STATUSES = List.of(
            AgentTaskStatusEnum.TODO.name(),
            AgentTaskStatusEnum.DOING.name(),
            AgentTaskStatusEnum.DEFERRED.name());

    private final AgentPlanChangeSetMapper changeSetMapper;
    private final AgentPlanChangeItemMapper changeItemMapper;
    private final AgentReviewMapper reviewMapper;
    private final AgentReviewPlanSuggestionMapper suggestionMapper;
    private final AgentRunMapper runMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentWeekPlanMapper weekPlanMapper;
    private final AgentWeekPlanItemMapper weekPlanItemMapper;
    private final AgentWeekPlanService weekPlanService;
    private final AgentPlanChangeJsonCodec jsonCodec;
    private final AgentBusinessTimeProvider timeProvider;

    @Transactional(rollbackFor = Exception.class, noRollbackFor = AgentPlanChangeConflictException.class)
    public AgentPlanChangeConfirmVO confirm(Long userId,
                                            Long changeSetId,
                                            AgentPlanChangeConfirmDTO dto) {
        requireConfirmRequest(dto);
        AgentPlanChangeSet changeSet = changeSetMapper.selectOwnedForUpdate(userId, changeSetId);
        if (changeSet == null) {
            throw forbidden("计划变更集不存在或不属于当前用户。");
        }
        String requestKeyHash = AgentAdaptivePlanHashUtils.sha256(dto.getIdempotencyKey().trim());
        String payloadHash = confirmPayloadHash(dto);
        AgentPlanChangeConfirmVO replay = idempotentReplay(changeSet, requestKeyHash, payloadHash);
        if (replay != null) {
            return replay;
        }
        if (!STATUS_PREVIEW_READY.equals(changeSet.getStatus())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_ALREADY_DECIDED, "当前计划变更集不能再次确认。");
        }
        if (changeSet.getExpiresAt() == null || !changeSet.getExpiresAt().isAfter(timeProvider.now())) {
            markStaleAndThrow(changeSet, "计划差异预览已过期，请重新生成预览。");
        }
        if (!Objects.equals(changeSet.getPreviewVersion(), dto.getPreviewVersion())
                || !Objects.equals(changeSet.getPreviewHash(), dto.getPreviewHash())) {
            markStaleAndThrow(changeSet, "提交的预览版本或哈希与当前记录不一致。");
        }

        List<AgentPlanChangeItem> items = changeItemMapper.selectByChangeSetForUpdate(userId, changeSetId);
        requireConfirmableItems(changeSet, items, dto.getAcknowledgedWarningCodes());
        AgentReview review = reviewMapper.selectOwnedForUpdate(userId, changeSet.getReviewId());
        if (review == null || !Objects.equals(review.getReviewVersion(), changeSet.getReviewVersion())
                || !Objects.equals(review.getSourceSnapshotHash(), changeSet.getSourceSnapshotHash())) {
            markStaleAndThrow(changeSet, "来源复盘已经变化，请重新生成预览。");
        }
        validateSelection(changeSet, items);

        AgentRun currentRun = latestRunForUpdate(userId, changeSet.getTargetJobId(), changeSet.getTargetDate());
        List<AgentTask> baselineTasks = baselineTasksForUpdate(userId, changeSet, items);
        AgentWeekPlan weekPlan = currentWeekPlanForUpdate(userId, changeSet.getTargetJobId(),
                changeSet.getTargetDate());
        List<AgentWeekPlanItem> weekItems = weekPlan == null ? List.of()
                : currentWeekItems(userId, weekPlan.getId());
        validateBaselines(changeSet, currentRun, baselineTasks, weekPlan, weekItems);

        LocalDateTime now = timeProvider.now();
        changeSet.setConfirmRequestKeyHash(requestKeyHash);
        changeSet.setConfirmPayloadHash(payloadHash);
        changeSet.setConfirmedAt(now);
        changeSet.setFailureCode(null);
        changeSet.setFailureMessage(null);
        if (currentRun == null || !AgentRunStatusEnum.SUCCESS.name().equals(currentRun.getStatus())) {
            changeSet.setStatus(STATUS_WAITING);
            for (AgentPlanChangeItem item : items) {
                if (!ITEM_DUPLICATE.equals(item.getApplyStatus())) {
                    item.setApplyStatus(ITEM_WAITING);
                    changeItemMapper.updateById(item);
                }
            }
            updateChangeSetWithConfirmKey(changeSet, requestKeyHash, payloadHash);
            AgentReviewPlanWeekResult weekResult = weekPlanService.recordPendingReviewChange(userId, changeSet, items);
            return confirmVO(changeSet, items, null, weekResult,
                    "调整已确认，将在目标日期计划生成时按本次预览应用。");
        }

        changeSet.setStatus(STATUS_APPLYING);
        updateChangeSetWithConfirmKey(changeSet, requestKeyHash, payloadHash);
        ApplyResult applyResult = applyItems(userId, changeSet, items, currentRun, false);
        AgentReviewPlanWeekResult weekResult = weekPlanService.rebuildAfterReviewChange(userId, changeSet, items);
        attachWeekResults(items, weekResult);
        changeSet.setStatus(STATUS_APPLIED);
        changeSet.setAppliedAt(timeProvider.now());
        changeSet.setLockVersion(valueOrDefault(changeSet.getLockVersion(), 1) + 1);
        changeSetMapper.updateById(changeSet);
        return confirmVO(changeSet, items, currentRun, weekResult,
                applyResult.duplicateCount() > 0 ? "计划已写入，重复任务已按幂等规则合并。" : "计划已按确认预览写入。");
    }

    @Transactional(rollbackFor = Exception.class)
    public void reconcileConfirmedChanges(Long userId, AgentRun run) {
        if (userId == null || run == null || run.getId() == null || run.getPlanDate() == null) {
            return;
        }
        List<AgentPlanChangeSet> changeSets = waitingChangeSetsForUpdate(userId, run);
        for (AgentPlanChangeSet changeSet : changeSets) {
            List<AgentPlanChangeItem> items = changeItemMapper.selectByChangeSetForUpdate(userId, changeSet.getId());
            changeSet.setStatus(STATUS_APPLYING);
            changeSet.setFailureCode(null);
            changeSet.setFailureMessage(null);
            changeSetMapper.updateById(changeSet);
            ApplyResult result = applyItems(userId, changeSet, items, run, true);
            AgentReviewPlanWeekResult weekResult = weekPlanService.rebuildAfterReviewChange(userId, changeSet, items);
            attachWeekResults(items, weekResult);
            changeSet.setStatus(result.failedCount() > 0 ? STATUS_PARTIALLY_APPLIED : STATUS_APPLIED);
            changeSet.setAppliedAt(timeProvider.now());
            changeSet.setFailureCode(result.failedCount() > 0 ? AgentErrorCode.PLAN_CHANGE_VALIDATION_FAILED : null);
            changeSet.setFailureMessage(result.failedCount() > 0
                    ? "部分已确认调整的外部前置条件已失效，未自动替换目标任务。"
                    : null);
            changeSet.setLockVersion(valueOrDefault(changeSet.getLockVersion(), 1) + 1);
            changeSetMapper.updateById(changeSet);
        }
    }

    private ApplyResult applyItems(Long userId,
                                   AgentPlanChangeSet changeSet,
                                   List<AgentPlanChangeItem> items,
                                   AgentRun run,
                                   boolean allowPartial) {
        Map<Long, AgentTask> sourceTasks = lockSourceTasks(userId, items);
        int nextSortOrder = nextSortOrder(userId, run.getId());
        int failed = 0;
        int duplicate = 0;
        for (AgentPlanChangeItem item : items.stream().sorted(Comparator.comparing(AgentPlanChangeItem::getId)).toList()) {
            if (ITEM_APPLIED.equals(item.getApplyStatus()) || ITEM_DUPLICATE.equals(item.getApplyStatus())) {
                if (ITEM_DUPLICATE.equals(item.getApplyStatus())) {
                    duplicate++;
                }
                continue;
            }
            try {
                ApplyItemResult result = applyItem(userId, changeSet, item, run, sourceTasks, nextSortOrder);
                if (result.created()) {
                    nextSortOrder++;
                }
                if (result.duplicate()) {
                    duplicate++;
                }
            } catch (BusinessException ex) {
                if (!allowPartial) {
                    throw ex;
                }
                failed++;
                item.setApplyStatus(ITEM_FAILED);
                item.setAppliedRunId(run.getId());
                item.setApplyCount(valueOrDefault(item.getApplyCount(), 0) + 1);
                changeItemMapper.updateById(item);
            }
        }
        return new ApplyResult(failed, duplicate);
    }

    private ApplyItemResult applyItem(Long userId,
                                      AgentPlanChangeSet changeSet,
                                      AgentPlanChangeItem item,
                                      AgentRun run,
                                      Map<Long, AgentTask> sourceTasks,
                                      int sortOrder) {
        AgentPlanTaskSnapshotDTO before = jsonCodec.readTaskSnapshot(item.getBeforeJson());
        AgentPlanTaskSnapshotDTO after = jsonCodec.readTaskSnapshot(item.getAfterJson());
        boolean created = false;
        boolean duplicate = false;
        switch (item.getChangeType()) {
            case AgentPlanPreviewPlanner.CHANGE_ADD_TASK,
                    AgentPlanPreviewPlanner.CHANGE_CARRY_OVER_TASK -> {
                TaskWriteResult writeResult = createConfirmedTask(userId, changeSet, item, run, after, sortOrder);
                item.setAppliedTaskId(writeResult.task().getId());
                duplicate = writeResult.duplicate();
                created = !writeResult.duplicate();
            }
            case AgentPlanPreviewPlanner.CHANGE_REMOVE_OPEN_TASK -> {
                AgentTask source = requireOpenSourceTask(userId, changeSet, item, sourceTasks);
                removeOpenTask(userId, source, before);
                item.setAppliedTaskId(source.getId());
            }
            case AgentPlanPreviewPlanner.CHANGE_RESCHEDULE_TASK -> {
                if (valueOrDefault(item.getApplyCount(), 0) == 0) {
                    AgentTask source = requireOpenSourceTask(userId, changeSet, item, sourceTasks);
                    removeOpenTask(userId, source, before);
                }
                TaskWriteResult writeResult = createConfirmedTask(userId, changeSet, item, run, after, sortOrder);
                item.setAppliedTaskId(writeResult.task().getId());
                duplicate = writeResult.duplicate();
                created = !writeResult.duplicate();
            }
            case AgentPlanPreviewPlanner.CHANGE_PRIORITY -> {
                AgentTask source = requireOpenSourceTask(userId, changeSet, item, sourceTasks);
                changePriority(userId, source, before, after);
                item.setAppliedTaskId(source.getId());
            }
            default -> throw validation("确认记录包含不受支持的变更类型。");
        }
        item.setApplyStatus(duplicate ? ITEM_DUPLICATE : ITEM_APPLIED);
        item.setAppliedRunId(run.getId());
        item.setApplyCount(valueOrDefault(item.getApplyCount(), 0) + 1);
        changeItemMapper.updateById(item);
        return new ApplyItemResult(created, duplicate);
    }

    private TaskWriteResult createConfirmedTask(Long userId,
                                                AgentPlanChangeSet changeSet,
                                                AgentPlanChangeItem item,
                                                AgentRun run,
                                                AgentPlanTaskSnapshotDTO after,
                                                int sortOrder) {
        if (after == null || !Objects.equals(after.getDueDate(), changeSet.getTargetDate())) {
            throw validation("新增任务快照与确认目标日期不一致。");
        }
        AgentTask existingByChange = taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getPlanChangeItemId, item.getId())
                .eq(AgentTask::getDeleted, 0)
                .orderByDesc(AgentTask::getId)
                .last("LIMIT 1"));
        if (existingByChange != null) {
            return new TaskWriteResult(existingByChange, true);
        }
        AgentTask duplicateTask = findDuplicateTask(userId, changeSet, after);
        if (duplicateTask != null) {
            attachConfirmedOrigin(duplicateTask, changeSet, item);
            return new TaskWriteResult(duplicateTask, true);
        }

        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setAgentRunId(run.getId());
        task.setTargetJobId(changeSet.getTargetJobId());
        task.setCandidateId("review-change:" + item.getId());
        task.setTaskType(after.getTaskType());
        task.setTitle(after.getTitle());
        task.setDescription(after.getDescription());
        task.setReason(after.getReason());
        task.setPriority(after.getPriority());
        task.setEstimatedMinutes(after.getEstimatedMinutes());
        task.setRelatedSkillCode(after.getRelatedSkillCode());
        task.setRelatedSkillName(after.getRelatedSkillName());
        task.setRelatedBizType(after.getRelatedBizType());
        task.setRelatedBizId(after.getRelatedBizId());
        task.setPlanChangeItemId(item.getId());
        task.setPlanOriginType(ORIGIN_REVIEW_CONFIRMED);
        task.setPlanOriginId(changeSet.getId());
        task.setUserConfirmed(true);
        task.setActionUrl(safeActionUrl(after.getActionUrl()));
        task.setStatus(AgentTaskStatusEnum.TODO.name());
        task.setDueDate(changeSet.getTargetDate());
        task.setSortOrder(sortOrder);
        try {
            taskMapper.insert(task);
            return new TaskWriteResult(task, false);
        } catch (DuplicateKeyException ex) {
            AgentTask concurrent = taskMapper.selectOne(new LambdaQueryWrapper<AgentTask>()
                    .eq(AgentTask::getUserId, userId)
                    .eq(AgentTask::getPlanChangeItemId, item.getId())
                    .eq(AgentTask::getDeleted, 0)
                    .orderByDesc(AgentTask::getId)
                    .last("LIMIT 1"));
            if (concurrent != null) {
                return new TaskWriteResult(concurrent, true);
            }
            throw ex;
        }
    }

    private void attachConfirmedOrigin(AgentTask task,
                                       AgentPlanChangeSet changeSet,
                                       AgentPlanChangeItem item) {
        if (task.getPlanChangeItemId() != null && !Objects.equals(task.getPlanChangeItemId(), item.getId())) {
            return;
        }
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, task.getId())
                .eq(AgentTask::getUserId, changeSet.getUserId())
                .eq(AgentTask::getDeleted, 0)
                .and(wrapper -> wrapper.isNull(AgentTask::getPlanChangeItemId)
                        .or().eq(AgentTask::getPlanChangeItemId, item.getId()))
                .set(AgentTask::getPlanChangeItemId, item.getId())
                .set(AgentTask::getPlanOriginType, ORIGIN_REVIEW_CONFIRMED)
                .set(AgentTask::getPlanOriginId, changeSet.getId())
                .set(AgentTask::getUserConfirmed, true)
                .set(AgentTask::getUpdatedAt, timeProvider.now()));
        if (updated != 1) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "重复任务的确认来源已被其他请求更新。");
        }
        task.setPlanChangeItemId(item.getId());
        task.setPlanOriginType(ORIGIN_REVIEW_CONFIRMED);
        task.setPlanOriginId(changeSet.getId());
        task.setUserConfirmed(true);
    }

    private AgentTask findDuplicateTask(Long userId,
                                        AgentPlanChangeSet changeSet,
                                        AgentPlanTaskSnapshotDTO after) {
        LambdaQueryWrapper<AgentTask> query = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDueDate, changeSet.getTargetDate())
                .eq(AgentTask::getTaskType, after.getTaskType())
                .eq(changeSet.getTargetJobId() != null, AgentTask::getTargetJobId, changeSet.getTargetJobId())
                .eq(AgentTask::getDeleted, 0)
                .in(AgentTask::getStatus, OPEN_TASK_STATUSES)
                .orderByAsc(AgentTask::getId);
        if (StringUtils.hasText(after.getRelatedBizType()) && after.getRelatedBizId() != null) {
            query.eq(AgentTask::getRelatedBizType, after.getRelatedBizType())
                    .eq(AgentTask::getRelatedBizId, after.getRelatedBizId());
        } else {
            query.eq(AgentTask::getTitle, after.getTitle());
        }
        List<AgentTask> tasks = taskMapper.selectList(query.last("LIMIT 1 FOR UPDATE"));
        return tasks == null || tasks.isEmpty() ? null : tasks.get(0);
    }

    private AgentTask requireOpenSourceTask(Long userId,
                                            AgentPlanChangeSet changeSet,
                                            AgentPlanChangeItem item,
                                            Map<Long, AgentTask> sourceTasks) {
        AgentTask source = sourceTasks.get(item.getSourceTaskId());
        if (source == null || !Objects.equals(source.getUserId(), userId)
                || Integer.valueOf(1).equals(source.getDeleted())
                || !OPEN_TASK_STATUSES.contains(source.getStatus())) {
            throw validation("来源任务已关闭、已删除或不再属于当前用户。");
        }
        if (changeSet.getTargetJobId() != null
                && !Objects.equals(changeSet.getTargetJobId(), source.getTargetJobId())) {
            throw forbidden("来源任务不属于当前目标岗位范围。");
        }
        return source;
    }

    private void removeOpenTask(Long userId, AgentTask source, AgentPlanTaskSnapshotDTO before) {
        if (before == null || !Objects.equals(before.getTaskId(), source.getId())
                || !Objects.equals(before.getStatus(), source.getStatus())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "来源任务状态已变化，需要重新预览。");
        }
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, source.getId())
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDeleted, 0)
                .eq(AgentTask::getStatus, before.getStatus())
                .in(AgentTask::getStatus, OPEN_TASK_STATUSES)
                .set(AgentTask::getStatus, AgentTaskStatusEnum.EXPIRED.name())
                .set(AgentTask::getDeleted, 1)
                .set(AgentTask::getUpdatedAt, timeProvider.now()));
        if (updated != 1) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "来源任务已被其他请求修改，需要重新预览。");
        }
        source.setStatus(AgentTaskStatusEnum.EXPIRED.name());
        source.setDeleted(1);
    }

    private void changePriority(Long userId,
                                AgentTask source,
                                AgentPlanTaskSnapshotDTO before,
                                AgentPlanTaskSnapshotDTO after) {
        if (before == null || after == null || !Objects.equals(before.getTaskId(), source.getId())
                || !Objects.equals(before.getPriority(), source.getPriority())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "来源任务优先级已变化，需要重新预览。");
        }
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AgentTask>()
                .eq(AgentTask::getId, source.getId())
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDeleted, 0)
                .eq(AgentTask::getPriority, before.getPriority())
                .in(AgentTask::getStatus, OPEN_TASK_STATUSES)
                .set(AgentTask::getPriority, after.getPriority())
                .set(AgentTask::getUpdatedAt, timeProvider.now()));
        if (updated != 1) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "来源任务已被其他请求修改，需要重新预览。");
        }
        source.setPriority(after.getPriority());
    }

    private Map<Long, AgentTask> lockSourceTasks(Long userId, List<AgentPlanChangeItem> items) {
        Set<Long> ids = items.stream().map(AgentPlanChangeItem::getSourceTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<AgentTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .in(AgentTask::getId, ids)
                .orderByAsc(AgentTask::getId)
                .last("FOR UPDATE"));
        return tasks.stream().collect(Collectors.toMap(AgentTask::getId, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
    }

    private void validateSelection(AgentPlanChangeSet changeSet, List<AgentPlanChangeItem> items) {
        Set<Long> suggestionIds = items.stream().map(AgentPlanChangeItem::getSuggestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AgentReviewPlanSuggestion> suggestions = suggestionMapper.selectList(
                new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                        .eq(AgentReviewPlanSuggestion::getUserId, changeSet.getUserId())
                        .eq(AgentReviewPlanSuggestion::getReviewId, changeSet.getReviewId())
                        .eq(AgentReviewPlanSuggestion::getReviewVersion, changeSet.getReviewVersion())
                        .in(!suggestionIds.isEmpty(), AgentReviewPlanSuggestion::getId, suggestionIds)
                        .eq(AgentReviewPlanSuggestion::getDecisionStatus, "ACCEPTED")
                        .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                        .orderByAsc(AgentReviewPlanSuggestion::getId)
                        .last("FOR UPDATE"));
        if (suggestions.size() != suggestionIds.size()
                || !Objects.equals(changeSet.getSelectionHash(),
                AgentAdaptivePlanHashUtils.selectionHash(suggestions))) {
            markStaleAndThrow(changeSet, "已采纳建议集合或决策版本已经变化。");
        }
    }

    private void validateBaselines(AgentPlanChangeSet changeSet,
                                   AgentRun currentRun,
                                   List<AgentTask> baselineTasks,
                                   AgentWeekPlan weekPlan,
                                   List<AgentWeekPlanItem> weekItems) {
        if (!Objects.equals(changeSet.getBaseDailyRunId(), currentRun == null ? null : currentRun.getId())
                || !Objects.equals(changeSet.getBaseDailyStatus(), currentRun == null ? null : currentRun.getStatus())
                || !Objects.equals(changeSet.getBaseDailyTaskHash(),
                AgentAdaptivePlanHashUtils.taskBaselineHash(baselineTasks))) {
            markStaleAndThrow(changeSet, "目标日计划或任务基线已经变化。");
        }
        if (!Objects.equals(changeSet.getBaseWeekPlanId(), weekPlan == null ? null : weekPlan.getId())
                || !Objects.equals(changeSet.getBaseWeekSnapshotVersion(),
                weekPlan == null ? null : weekPlan.getSnapshotVersion())
                || !Objects.equals(changeSet.getBaseWeekItemHash(),
                AgentAdaptivePlanHashUtils.weekItemBaselineHash(weekItems))) {
            markStaleAndThrow(changeSet, "周计划快照已经变化。");
        }
    }

    private List<AgentTask> baselineTasksForUpdate(Long userId,
                                                   AgentPlanChangeSet changeSet,
                                                   List<AgentPlanChangeItem> items) {
        Set<Long> sourceIds = items.stream().map(AgentPlanChangeItem::getSourceTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LambdaQueryWrapper<AgentTask> query = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
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

    private AgentRun latestRunForUpdate(Long userId, Long targetJobId, LocalDate targetDate) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getAgentType, "JOB_COACH")
                .eq(AgentRun::getPlanDate, targetDate)
                .in(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name(), AgentRunStatusEnum.SUCCESS.name(),
                        AgentRunStatusEnum.FAILED.name())
                .eq(AgentRun::getDeleted, 0)
                .orderByDesc(AgentRun::getCreatedAt)
                .last("LIMIT 1 FOR UPDATE");
        if (targetJobId == null) {
            query.isNull(AgentRun::getTargetJobId);
        } else {
            query.eq(AgentRun::getTargetJobId, targetJobId);
        }
        List<AgentRun> runs = runMapper.selectList(query);
        return runs == null || runs.isEmpty() ? null : runs.get(0);
    }

    private AgentWeekPlan currentWeekPlanForUpdate(Long userId, Long targetJobId, LocalDate targetDate) {
        LocalDate weekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return weekPlanMapper.selectOne(new LambdaQueryWrapper<AgentWeekPlan>()
                .eq(AgentWeekPlan::getUserId, userId)
                .eq(AgentWeekPlan::getTargetScopeKey, AgentAdaptivePlanHashUtils.targetScopeKey(targetJobId))
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

    private List<AgentPlanChangeSet> waitingChangeSetsForUpdate(Long userId, AgentRun run) {
        LambdaQueryWrapper<AgentPlanChangeSet> query = new LambdaQueryWrapper<AgentPlanChangeSet>()
                .eq(AgentPlanChangeSet::getUserId, userId)
                .eq(AgentPlanChangeSet::getTargetDate, run.getPlanDate())
                .eq(AgentPlanChangeSet::getTargetScopeKey,
                        AgentAdaptivePlanHashUtils.targetScopeKey(run.getTargetJobId()))
                .in(AgentPlanChangeSet::getStatus,
                        STATUS_WAITING, STATUS_APPLY_FAILED, STATUS_PARTIALLY_APPLIED)
                .eq(AgentPlanChangeSet::getDeleted, 0)
                .orderByAsc(AgentPlanChangeSet::getId)
                .last("FOR UPDATE");
        return changeSetMapper.selectList(query);
    }

    private void requireConfirmableItems(AgentPlanChangeSet changeSet,
                                         List<AgentPlanChangeItem> items,
                                         List<String> acknowledgedWarnings) {
        Map<String, Object> summary = jsonCodec.readObjectMap(changeSet.getPreviewSummaryJson());
        List<String> blockers = objectList(summary.get("blockers"));
        if (!blockers.isEmpty()) {
            throw validation("当前预览存在阻断项，不能确认写入计划。");
        }
        Set<String> requiredWarnings = items.stream()
                .flatMap(item -> jsonCodec.readStringList(item.getWarningCodesJson()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        requiredWarnings.addAll(objectList(summary.get("warnings")));
        Set<String> acknowledged = acknowledgedWarnings == null ? Set.of()
                : acknowledgedWarnings.stream().filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (!acknowledged.containsAll(requiredWarnings)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    AgentErrorCode.PLAN_CHANGE_WARNING_NOT_ACKNOWLEDGED + "：请先确认预览中的全部警告。");
        }
        boolean hasApplicableItem = items.stream().anyMatch(item -> !ITEM_DUPLICATE.equals(item.getApplyStatus()));
        if (!hasApplicableItem) {
            throw validation("当前预览没有可写入计划的实际变更。");
        }
    }

    private void updateChangeSetWithConfirmKey(AgentPlanChangeSet changeSet,
                                               String requestKeyHash,
                                               String payloadHash) {
        try {
            changeSet.setLockVersion(valueOrDefault(changeSet.getLockVersion(), 1) + 1);
            changeSetMapper.updateById(changeSet);
        } catch (DuplicateKeyException ex) {
            AgentPlanChangeSet existing = changeSetMapper.selectOne(new LambdaQueryWrapper<AgentPlanChangeSet>()
                    .eq(AgentPlanChangeSet::getUserId, changeSet.getUserId())
                    .eq(AgentPlanChangeSet::getConfirmRequestKeyHash, requestKeyHash)
                    .eq(AgentPlanChangeSet::getDeleted, 0)
                    .last("LIMIT 1"));
            if (existing != null && Objects.equals(existing.getConfirmPayloadHash(), payloadHash)) {
                throw conflict(AgentErrorCode.PLAN_CHANGE_CONFIRM_IN_PROGRESS, "相同确认请求正在处理中，请查询变更集状态。");
            }
            throw conflict(AgentErrorCode.IDEMPOTENCY_KEY_REUSED, "同一幂等键不能用于不同的确认请求。");
        }
    }

    private AgentPlanChangeConfirmVO idempotentReplay(AgentPlanChangeSet changeSet,
                                                       String requestKeyHash,
                                                       String payloadHash) {
        if (!StringUtils.hasText(changeSet.getConfirmRequestKeyHash())) {
            return null;
        }
        if (!Objects.equals(changeSet.getConfirmRequestKeyHash(), requestKeyHash)) {
            if (List.of(STATUS_WAITING, STATUS_APPLIED, STATUS_PARTIALLY_APPLIED).contains(changeSet.getStatus())) {
                return confirmVO(changeSet, loadItems(changeSet), null, null, "该计划变更已经确认。");
            }
            throw conflict(AgentErrorCode.PLAN_CHANGE_ALREADY_DECIDED, "计划变更集已由其他确认请求处理。");
        }
        if (!Objects.equals(changeSet.getConfirmPayloadHash(), payloadHash)) {
            throw conflict(AgentErrorCode.IDEMPOTENCY_KEY_REUSED, "同一幂等键不能用于不同的确认请求。");
        }
        return confirmVO(changeSet, loadItems(changeSet), null, null, "已返回原确认请求的处理结果。");
    }

    private List<AgentPlanChangeItem> loadItems(AgentPlanChangeSet changeSet) {
        return changeItemMapper.selectList(new LambdaQueryWrapper<AgentPlanChangeItem>()
                .eq(AgentPlanChangeItem::getUserId, changeSet.getUserId())
                .eq(AgentPlanChangeItem::getChangeSetId, changeSet.getId())
                .eq(AgentPlanChangeItem::getDeleted, 0)
                .orderByAsc(AgentPlanChangeItem::getId));
    }

    private void attachWeekResults(List<AgentPlanChangeItem> items, AgentReviewPlanWeekResult weekResult) {
        if (weekResult == null) {
            return;
        }
        for (AgentPlanChangeItem item : items) {
            item.setAppliedWeekPlanId(weekResult.getWeekPlanId());
            item.setAppliedWeekPlanItemId(item.getAppliedTaskId() == null ? null
                    : weekResult.getWeekPlanItemIdsByTaskId().get(item.getAppliedTaskId()));
            changeItemMapper.updateById(item);
        }
    }

    private AgentPlanChangeConfirmVO confirmVO(AgentPlanChangeSet changeSet,
                                               List<AgentPlanChangeItem> items,
                                               AgentRun run,
                                               AgentReviewPlanWeekResult weekResult,
                                               String message) {
        AgentPlanChangeConfirmVO vo = new AgentPlanChangeConfirmVO();
        vo.setChangeSetId(changeSet.getId());
        vo.setStatus(changeSet.getStatus());
        vo.setConfirmedAt(changeSet.getConfirmedAt());
        vo.setAppliedAt(changeSet.getAppliedAt());
        vo.setDailyPlanRunId(run == null ? appliedRunId(items) : run.getId());
        vo.setWeekPlanId(weekResult == null ? appliedWeekPlanId(items) : weekResult.getWeekPlanId());
        vo.setWeekSnapshotVersion(weekResult == null ? null : weekResult.getSnapshotVersion());
        vo.setAppliedItemCount((int) items.stream()
                .filter(item -> ITEM_APPLIED.equals(item.getApplyStatus())
                        || ITEM_DUPLICATE.equals(item.getApplyStatus()))
                .count());
        vo.setWaitingItemCount((int) items.stream().filter(item -> ITEM_WAITING.equals(item.getApplyStatus())).count());
        vo.setConflicts(items.stream().filter(item -> ITEM_FAILED.equals(item.getApplyStatus()))
                .map(item -> "变更项 " + item.getId() + " 的前置条件已失效。")
                .toList());
        vo.setMessage(message);
        return vo;
    }

    private Long appliedRunId(List<AgentPlanChangeItem> items) {
        return items.stream().map(AgentPlanChangeItem::getAppliedRunId)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Long appliedWeekPlanId(List<AgentPlanChangeItem> items) {
        return items.stream().map(AgentPlanChangeItem::getAppliedWeekPlanId)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private int nextSortOrder(Long userId, Long runId) {
        List<AgentTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getAgentRunId, runId)
                .eq(AgentTask::getDeleted, 0)
                .orderByDesc(AgentTask::getSortOrder)
                .last("LIMIT 1"));
        return tasks == null || tasks.isEmpty() ? 1 : valueOrDefault(tasks.get(0).getSortOrder(), 0) + 1;
    }

    private String confirmPayloadHash(AgentPlanChangeConfirmDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previewVersion", dto.getPreviewVersion());
        payload.put("previewHash", dto.getPreviewHash());
        payload.put("acknowledgedWarningCodes", dto.getAcknowledgedWarningCodes() == null ? List.of()
                : dto.getAcknowledgedWarningCodes().stream().sorted().toList());
        return AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(payload));
    }

    private List<String> objectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }

    private String safeActionUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") && !trimmed.startsWith("//") ? trimmed : null;
    }

    private void requireConfirmRequest(AgentPlanChangeConfirmDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getIdempotencyKey())
                || dto.getPreviewVersion() == null || !StringUtils.hasText(dto.getPreviewHash())) {
            throw validation("计划确认请求不完整。");
        }
    }

    private void markStaleAndThrow(AgentPlanChangeSet changeSet, String message) {
        changeSet.setStatus(STATUS_STALE);
        changeSet.setFailureCode(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE);
        changeSet.setFailureMessage(message);
        changeSet.setLockVersion(valueOrDefault(changeSet.getLockVersion(), 1) + 1);
        changeSetMapper.updateById(changeSet);
        throw new AgentPlanChangeConflictException(
                AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE + "：" + message);
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR,
                AgentErrorCode.PLAN_CHANGE_VALIDATION_FAILED + "：" + message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN,
                AgentErrorCode.PLAN_CHANGE_FORBIDDEN + "：" + message);
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(ErrorCode.STALE_SOURCE_VERSION, code + "：" + message);
    }

    private record TaskWriteResult(AgentTask task, boolean duplicate) {
    }

    private record ApplyItemResult(boolean created, boolean duplicate) {
    }

    private record ApplyResult(int failedCount, int duplicateCount) {
    }
}
