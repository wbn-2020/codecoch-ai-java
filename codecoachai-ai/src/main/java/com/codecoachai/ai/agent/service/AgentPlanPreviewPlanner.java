package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentPlanSuggestionIntentDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanTaskSnapshotDTO;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.enums.AgentTaskPriorityEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskTypeEnum;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeSummaryVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeJsonCodec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AgentPlanPreviewPlanner {

    public static final String CHANGE_ADD_TASK = "ADD_TASK";
    public static final String CHANGE_CARRY_OVER_TASK = "CARRY_OVER_TASK";
    public static final String CHANGE_REMOVE_OPEN_TASK = "REMOVE_OPEN_TASK";
    public static final String CHANGE_RESCHEDULE_TASK = "RESCHEDULE_TASK";
    public static final String CHANGE_PRIORITY = "CHANGE_PRIORITY";

    private static final String INTENT_REDUCE_LOAD = "REDUCE_LOAD";
    private static final String INTENT_CARRY_OVER = "CARRY_OVER";
    private static final String INTENT_ADD_PRACTICE = "ADD_PRACTICE";
    private static final String INTENT_RESCHEDULE = "RESCHEDULE";
    private static final String INTENT_CHANGE_PRIORITY = "CHANGE_PRIORITY";
    private static final String INTENT_MANUAL_ONLY = "MANUAL_ONLY";
    private static final String VALIDATION_PASS = "PASS";
    private static final String VALIDATION_WARN = "WARN";
    private static final String APPLY_PENDING = "PENDING";
    private static final String APPLY_SKIPPED_DUPLICATE = "SKIPPED_DUPLICATE";

    private final AgentPlanChangeJsonCodec jsonCodec;
    private final AgentPlanPreviewValidator validator;

    public PlanningResult plan(PlanningInput input) {
        List<AgentReviewPlanSuggestion> suggestions = sortedSuggestions(input.suggestions());
        Map<Long, AgentTask> taskById = taskById(input.availableTasks());
        List<AgentPlanTaskSnapshotDTO> beforeTasks = effectiveTargetTasks(input.targetTasks());
        List<AgentPlanTaskSnapshotDTO> projectedTasks = new ArrayList<>(beforeTasks);
        List<ItemDraft> items = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>();
        boolean weakAdjustment = isWeak(input.review(), suggestions);
        boolean fallback = Boolean.TRUE.equals(input.review().getFallback())
                || suggestions.stream().anyMatch(item -> Boolean.TRUE.equals(item.getFallback()));

        for (AgentReviewPlanSuggestion suggestion : suggestions) {
            if (weakAdjustment && !items.isEmpty()) {
                warnings.add(AgentPlanPreviewValidator.WARNING_WEAK_SINGLE_CHANGE);
                break;
            }
            AgentPlanSuggestionIntentDTO intent = jsonCodec.readIntent(suggestion.getIntentJson());
            ItemDraft draft = mapSuggestion(input, suggestion, intent, taskById, projectedTasks,
                    weakAdjustment, blockers);
            if (draft == null) {
                continue;
            }
            items.add(draft);
            if (!APPLY_SKIPPED_DUPLICATE.equals(draft.applyStatus())) {
                applyProjection(projectedTasks, draft);
            }
            warnings.addAll(draft.warningCodes());
        }

        int beforeMinutes = totalMinutes(beforeTasks);
        AgentPlanPreviewValidator.ValidationResult validation = validator.validate(
                input.currentDate(),
                input.targetDate(),
                input.maxTotalMinutes(),
                projectedTasks,
                beforeMinutes,
                "ALL".equals(input.review().getTargetScopeKey()),
                weakAdjustment);
        warnings.addAll(validation.warnings());
        blockers.addAll(validation.blockers());
        if (items.isEmpty() && blockers.isEmpty()) {
            blockers.add("当前已采纳建议无法安全映射为计划变更，请重新选择建议。");
        }

        AgentPlanChangeSummaryVO summary = summary(beforeTasks, projectedTasks, items);
        return new PlanningResult(
                List.copyOf(items),
                summary,
                List.copyOf(warnings),
                List.copyOf(new LinkedHashSet<>(blockers)),
                fallback ? "FALLBACK" : "RULE",
                fallback,
                AgentAdaptivePlanHashUtils.taskBaselineHash(input.baselineTasks()),
                input.weekPlan() == null ? null : input.weekPlan().getId(),
                input.weekPlan() == null ? null : input.weekPlan().getSnapshotVersion(),
                AgentAdaptivePlanHashUtils.weekItemBaselineHash(input.weekItems()));
    }

    private ItemDraft mapSuggestion(PlanningInput input,
                                    AgentReviewPlanSuggestion suggestion,
                                    AgentPlanSuggestionIntentDTO intent,
                                    Map<Long, AgentTask> taskById,
                                    List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                    boolean weakAdjustment,
                                    List<String> blockers) {
        String intentType = normalizeCode(suggestion.getIntentType());
        if (INTENT_MANUAL_ONLY.equals(intentType)) {
            blockers.add("建议“" + safeTitle(suggestion.getTitle()) + "”仅支持人工参考，不能确认写入计划。");
            return null;
        }
        AgentTask sourceTask = resolveSourceTask(intent, taskById);
        return switch (intentType) {
            case INTENT_CARRY_OVER -> carryOver(input, suggestion, sourceTask, projectedTasks,
                    weakAdjustment, blockers);
            case INTENT_ADD_PRACTICE -> addPractice(input, suggestion, intent, sourceTask, projectedTasks,
                    weakAdjustment, blockers);
            case INTENT_REDUCE_LOAD -> reduceLoad(input, suggestion, sourceTask, projectedTasks,
                    weakAdjustment, blockers);
            case INTENT_RESCHEDULE -> reschedule(input, suggestion, sourceTask, projectedTasks,
                    weakAdjustment, blockers);
            case INTENT_CHANGE_PRIORITY -> changePriority(input, suggestion, intent, sourceTask,
                    projectedTasks, weakAdjustment, blockers);
            default -> {
                blockers.add("建议“" + safeTitle(suggestion.getTitle()) + "”的调整类型不受支持。");
                yield null;
            }
        };
    }

    private ItemDraft carryOver(PlanningInput input,
                                AgentReviewPlanSuggestion suggestion,
                                AgentTask sourceTask,
                                List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                boolean weakAdjustment,
                                List<String> blockers) {
        AgentPlanTaskSnapshotDTO before = taskSnapshot(sourceTask);
        if (!canChangeSource(input, sourceTask, before, blockers)) {
            return null;
        }
        boolean hardDeadline = validator.hasHardBusinessDeadline(before);
        if (weakAdjustment && hardDeadline) {
            blockers.add("低置信度或降级复盘不允许承接有明确面试或投递跟进时限的任务。");
            return null;
        }
        AgentPlanTaskSnapshotDTO after = copyForTarget(before, input.targetDate());
        if (weakAdjustment) {
            after.setEstimatedMinutes(Math.min(30, safeMinutes(after.getEstimatedMinutes(), 30)));
            if (AgentTaskPriorityEnum.HIGH.name().equals(after.getPriority())) {
                after.setPriority(AgentTaskPriorityEnum.MEDIUM.name());
            }
        }
        boolean duplicate = containsDuplicate(projectedTasks, after);
        return itemDraft(suggestion, CHANGE_CARRY_OVER_TASK, before, after,
                duplicate ? "目标日期已有同类任务，本项不会重复新增。" : "下一日新增一项承接任务。",
                duplicate ? "本周任务保持不变。" : "本周任务总数增加一项。",
                hardDeadline
                        ? List.of(AgentPlanPreviewValidator.WARNING_DEADLINE_RESCHEDULE)
                        : List.of(),
                duplicate);
    }

    private ItemDraft addPractice(PlanningInput input,
                                  AgentReviewPlanSuggestion suggestion,
                                  AgentPlanSuggestionIntentDTO intent,
                                  AgentTask sourceTask,
                                  List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                  boolean weakAdjustment,
                                  List<String> blockers) {
        AgentPlanTaskSnapshotDTO after = sourceTask == null ? new AgentPlanTaskSnapshotDTO() : taskSnapshot(sourceTask);
        after.setTaskId(null);
        after.setAgentRunId(input.baseRun() == null ? null : input.baseRun().getId());
        after.setTargetJobId(input.review().getTargetJobId());
        after.setCandidateId(null);
        after.setTaskType(firstText(intent.getTaskType(), after.getTaskType()));
        after.setTitle(firstText(intent.getTitle(), suggestion.getTitle()));
        after.setDescription(firstText(intent.getDescription(), suggestion.getContent()));
        after.setReason(firstText(intent.getReason(), suggestion.getReason()));
        after.setPriority(normalizePriority(firstText(intent.getPriority(), after.getPriority(), "MEDIUM")));
        after.setEstimatedMinutes(safeMinutes(
                firstInteger(intent.getEstimatedMinutes(), positiveDelta(intent.getEstimatedMinutesDelta()),
                        after.getEstimatedMinutes()), 30));
        after.setRelatedSkillCode(firstText(intent.getRelatedSkillCode(), after.getRelatedSkillCode()));
        after.setRelatedSkillName(firstText(intent.getRelatedSkillName(), after.getRelatedSkillName()));
        after.setRelatedBizType(firstText(intent.getRelatedBizType(), after.getRelatedBizType()));
        after.setRelatedBizId(intent.getRelatedBizId() == null ? after.getRelatedBizId() : intent.getRelatedBizId());
        after.setActionUrl(safeActionUrl(firstText(intent.getActionUrl(), after.getActionUrl())));
        after.setStatus(AgentTaskStatusEnum.TODO.name());
        after.setDueDate(input.targetDate());
        if (!validTaskType(after.getTaskType())) {
            blockers.add("新增练习建议缺少受支持的任务类型。");
            return null;
        }
        if (!StringUtils.hasText(after.getRelatedSkillCode())
                && !StringUtils.hasText(after.getRelatedSkillName())
                && (!StringUtils.hasText(after.getRelatedBizType()) || after.getRelatedBizId() == null)) {
            blockers.add("新增练习建议缺少可信技能或业务来源，只能作为人工参考。");
            return null;
        }
        if (weakAdjustment) {
            after.setEstimatedMinutes(Math.min(30, after.getEstimatedMinutes()));
            if (AgentTaskPriorityEnum.HIGH.name().equals(after.getPriority())) {
                after.setPriority(AgentTaskPriorityEnum.MEDIUM.name());
            }
        }
        boolean duplicate = containsDuplicate(projectedTasks, after);
        return itemDraft(suggestion, CHANGE_ADD_TASK, null, after,
                duplicate ? "目标日期已有同类任务，本项不会重复新增。" : "目标日期新增一项练习任务。",
                duplicate ? "本周任务保持不变。" : "本周任务总数增加一项。",
                List.of(), duplicate);
    }

    private ItemDraft reduceLoad(PlanningInput input,
                                 AgentReviewPlanSuggestion suggestion,
                                 AgentTask sourceTask,
                                 List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                 boolean weakAdjustment,
                                 List<String> blockers) {
        if (weakAdjustment) {
            blockers.add("低置信度或降级复盘不允许移出任务。");
            return null;
        }
        AgentPlanTaskSnapshotDTO selected = sourceTask == null
                ? projectedTasks.stream()
                .filter(task -> validator.isOpenStatus(task.getStatus()))
                .filter(task -> task.getPlanChangeItemId() == null)
                .filter(task -> !validator.hasHardBusinessDeadline(task))
                .sorted(Comparator.comparingInt(this::priorityRank).reversed()
                        .thenComparing(AgentPlanTaskSnapshotDTO::getEstimatedMinutes,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentPlanTaskSnapshotDTO::getTaskId,
                                Comparator.nullsLast(Long::compareTo)))
                .findFirst().orElse(null)
                : taskSnapshot(sourceTask);
        if (selected == null) {
            blockers.add("当前计划中没有可安全移出的开放任务。");
            return null;
        }
        if (sourceTask != null && !canChangeSource(input, sourceTask, selected, blockers)) {
            return null;
        }
        if (!Objects.equals(selected.getDueDate(), input.targetDate())) {
            blockers.add("只能从本次预览的目标日期移出任务。");
            return null;
        }
        if (validator.hasHardBusinessDeadline(selected)) {
            blockers.add("存在明确面试或投递跟进时限的任务不能移出计划。");
            return null;
        }
        List<String> warningCodes = AgentTaskPriorityEnum.HIGH.name().equals(selected.getPriority())
                ? List.of(AgentPlanPreviewValidator.WARNING_HIGH_PRIORITY_REMOVAL) : List.of();
        return itemDraft(suggestion, CHANGE_REMOVE_OPEN_TASK, selected, null,
                "从目标日期计划中移出一项开放任务。",
                "本周开放任务减少一项。",
                warningCodes, false);
    }

    private ItemDraft reschedule(PlanningInput input,
                                 AgentReviewPlanSuggestion suggestion,
                                 AgentTask sourceTask,
                                 List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                 boolean weakAdjustment,
                                 List<String> blockers) {
        AgentPlanTaskSnapshotDTO before = taskSnapshot(sourceTask);
        if (!canChangeSource(input, sourceTask, before, blockers)) {
            return null;
        }
        if (Objects.equals(before.getDueDate(), input.targetDate())) {
            blockers.add("延后任务的目标日期与原日期相同。");
            return null;
        }
        if (before.getDueDate() == null || input.targetDate() == null
                || !input.targetDate().isAfter(before.getDueDate())) {
            blockers.add("延后任务的目标日期必须晚于原日期。");
            return null;
        }
        boolean hardDeadline = validator.hasHardBusinessDeadline(before);
        if (weakAdjustment && hardDeadline) {
            blockers.add("低置信度或降级复盘不允许调整有明确面试或投递跟进时限的任务。");
            return null;
        }
        AgentPlanTaskSnapshotDTO after = copyForTarget(before, input.targetDate());
        if (weakAdjustment) {
            after.setEstimatedMinutes(Math.min(30, safeMinutes(after.getEstimatedMinutes(), 30)));
            if (AgentTaskPriorityEnum.HIGH.name().equals(after.getPriority())) {
                after.setPriority(AgentTaskPriorityEnum.MEDIUM.name());
            }
        }
        List<String> warningCodes = hardDeadline
                ? List.of(AgentPlanPreviewValidator.WARNING_DEADLINE_RESCHEDULE) : List.of();
        boolean duplicate = containsDuplicate(projectedTasks, after);
        return itemDraft(suggestion, CHANGE_RESCHEDULE_TASK, before, after,
                duplicate ? "目标日期已有同类任务，本项不会重复新增。" : "原开放任务保留历史，并在目标日期新增承接任务。",
                duplicate ? "本周任务日期保持不变。" : "本周任务日期发生调整。",
                warningCodes, duplicate);
    }

    private ItemDraft changePriority(PlanningInput input,
                                     AgentReviewPlanSuggestion suggestion,
                                     AgentPlanSuggestionIntentDTO intent,
                                     AgentTask sourceTask,
                                     List<AgentPlanTaskSnapshotDTO> projectedTasks,
                                     boolean weakAdjustment,
                                     List<String> blockers) {
        AgentPlanTaskSnapshotDTO before = taskSnapshot(sourceTask);
        if (!canChangeSource(input, sourceTask, before, blockers)) {
            return null;
        }
        if (!Objects.equals(before.getDueDate(), input.targetDate())) {
            blockers.add("只能调整本次预览目标日期内任务的优先级。");
            return null;
        }
        boolean hardDeadline = validator.hasHardBusinessDeadline(before);
        if (weakAdjustment && hardDeadline) {
            blockers.add("低置信度或降级复盘不允许调整有明确面试或投递跟进时限任务的优先级。");
            return null;
        }
        String targetPriority = normalizePriority(intent.getTargetPriority());
        if (!StringUtils.hasText(targetPriority)) {
            blockers.add("优先级调整建议缺少目标优先级。");
            return null;
        }
        if (weakAdjustment && AgentTaskPriorityEnum.HIGH.name().equals(targetPriority)) {
            blockers.add("低置信度或降级复盘不能把任务优先级提升为高。");
            return null;
        }
        AgentPlanTaskSnapshotDTO after = taskSnapshot(sourceTask);
        after.setPriority(targetPriority);
        if (Objects.equals(before.getPriority(), after.getPriority())) {
            blockers.add("任务优先级未发生变化。");
            return null;
        }
        return itemDraft(suggestion, CHANGE_PRIORITY, before, after,
                "调整一项开放任务的优先级。",
                "本周任务优先级发生变化。",
                hardDeadline
                        ? List.of(AgentPlanPreviewValidator.WARNING_DEADLINE_PRIORITY_CHANGE)
                        : List.of(),
                false);
    }

    private boolean canChangeSource(PlanningInput input,
                                    AgentTask sourceTask,
                                    AgentPlanTaskSnapshotDTO snapshot,
                                    List<String> blockers) {
        if (sourceTask == null || snapshot == null) {
            blockers.add("建议引用的任务不存在或已不可见。");
            return false;
        }
        if (!Objects.equals(sourceTask.getUserId(), input.review().getUserId())) {
            blockers.add("建议引用的任务不属于当前用户。");
            return false;
        }
        if (input.review().getTargetJobId() != null
                && !Objects.equals(sourceTask.getTargetJobId(), input.review().getTargetJobId())) {
            blockers.add("建议引用的任务不属于当前目标岗位范围。");
            return false;
        }
        if (!validator.isOpenStatus(snapshot.getStatus())) {
            blockers.add("已完成、已跳过或已失效的任务不能通过计划调整改写。");
            return false;
        }
        return true;
    }

    private ItemDraft itemDraft(AgentReviewPlanSuggestion suggestion,
                                String changeType,
                                AgentPlanTaskSnapshotDTO before,
                                AgentPlanTaskSnapshotDTO after,
                                String dailyImpact,
                                String weekImpact,
                                List<String> warningCodes,
                                boolean duplicate) {
        String sourceId = before == null ? "" : Objects.toString(before.getTaskId(), "");
        String targetDate = after == null ? "" : Objects.toString(after.getDueDate(), "");
        String itemKey = AgentAdaptivePlanHashUtils.sha256(
                suggestion.getId() + "|" + changeType + "|" + sourceId + "|" + targetDate
                        + "|" + (after == null ? "" : Objects.toString(after.getPriority(), "")));
        return new ItemDraft(
                suggestion.getId(),
                itemKey,
                changeType,
                before == null ? null : before.getTaskId(),
                before,
                after,
                dailyImpact,
                weekImpact,
                warningCodes == null || warningCodes.isEmpty() ? VALIDATION_PASS : VALIDATION_WARN,
                warningCodes == null ? List.of() : List.copyOf(warningCodes),
                suggestion.getConfidenceLevel(),
                Boolean.TRUE.equals(suggestion.getFallback()),
                duplicate ? APPLY_SKIPPED_DUPLICATE : APPLY_PENDING);
    }

    private void applyProjection(List<AgentPlanTaskSnapshotDTO> projectedTasks, ItemDraft item) {
        switch (item.changeType()) {
            case CHANGE_ADD_TASK, CHANGE_CARRY_OVER_TASK -> projectedTasks.add(item.after());
            case CHANGE_REMOVE_OPEN_TASK -> removeTask(projectedTasks, item.before());
            case CHANGE_RESCHEDULE_TASK -> {
                removeTask(projectedTasks, item.before());
                projectedTasks.add(item.after());
            }
            case CHANGE_PRIORITY -> {
                removeTask(projectedTasks, item.before());
                projectedTasks.add(item.after());
            }
            default -> {
            }
        }
    }

    private void removeTask(List<AgentPlanTaskSnapshotDTO> tasks, AgentPlanTaskSnapshotDTO target) {
        if (target == null || target.getTaskId() == null) {
            return;
        }
        tasks.removeIf(item -> Objects.equals(item.getTaskId(), target.getTaskId()));
    }

    private AgentPlanChangeSummaryVO summary(List<AgentPlanTaskSnapshotDTO> beforeTasks,
                                             List<AgentPlanTaskSnapshotDTO> afterTasks,
                                             List<ItemDraft> items) {
        AgentPlanChangeSummaryVO summary = new AgentPlanChangeSummaryVO();
        summary.setBeforeTaskCount(beforeTasks.size());
        summary.setAfterTaskCount(afterTasks.size());
        summary.setBeforeMinutes(totalMinutes(beforeTasks));
        summary.setAfterMinutes(totalMinutes(afterTasks));
        for (ItemDraft item : items) {
            if (APPLY_SKIPPED_DUPLICATE.equals(item.applyStatus())) {
                continue;
            }
            switch (item.changeType()) {
                case CHANGE_ADD_TASK, CHANGE_CARRY_OVER_TASK -> summary.setAddCount(summary.getAddCount() + 1);
                case CHANGE_REMOVE_OPEN_TASK -> summary.setRemoveCount(summary.getRemoveCount() + 1);
                case CHANGE_RESCHEDULE_TASK -> summary.setRescheduleCount(summary.getRescheduleCount() + 1);
                case CHANGE_PRIORITY -> summary.setPriorityChangeCount(summary.getPriorityChangeCount() + 1);
                default -> {
                }
            }
        }
        return summary;
    }

    private List<AgentPlanTaskSnapshotDTO> effectiveTargetTasks(List<AgentTask> tasks) {
        if (tasks == null) {
            return new ArrayList<>();
        }
        return tasks.stream()
                .filter(task -> !Integer.valueOf(1).equals(task.getDeleted()))
                .filter(task -> validator.isOpenStatus(task.getStatus()))
                .map(this::taskSnapshot)
                .toList();
    }

    private AgentPlanTaskSnapshotDTO taskSnapshot(AgentTask task) {
        if (task == null) {
            return null;
        }
        AgentPlanTaskSnapshotDTO snapshot = new AgentPlanTaskSnapshotDTO();
        snapshot.setTaskId(task.getId());
        snapshot.setAgentRunId(task.getAgentRunId());
        snapshot.setTargetJobId(task.getTargetJobId());
        snapshot.setCandidateId(task.getCandidateId());
        snapshot.setTaskType(task.getTaskType());
        snapshot.setTitle(task.getTitle());
        snapshot.setDescription(task.getDescription());
        snapshot.setReason(task.getReason());
        snapshot.setPriority(task.getPriority());
        snapshot.setEstimatedMinutes(task.getEstimatedMinutes());
        snapshot.setRelatedSkillCode(task.getRelatedSkillCode());
        snapshot.setRelatedSkillName(task.getRelatedSkillName());
        snapshot.setRelatedBizType(task.getRelatedBizType());
        snapshot.setRelatedBizId(task.getRelatedBizId());
        snapshot.setActionUrl(safeActionUrl(task.getActionUrl()));
        snapshot.setStatus(task.getStatus());
        snapshot.setDueDate(task.getDueDate());
        snapshot.setPlanChangeItemId(task.getPlanChangeItemId());
        snapshot.setUpdatedAt(task.getUpdatedAt());
        snapshot.setDeleted(task.getDeleted());
        return snapshot;
    }

    private AgentPlanTaskSnapshotDTO copyForTarget(AgentPlanTaskSnapshotDTO source, LocalDate targetDate) {
        AgentPlanTaskSnapshotDTO copy = jsonCodec.readTaskSnapshot(jsonCodec.write(source));
        copy.setTaskId(null);
        copy.setAgentRunId(null);
        copy.setCandidateId(null);
        copy.setStatus(AgentTaskStatusEnum.TODO.name());
        copy.setDueDate(targetDate);
        copy.setPlanChangeItemId(null);
        copy.setUpdatedAt(null);
        copy.setDeleted(0);
        return copy;
    }

    private AgentTask resolveSourceTask(AgentPlanSuggestionIntentDTO intent, Map<Long, AgentTask> taskById) {
        if (intent == null) {
            return null;
        }
        Long taskId = intent.getSourceTaskId();
        if (taskId == null && intent.getRelatedTaskRefs() != null) {
            taskId = intent.getRelatedTaskRefs().stream()
                    .map(this::parseTaskRef)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return taskId == null ? null : taskById.get(taskId);
    }

    private Long parseTaskRef(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith("task:")) {
            return null;
        }
        try {
            return Long.parseLong(value.substring("task:".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean containsDuplicate(List<AgentPlanTaskSnapshotDTO> tasks, AgentPlanTaskSnapshotDTO candidate) {
        String candidateKey = duplicateKey(candidate);
        return StringUtils.hasText(candidateKey)
                && tasks.stream().map(this::duplicateKey).anyMatch(candidateKey::equals);
    }

    private String duplicateKey(AgentPlanTaskSnapshotDTO task) {
        if (task == null || task.getDueDate() == null || !StringUtils.hasText(task.getTaskType())) {
            return null;
        }
        if (StringUtils.hasText(task.getRelatedBizType()) && task.getRelatedBizId() != null) {
            return String.join("|",
                    task.getDueDate().toString(),
                    Objects.toString(task.getTargetJobId(), "ALL"),
                    task.getTaskType(),
                    task.getRelatedBizType(),
                    task.getRelatedBizId().toString());
        }
        return String.join("|",
                task.getDueDate().toString(),
                Objects.toString(task.getTargetJobId(), "ALL"),
                task.getTaskType(),
                AgentAdaptivePlanHashUtils.sha256(AgentAdaptivePlanHashUtils.normalizeText(task.getTitle())));
    }

    private boolean isWeak(AgentReview review, List<AgentReviewPlanSuggestion> suggestions) {
        if (review == null) {
            return true;
        }
        String level = normalizeCode(review.getConfidenceLevel());
        return Boolean.TRUE.equals(review.getFallback())
                || "LOW".equals(level)
                || "INSUFFICIENT".equals(level)
                || suggestions.stream().anyMatch(item -> Boolean.TRUE.equals(item.getFallback())
                || "LOW".equals(normalizeCode(item.getConfidenceLevel()))
                || "INSUFFICIENT".equals(normalizeCode(item.getConfidenceLevel())));
    }

    private Map<Long, AgentTask> taskById(List<AgentTask> tasks) {
        Map<Long, AgentTask> result = new HashMap<>();
        if (tasks != null) {
            tasks.stream().filter(task -> task.getId() != null).forEach(task -> result.put(task.getId(), task));
        }
        return result;
    }

    private List<AgentReviewPlanSuggestion> sortedSuggestions(List<AgentReviewPlanSuggestion> suggestions) {
        return suggestions == null ? List.of() : suggestions.stream()
                .sorted(Comparator.comparing(AgentReviewPlanSuggestion::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private int totalMinutes(List<AgentPlanTaskSnapshotDTO> tasks) {
        return tasks == null ? 0 : tasks.stream()
                .map(AgentPlanTaskSnapshotDTO::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int priorityRank(AgentPlanTaskSnapshotDTO task) {
        return switch (normalizeCode(task == null ? null : task.getPriority())) {
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    private boolean validTaskType(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return false;
        }
        try {
            AgentTaskTypeEnum.valueOf(normalizeCode(taskType));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String normalizePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return null;
        }
        try {
            return AgentTaskPriorityEnum.valueOf(normalizeCode(priority)).name();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : "";
    }

    private int safeMinutes(Integer value, int defaultValue) {
        int minutes = value == null ? defaultValue : value;
        return Math.max(5, Math.min(180, minutes));
    }

    private Integer positiveDelta(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private Integer firstInteger(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String safeActionUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") && !trimmed.startsWith("//") ? trimmed : null;
    }

    private String safeTitle(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未命名建议";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public record PlanningInput(
            AgentReview review,
            List<AgentReviewPlanSuggestion> suggestions,
            List<AgentTask> availableTasks,
            List<AgentTask> baselineTasks,
            List<AgentTask> targetTasks,
            AgentRun baseRun,
            AgentWeekPlan weekPlan,
            List<AgentWeekPlanItem> weekItems,
            LocalDate targetDate,
            Integer maxTotalMinutes,
            LocalDate currentDate) {
    }

    public record ItemDraft(
            Long suggestionId,
            String itemKey,
            String changeType,
            Long sourceTaskId,
            AgentPlanTaskSnapshotDTO before,
            AgentPlanTaskSnapshotDTO after,
            String dailyImpact,
            String weekImpact,
            String validationStatus,
            List<String> warningCodes,
            String confidenceLevel,
            Boolean fallback,
            String applyStatus) {
    }

    public record PlanningResult(
            List<ItemDraft> items,
            AgentPlanChangeSummaryVO summary,
            List<String> warnings,
            List<String> blockers,
            String resultSource,
            Boolean fallback,
            String baseDailyTaskHash,
            Long baseWeekPlanId,
            Integer baseWeekSnapshotVersion,
            String baseWeekItemHash) {
    }
}
