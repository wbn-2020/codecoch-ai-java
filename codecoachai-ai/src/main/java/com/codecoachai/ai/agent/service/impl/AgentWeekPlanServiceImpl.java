package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.dto.AgentWeekPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentContextUsageReference;
import com.codecoachai.ai.agent.domain.entity.AgentPlanAdjustment;
import com.codecoachai.ai.agent.domain.entity.AgentPlanInfluence;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.context.TargetJobContextVO;
import com.codecoachai.ai.agent.feign.ResumeAgentContextFeignClient;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentPlanAdjustmentVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentPlanInfluenceVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentWeekPlanItemVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentWeekPlanVO;
import com.codecoachai.ai.agent.mapper.AgentContextUsageReferenceMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanAdjustmentMapper;
import com.codecoachai.ai.agent.mapper.AgentPlanInfluenceMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanItemMapper;
import com.codecoachai.ai.agent.mapper.AgentWeekPlanMapper;
import com.codecoachai.ai.agent.service.AgentWeekPlanService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWeekPlanServiceImpl implements AgentWeekPlanService {

    private static final String PLAN_STATUS_ACTIVE = "ACTIVE";
    private static final String PLAN_STATUS_REFRESHED = "REFRESHED";
    private static final String RESULT_SOURCE_RULE = "RULE";
    private static final String TARGET_SCOPE_ALL = "ALL";
    private static final String LAYER_TODAY = "TODAY";
    private static final String LAYER_WEEK = "WEEK";
    private static final String LAYER_NEXT_EXPERIMENT = "NEXT_EXPERIMENT";
    private static final String TRUST_VERIFIED = "VERIFIED";
    private static final String TRUST_PARTIAL = "PARTIAL";
    private static final String TRUST_FALLBACK = "FALLBACK";
    private static final String CONSUMER_WEEK_PLAN_ITEM = "AGENT_WEEK_PLAN_ITEM";
    private static final String USAGE_SCENE_WEEK_PLAN = "WEEK_PLAN_GENERATION";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d\\s-]{7,}\\d)(?!\\d)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(api[_-]?key|authorization|token|secret|password)\\s*[:=]\\s*[^,\\s}]+");
    private static final List<String> WEEK_PLAN_TASK_STATUSES = List.of(
            AgentTaskStatusEnum.TODO.name(),
            AgentTaskStatusEnum.DOING.name(),
            AgentTaskStatusEnum.DEFERRED.name(),
            AgentTaskStatusEnum.EXPIRED.name(),
            AgentTaskStatusEnum.DONE.name(),
            AgentTaskStatusEnum.SKIPPED.name());

    private final AgentWeekPlanMapper weekPlanMapper;
    private final AgentWeekPlanItemMapper weekPlanItemMapper;
    private final AgentPlanAdjustmentMapper adjustmentMapper;
    private final AgentPlanInfluenceMapper influenceMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentRunMapper agentRunMapper;
    private final AgentContextUsageReferenceMapper usageReferenceMapper;
    private final ResumeAgentContextFeignClient resumeAgentContextFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWeekPlanVO current(Long userId, Long targetJobId, LocalDate date) {
        Long validatedTargetJobId = validateOwnedTargetJob(userId, targetJobId);
        LocalDate anchorDate = normalizeDate(date);
        LocalDate weekStart = weekStart(anchorDate);
        AgentWeekPlan existing = findPlan(userId, validatedTargetJobId, weekStart);
        if (existing != null) {
            return toVO(existing);
        }
        AgentWeekPlanGenerateDTO dto = new AgentWeekPlanGenerateDTO();
        dto.setTargetJobId(validatedTargetJobId);
        dto.setDate(anchorDate);
        dto.setForceRegenerate(false);
        return generate(userId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWeekPlanVO generate(Long userId, AgentWeekPlanGenerateDTO dto) {
        AgentWeekPlanGenerateDTO request = dto == null ? new AgentWeekPlanGenerateDTO() : dto;
        LocalDate anchorDate = normalizeDate(request.getDate());
        LocalDate weekStart = weekStart(anchorDate);
        Long targetJobId = validateOwnedTargetJob(userId, request.getTargetJobId());
        boolean force = Boolean.TRUE.equals(request.getForceRegenerate());
        AgentWeekPlan existing = findPlan(userId, targetJobId, weekStart);
        if (existing != null && !force) {
            return toVO(existing);
        }
        try {
            AgentWeekPlan plan = existing == null
                    ? createPlanSkeleton(userId, targetJobId, weekStart)
                    : existing;
            return rebuildPlan(plan, anchorDate, force ? "FORCE_REGENERATED" : "GENERATED", false);
        } catch (DuplicateKeyException ex) {
            AgentWeekPlan concurrent = findPlan(userId, targetJobId, weekStart);
            if (concurrent == null) {
                throw ex;
            }
            return force ? rebuildPlan(concurrent, anchorDate, "FORCE_REGENERATED", false) : toVO(concurrent);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWeekPlanVO refresh(Long userId, Long weekPlanId) {
        AgentWeekPlan plan = requirePlanForUpdate(userId, weekPlanId);
        String beforeStatus = plan.getPlanStatus();
        Integer beforeVersion = plan.getSnapshotVersion();
        AgentWeekPlanVO refreshed = rebuildPlan(plan, plan.getWeekStartDate(), "PLAN_REFRESHED", true);
        recordRefreshAdjustment(plan, beforeStatus, beforeVersion, refreshed);
        return refreshed;
    }

    @Override
    public AgentWeekPlanVO detail(Long userId, Long weekPlanId) {
        return toVO(requirePlan(userId, weekPlanId));
    }

    @Override
    public List<AgentPlanAdjustmentVO> adjustments(Long userId, Long weekPlanId) {
        requirePlan(userId, weekPlanId);
        return adjustmentMapper.selectList(new LambdaQueryWrapper<AgentPlanAdjustment>()
                        .eq(AgentPlanAdjustment::getUserId, userId)
                        .eq(AgentPlanAdjustment::getWeekPlanId, weekPlanId)
                        .eq(AgentPlanAdjustment::getDeleted, 0)
                        .orderByDesc(AgentPlanAdjustment::getOccurredAt)
                        .orderByDesc(AgentPlanAdjustment::getId)
                        .last("LIMIT 100"))
                .stream()
                .map(this::toAdjustmentVO)
                .toList();
    }

    @Override
    public List<AgentPlanInfluenceVO> influences(Long userId, Long weekPlanId) {
        requirePlan(userId, weekPlanId);
        return influenceMapper.selectList(new LambdaQueryWrapper<AgentPlanInfluence>()
                        .eq(AgentPlanInfluence::getUserId, userId)
                        .eq(AgentPlanInfluence::getWeekPlanId, weekPlanId)
                        .eq(AgentPlanInfluence::getDeleted, 0)
                        .orderByDesc(AgentPlanInfluence::getConfidence)
                        .orderByDesc(AgentPlanInfluence::getCreatedAt)
                        .last("LIMIT 200"))
                .stream()
                .map(this::toInfluenceVO)
                .toList();
    }

    @Override
    public void recordTaskAdjustment(Long userId, AgentTask before, AgentTask after, String adjustmentType, String reason) {
        if (userId == null || before == null || after == null || after.getId() == null) {
            return;
        }
        try {
            AgentWeekPlan plan = findPlanForTask(userId, after);
            if (plan == null) {
                return;
            }
            AgentWeekPlanItem item = findPlanItem(plan.getId(), userId, after.getId());
            if (item != null) {
                AgentRun run = loadRun(userId, after.getAgentRunId());
                String itemTraceId = run == null ? item.getTraceId() : firstText(run.getTraceId(), item.getTraceId());
                item.setItemStatus(toItemStatus(after.getStatus()));
                item.setDueDate(after.getDueDate());
                item.setPlannedDate(after.getDueDate());
                item.setTraceId(itemTraceId);
                item.setSnapshotVersion(valueOrDefault(plan.getSnapshotVersion(), valueOrDefault(item.getSnapshotVersion(), 1)));
                item.setConfidenceLevel(confidenceLevel(item.getConfidence()));
                boolean lowSample = isLowSampleItem(after, run, itemTraceId);
                item.setSampleInsufficient(lowSample ? 1 : 0);
                item.setSampleWarning(lowSample ? lowSampleWarning() : null);
                item.setFallbackReason(Boolean.TRUE.equals(isFallbackItem(after, run)) ? "Task has limited trace evidence" : null);
                weekPlanItemMapper.updateById(item);
            }
            AgentPlanAdjustment adjustment = new AgentPlanAdjustment();
            adjustment.setUserId(userId);
            adjustment.setWeekPlanId(plan.getId());
            adjustment.setWeekPlanItemId(item == null ? null : item.getId());
            adjustment.setAgentTaskId(after.getId());
            adjustment.setAdjustmentType(firstText(adjustmentType, "TASK_UPDATED"));
            adjustment.setFromStatus(before.getStatus());
            adjustment.setToStatus(after.getStatus());
            adjustment.setReason(safeAdjustmentReason(adjustmentType, reason));
            adjustment.setTraceId(plan.getTraceId());
            adjustment.setSnapshotVersion(plan.getSnapshotVersion());
            adjustment.setSourceType(firstText(after.getRelatedBizType(), "AGENT_TASK"));
            adjustment.setSourceId(after.getRelatedBizId() == null ? after.getId() : after.getRelatedBizId());
            adjustment.setOccurredAt(LocalDateTime.now());
            adjustment.setMetadataJson(toJson(safeTaskAdjustmentMetadata(before, after, reason)));
            adjustmentMapper.insert(adjustment);
        } catch (Exception ex) {
            log.warn("Agent week plan adjustment record skipped. userId={}, taskId={}, adjustmentType={}, error={}",
                    userId, after.getId(), adjustmentType, safeError(ex));
        }
    }

    private AgentWeekPlanVO rebuildPlan(AgentWeekPlan plan, LocalDate anchorDate, String reason, boolean refresh) {
        LocalDateTime now = LocalDateTime.now();
        List<AgentTask> tasks = weekTasks(plan.getUserId(), plan.getTargetJobId(), plan.getWeekStartDate(), plan.getWeekEndDate());
        Map<Long, AgentRun> runs = loadRuns(plan.getUserId(), tasks);
        AgentRun primaryRun = choosePrimaryRun(tasks, runs);
        List<AgentWeekPlanItem> items = toItems(plan, tasks, runs, anchorDate);
        boolean fallback = items.isEmpty() || items.stream().anyMatch(item -> Integer.valueOf(1).equals(item.getFallback()));

        if (plan.getId() == null) {
            plan.setGeneratedAt(now);
            plan.setSnapshotVersion(1);
            plan.setCreatedAt(now);
        } else if (refresh || "FORCE_REGENERATED".equals(reason)) {
            plan.setSnapshotVersion(Math.max(1, valueOrDefault(plan.getSnapshotVersion(), 1) + 1));
            plan.setRefreshedAt(now);
        }
        plan.setPlanStatus(refresh ? PLAN_STATUS_REFRESHED : PLAN_STATUS_ACTIVE);
        plan.setAgentRunId(primaryRun == null ? null : primaryRun.getId());
        plan.setTraceId(firstText(primaryRun == null ? null : primaryRun.getTraceId(), plan.getTraceId(), newTraceId()));
        plan.setResultSource(RESULT_SOURCE_RULE);
        plan.setFallback(fallback ? 1 : 0);
        plan.setFallbackReason(fallbackReason(items, tasks));
        plan.setSummary(planSummary(items, tasks, refresh));
        plan.setFocusJson(toJson(focusSnapshot(items, tasks, runs, reason)));
        plan.setUpdatedAt(now);

        if (plan.getId() == null) {
            weekPlanMapper.insert(plan);
        } else {
            weekPlanMapper.updateById(plan);
            weekPlanItemMapper.delete(new LambdaQueryWrapper<AgentWeekPlanItem>()
                    .eq(AgentWeekPlanItem::getWeekPlanId, plan.getId())
                    .eq(AgentWeekPlanItem::getUserId, plan.getUserId())
                    .eq(AgentWeekPlanItem::getDeleted, 0));
            influenceMapper.delete(new LambdaQueryWrapper<AgentPlanInfluence>()
                    .eq(AgentPlanInfluence::getWeekPlanId, plan.getId())
                    .eq(AgentPlanInfluence::getUserId, plan.getUserId())
                    .eq(AgentPlanInfluence::getDeleted, 0));
        }
        insertItemsAndInfluences(plan, items, tasks, runs);
        return toVO(plan);
    }

    private AgentWeekPlan createPlanSkeleton(Long userId, Long targetJobId, LocalDate weekStart) {
        AgentWeekPlan plan = new AgentWeekPlan();
        plan.setUserId(userId);
        plan.setTargetJobId(targetJobId);
        plan.setTargetScopeKey(targetScopeKey(targetJobId));
        plan.setWeekStartDate(weekStart);
        plan.setWeekEndDate(weekStart.plusDays(6));
        plan.setPlanStatus(PLAN_STATUS_ACTIVE);
        plan.setResultSource(RESULT_SOURCE_RULE);
        plan.setFallback(0);
        plan.setSnapshotVersion(1);
        plan.setGeneratedAt(LocalDateTime.now());
        return plan;
    }

    private void insertItemsAndInfluences(AgentWeekPlan plan, List<AgentWeekPlanItem> items,
                                          List<AgentTask> tasks, Map<Long, AgentRun> runs) {
        Map<Long, AgentTask> taskById = tasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(AgentTask::getId, task -> task, (left, right) -> left, LinkedHashMap::new));
        for (AgentWeekPlanItem item : items) {
            item.setWeekPlanId(plan.getId());
            weekPlanItemMapper.insert(item);
            AgentTask task = taskById.get(item.getAgentTaskId());
            AgentRun run = task == null ? null : runs.get(task.getAgentRunId());
            insertInfluences(plan, item, task, run);
        }
    }

    private void insertInfluences(AgentWeekPlan plan, AgentWeekPlanItem item, AgentTask task, AgentRun run) {
        if (task == null) {
            return;
        }
        AgentPlanInfluence direct = baseInfluence(plan, item, task, run);
        influenceMapper.insert(direct);
        for (AgentContextUsageReference reference : usageReferencesForTask(plan.getUserId(), task, run)) {
            AgentPlanInfluence influence = new AgentPlanInfluence();
            influence.setUserId(plan.getUserId());
            influence.setWeekPlanId(plan.getId());
            influence.setWeekPlanItemId(item.getId());
            influence.setSourceType(reference.getSourceType());
            influence.setSourceId(reference.getSourceId());
            influence.setSourceTitle(reference.getSourceType() + "#" + reference.getSourceId());
            influence.setConsumerType(CONSUMER_WEEK_PLAN_ITEM);
            influence.setConsumerId(item.getId());
            influence.setUsageReferenceId(reference.getId());
            influence.setUsageScene(firstText(reference.getUsageScene(), USAGE_SCENE_WEEK_PLAN));
            influence.setInfluenceStrength(firstText(reference.getUsageStrength(), influenceStrength(task)));
            influence.setConfidence(scale(reference.getConfidence() == null ? 0.60 : reference.getConfidence().doubleValue()));
            influence.setTraceId(firstText(reference.getTraceId(), plan.getTraceId()));
            influence.setSnapshotVersion(valueOrDefault(plan.getSnapshotVersion(), 1));
            influence.setSnapshotHash(reference.getSnapshotHash());
            influence.setFallback(Integer.valueOf(1).equals(item.getFallback()) ? 1 : 0);
            influenceMapper.insert(influence);
        }
    }

    private AgentPlanInfluence baseInfluence(AgentWeekPlan plan, AgentWeekPlanItem item, AgentTask task, AgentRun run) {
        String sourceType = firstText(task.getRelatedBizType(), "AGENT_TASK");
        Long sourceId = task.getRelatedBizId() == null ? task.getId() : task.getRelatedBizId();
        AgentPlanInfluence influence = new AgentPlanInfluence();
        influence.setUserId(plan.getUserId());
        influence.setWeekPlanId(plan.getId());
        influence.setWeekPlanItemId(item.getId());
        influence.setSourceType(sourceType);
        influence.setSourceId(sourceId);
        influence.setSourceTitle(safeText(firstText(item.getRelatedBizTitle(), task.getRelatedBizType(), task.getTaskType(), "Agent task"), 180));
        influence.setConsumerType(CONSUMER_WEEK_PLAN_ITEM);
        influence.setConsumerId(item.getId());
        influence.setUsageScene(USAGE_SCENE_WEEK_PLAN);
        influence.setInfluenceStrength(influenceStrength(task));
        influence.setConfidence(item.getConfidence());
        influence.setTraceId(firstText(item.getTraceId(), run == null ? null : run.getTraceId(), plan.getTraceId()));
        influence.setSnapshotVersion(valueOrDefault(plan.getSnapshotVersion(), 1));
        influence.setSnapshotHash(hashOf(sourceType + "#" + sourceId + ":" + task.getId() + ":" + item.getItemStatus()));
        influence.setFallback(Integer.valueOf(1).equals(item.getFallback()) ? 1 : 0);
        return influence;
    }

    private List<AgentTask> weekTasks(Long userId, Long targetJobId, LocalDate weekStart, LocalDate weekEnd) {
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                        .eq(AgentTask::getUserId, userId)
                        .eq(targetJobId != null, AgentTask::getTargetJobId, targetJobId)
                        .eq(AgentTask::getDeleted, 0)
                        .in(AgentTask::getStatus, WEEK_PLAN_TASK_STATUSES)
                        .and(wrapper -> wrapper
                                .between(AgentTask::getDueDate, weekStart, weekEnd)
                                .or()
                                .in(AgentTask::getStatus, AgentTaskStatusEnum.TODO.name(),
                                        AgentTaskStatusEnum.DOING.name(),
                                        AgentTaskStatusEnum.DEFERRED.name()))
                        .orderByAsc(AgentTask::getDueDate)
                        .orderByAsc(AgentTask::getSortOrder)
                        .orderByDesc(AgentTask::getId)
                        .last("LIMIT 50"))
                .stream()
                .sorted(taskComparator(weekStart))
                .toList();
    }

    private Comparator<AgentTask> taskComparator(LocalDate weekStart) {
        return Comparator
                .comparing((AgentTask task) -> task.getDueDate() == null ? weekStart.plusDays(7) : task.getDueDate())
                .thenComparing(task -> valueOrDefault(task.getSortOrder(), 0))
                .thenComparing(task -> task.getId() == null ? Long.MAX_VALUE : task.getId());
    }

    private Map<Long, AgentRun> loadRuns(Long userId, List<AgentTask> tasks) {
        Set<Long> runIds = tasks.stream()
                .map(AgentTask::getAgentRunId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (runIds.isEmpty()) {
            return Map.of();
        }
        return agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                        .in(AgentRun::getId, runIds)
                        .eq(AgentRun::getUserId, userId)
                        .eq(AgentRun::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(AgentRun::getId, run -> run, (left, right) -> left, LinkedHashMap::new));
    }

    private AgentRun loadRun(Long userId, Long runId) {
        if (userId == null || runId == null) {
            return null;
        }
        return agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getId, runId)
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private AgentRun choosePrimaryRun(List<AgentTask> tasks, Map<Long, AgentRun> runs) {
        for (AgentTask task : tasks) {
            AgentRun run = runs.get(task.getAgentRunId());
            if (run != null && StringUtils.hasText(run.getTraceId())) {
                return run;
            }
        }
        return runs.values().stream().findFirst().orElse(null);
    }

    private List<AgentWeekPlanItem> toItems(AgentWeekPlan plan, List<AgentTask> tasks,
                                            Map<Long, AgentRun> runs, LocalDate anchorDate) {
        List<AgentWeekPlanItem> items = new ArrayList<>();
        int index = 0;
        for (AgentTask task : tasks) {
            AgentRun run = runs.get(task.getAgentRunId());
            AgentWeekPlanItem item = new AgentWeekPlanItem();
            String itemTraceId = run == null ? null : run.getTraceId();
            BigDecimal itemConfidence = confidence(task, run);
            boolean lowSample = isLowSampleItem(task, run, itemTraceId);
            item.setUserId(plan.getUserId());
            item.setLayer(layerFor(task, anchorDate));
            item.setActionType(actionType(task));
            item.setTitle(safeWeekPlanTitle(task));
            item.setDescription(safeWeekPlanDescription(task));
            item.setReason(safeDefaultReason(task));
            item.setRelatedBizType(task.getRelatedBizType());
            item.setRelatedBizId(task.getRelatedBizId());
            item.setRelatedBizTitle(safeText(firstText(task.getRelatedSkillName(), task.getRelatedBizType(), task.getTaskType()), 180));
            item.setAgentTaskId(task.getId());
            item.setPriority(firstText(task.getPriority(), "MEDIUM"));
            item.setConfidence(itemConfidence);
            item.setConfidenceLevel(confidenceLevel(itemConfidence));
            item.setTrustStatus(trustStatus(task, run));
            item.setFallback(isFallbackItem(task, run) ? 1 : 0);
            item.setFallbackReason(isFallbackItem(task, run) ? "Task has limited trace or source evidence" : null);
            item.setTraceId(itemTraceId);
            item.setSnapshotVersion(valueOrDefault(plan.getSnapshotVersion(), 1));
            item.setSampleInsufficient(lowSample ? 1 : 0);
            item.setSampleWarning(lowSample ? lowSampleWarning() : null);
            item.setItemStatus(toItemStatus(task.getStatus()));
            item.setPlannedDate(task.getDueDate());
            item.setDueDate(task.getDueDate());
            item.setActionUrl(safeText(task.getActionUrl(), 500));
            item.setEvidenceJson(toJson(itemEvidence(task, run)));
            item.setSortOrder(index++);
            items.add(item);
        }
        return items;
    }

    private List<AgentContextUsageReference> usageReferencesForTask(Long userId, AgentTask task, AgentRun run) {
        if (run == null || !StringUtils.hasText(run.getTraceId())) {
            return List.of();
        }
        return usageReferenceMapper.selectList(new LambdaQueryWrapper<AgentContextUsageReference>()
                .eq(AgentContextUsageReference::getUserId, userId)
                .eq(AgentContextUsageReference::getTraceId, run.getTraceId())
                .eq(AgentContextUsageReference::getDeleted, 0)
                .orderByDesc(AgentContextUsageReference::getConfidence)
                .orderByDesc(AgentContextUsageReference::getCreatedAt)
                .last("LIMIT 5"));
    }

    private AgentWeekPlan findPlanForTask(Long userId, AgentTask task) {
        LocalDate date = task.getDueDate() == null ? LocalDate.now() : task.getDueDate();
        LocalDate weekStart = weekStart(date);
        AgentWeekPlan exact = findPlan(userId, task.getTargetJobId(), weekStart);
        if (exact != null) {
            return exact;
        }
        return task.getTargetJobId() == null ? null : findPlan(userId, null, weekStart);
    }

    private AgentWeekPlan findPlan(Long userId, Long targetJobId, LocalDate weekStart) {
        if (userId == null || weekStart == null) {
            return null;
        }
        return weekPlanMapper.selectOne(new LambdaQueryWrapper<AgentWeekPlan>()
                .eq(AgentWeekPlan::getUserId, userId)
                .eq(AgentWeekPlan::getTargetScopeKey, targetScopeKey(targetJobId))
                .eq(AgentWeekPlan::getWeekStartDate, weekStart)
                .eq(AgentWeekPlan::getDeleted, 0)
                .orderByDesc(AgentWeekPlan::getUpdatedAt)
                .last("LIMIT 1"));
    }

    private AgentWeekPlan requirePlan(Long userId, Long weekPlanId) {
        AgentWeekPlan plan = weekPlanMapper.selectById(weekPlanId);
        if (plan == null || !userId.equals(plan.getUserId()) || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Agent week plan not found");
        }
        return plan;
    }

    private AgentWeekPlan requirePlanForUpdate(Long userId, Long weekPlanId) {
        AgentWeekPlan plan = weekPlanMapper.selectOne(new LambdaQueryWrapper<AgentWeekPlan>()
                .eq(AgentWeekPlan::getId, weekPlanId)
                .eq(AgentWeekPlan::getUserId, userId)
                .eq(AgentWeekPlan::getDeleted, 0)
                .last("LIMIT 1 FOR UPDATE"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Agent week plan not found");
        }
        return plan;
    }

    private AgentWeekPlanItem findPlanItem(Long weekPlanId, Long userId, Long taskId) {
        return weekPlanItemMapper.selectOne(new LambdaQueryWrapper<AgentWeekPlanItem>()
                .eq(AgentWeekPlanItem::getWeekPlanId, weekPlanId)
                .eq(AgentWeekPlanItem::getUserId, userId)
                .eq(AgentWeekPlanItem::getAgentTaskId, taskId)
                .eq(AgentWeekPlanItem::getDeleted, 0)
                .orderByDesc(AgentWeekPlanItem::getId)
                .last("LIMIT 1"));
    }

    private void recordRefreshAdjustment(AgentWeekPlan plan, String beforeStatus, Integer beforeVersion, AgentWeekPlanVO refreshed) {
        AgentPlanAdjustment adjustment = new AgentPlanAdjustment();
        adjustment.setUserId(plan.getUserId());
        adjustment.setWeekPlanId(plan.getId());
        adjustment.setAdjustmentType("PLAN_REFRESHED");
        adjustment.setFromStatus(beforeStatus);
        adjustment.setToStatus(refreshed.getPlanStatus());
        adjustment.setReason("Week plan snapshot refreshed from current Agent tasks and influence references.");
        adjustment.setTraceId(refreshed.getTraceId());
        adjustment.setSnapshotVersion(refreshed.getSnapshotVersion());
        adjustment.setSourceType("AGENT_WEEK_PLAN");
        adjustment.setSourceId(plan.getId());
        adjustment.setOccurredAt(LocalDateTime.now());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousSnapshotVersion", valueOrDefault(beforeVersion, 1));
        metadata.put("snapshotVersion", refreshed.getSnapshotVersion());
        metadata.put("itemCount", refreshed.getItems() == null ? 0 : refreshed.getItems().size());
        metadata.put("fallback", Boolean.TRUE.equals(refreshed.getFallback()));
        adjustment.setMetadataJson(toJson(metadata));
        adjustmentMapper.insert(adjustment);
    }

    private AgentWeekPlanVO toVO(AgentWeekPlan plan) {
        AgentWeekPlanVO vo = new AgentWeekPlanVO();
        vo.setId(plan.getId());
        vo.setTargetJobId(plan.getTargetJobId());
        vo.setAgentRunId(plan.getAgentRunId());
        vo.setPlanDate(plan.getWeekStartDate());
        vo.setWeekStartDate(plan.getWeekStartDate());
        vo.setWeekEndDate(plan.getWeekEndDate());
        vo.setPlanStatus(plan.getPlanStatus());
        vo.setSummary(plan.getSummary());
        vo.setFocusJson(plan.getFocusJson());
        vo.setTraceId(plan.getTraceId());
        vo.setResultSource(plan.getResultSource());
        vo.setFallback(Integer.valueOf(1).equals(plan.getFallback()));
        vo.setFallbackReason(plan.getFallbackReason());
        vo.setSnapshotVersion(plan.getSnapshotVersion());
        vo.setGeneratedAt(plan.getGeneratedAt());
        vo.setRefreshedAt(plan.getRefreshedAt());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());
        vo.setItems(weekPlanItemMapper.selectList(new LambdaQueryWrapper<AgentWeekPlanItem>()
                        .eq(AgentWeekPlanItem::getWeekPlanId, plan.getId())
                        .eq(AgentWeekPlanItem::getUserId, plan.getUserId())
                        .eq(AgentWeekPlanItem::getDeleted, 0)
                        .orderByAsc(AgentWeekPlanItem::getPlannedDate)
                        .orderByAsc(AgentWeekPlanItem::getSortOrder)
                        .orderByAsc(AgentWeekPlanItem::getId))
                .stream()
                .map(this::toItemVO)
                .toList());
        return vo;
    }

    private AgentWeekPlanItemVO toItemVO(AgentWeekPlanItem item) {
        AgentWeekPlanItemVO vo = new AgentWeekPlanItemVO();
        vo.setId(item.getId());
        vo.setWeekPlanId(item.getWeekPlanId());
        vo.setLayer(item.getLayer());
        vo.setActionType(item.getActionType());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setReason(item.getReason());
        vo.setRelatedBizType(item.getRelatedBizType());
        vo.setRelatedBizId(item.getRelatedBizId());
        vo.setRelatedBizTitle(item.getRelatedBizTitle());
        vo.setAgentTaskId(item.getAgentTaskId());
        vo.setPriority(item.getPriority());
        vo.setConfidence(item.getConfidence());
        vo.setConfidenceLevel(item.getConfidenceLevel());
        vo.setTrustStatus(item.getTrustStatus());
        vo.setFallback(Integer.valueOf(1).equals(item.getFallback()));
        vo.setFallbackReason(item.getFallbackReason());
        vo.setTraceId(item.getTraceId());
        vo.setSnapshotVersion(item.getSnapshotVersion());
        vo.setSampleInsufficient(Integer.valueOf(1).equals(item.getSampleInsufficient()));
        vo.setSampleWarning(item.getSampleWarning());
        vo.setItemStatus(item.getItemStatus());
        vo.setPlannedDate(item.getPlannedDate());
        vo.setDueDate(item.getDueDate());
        vo.setActionUrl(item.getActionUrl());
        vo.setEvidence(parseEvidence(item.getEvidenceJson()));
        vo.setSortOrder(item.getSortOrder());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private AgentPlanAdjustmentVO toAdjustmentVO(AgentPlanAdjustment adjustment) {
        AgentPlanAdjustmentVO vo = new AgentPlanAdjustmentVO();
        vo.setId(adjustment.getId());
        vo.setWeekPlanId(adjustment.getWeekPlanId());
        vo.setWeekPlanItemId(adjustment.getWeekPlanItemId());
        vo.setAgentTaskId(adjustment.getAgentTaskId());
        vo.setAdjustmentType(adjustment.getAdjustmentType());
        vo.setFromStatus(adjustment.getFromStatus());
        vo.setToStatus(adjustment.getToStatus());
        vo.setReason(adjustment.getReason());
        vo.setTraceId(adjustment.getTraceId());
        vo.setSnapshotVersion(adjustment.getSnapshotVersion());
        vo.setSourceType(adjustment.getSourceType());
        vo.setSourceId(adjustment.getSourceId());
        vo.setOccurredAt(adjustment.getOccurredAt());
        vo.setMetadataJson(adjustment.getMetadataJson());
        vo.setCreatedAt(adjustment.getCreatedAt());
        return vo;
    }

    private AgentPlanInfluenceVO toInfluenceVO(AgentPlanInfluence influence) {
        AgentPlanInfluenceVO vo = new AgentPlanInfluenceVO();
        vo.setId(influence.getId());
        vo.setWeekPlanId(influence.getWeekPlanId());
        vo.setWeekPlanItemId(influence.getWeekPlanItemId());
        vo.setSourceType(influence.getSourceType());
        vo.setSourceId(influence.getSourceId());
        vo.setSourceTitle(influence.getSourceTitle());
        vo.setConsumerType(influence.getConsumerType());
        vo.setConsumerId(influence.getConsumerId());
        vo.setUsageReferenceId(influence.getUsageReferenceId());
        vo.setUsageScene(influence.getUsageScene());
        vo.setInfluenceStrength(influence.getInfluenceStrength());
        vo.setConfidence(influence.getConfidence());
        vo.setTraceId(influence.getTraceId());
        vo.setSnapshotVersion(influence.getSnapshotVersion());
        vo.setSnapshotHash(influence.getSnapshotHash());
        vo.setFallback(Integer.valueOf(1).equals(influence.getFallback()));
        vo.setCreatedAt(influence.getCreatedAt());
        return vo;
    }

    private Map<String, Object> focusSnapshot(List<AgentWeekPlanItem> items, List<AgentTask> tasks,
                                              Map<Long, AgentRun> runs, String reason) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("reason", reason);
        snapshot.put("itemCount", items.size());
        snapshot.put("taskCount", tasks.size());
        snapshot.put("statusCounts", countBy(items, AgentWeekPlanItem::getItemStatus));
        snapshot.put("layerCounts", countBy(items, AgentWeekPlanItem::getLayer));
        snapshot.put("sourceCounts", countBy(items, item -> firstText(item.getRelatedBizType(), "AGENT_TASK")));
        snapshot.put("traceCount", runs.values().stream().map(AgentRun::getTraceId).filter(StringUtils::hasText).distinct().count());
        snapshot.put("fallbackCount", items.stream().filter(item -> Integer.valueOf(1).equals(item.getFallback())).count());
        return snapshot;
    }

    private Map<String, Long> countBy(Collection<AgentWeekPlanItem> items, java.util.function.Function<AgentWeekPlanItem, String> classifier) {
        return items.stream()
                .map(classifier)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Object> safeTaskAdjustmentMetadata(AgentTask before, AgentTask after, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", after.getId());
        metadata.put("taskType", after.getTaskType());
        metadata.put("targetJobId", after.getTargetJobId());
        metadata.put("relatedBizType", after.getRelatedBizType());
        metadata.put("relatedBizId", after.getRelatedBizId());
        metadata.put("beforeDueDate", before.getDueDate());
        metadata.put("afterDueDate", after.getDueDate());
        metadata.put("titleLength", after.getTitle() == null ? 0 : after.getTitle().length());
        metadata.put("titleHash", hashOf(after.getTitle()));
        metadata.put("reasonLength", reason == null ? 0 : reason.length());
        metadata.put("reasonHash", hashOf(reason));
        return metadata;
    }

    private Map<String, Object> itemEvidence(AgentTask task, AgentRun run) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("agentTaskId", task.getId());
        evidence.put("agentRunId", task.getAgentRunId());
        evidence.put("traceId", run == null ? null : run.getTraceId());
        evidence.put("resultSource", run == null ? null : run.getResultSource());
        evidence.put("taskType", task.getTaskType());
        evidence.put("status", task.getStatus());
        evidence.put("relatedBizType", task.getRelatedBizType());
        evidence.put("relatedBizId", task.getRelatedBizId());
        evidence.put("titleHash", hashOf(task.getTitle()));
        evidence.put("titleLength", task.getTitle() == null ? 0 : task.getTitle().length());
        evidence.put("reasonHash", hashOf(task.getReason()));
        evidence.put("reasonLength", task.getReason() == null ? 0 : task.getReason().length());
        return evidence;
    }

    private List<String> parseEvidence(String evidenceJson) {
        if (!StringUtils.hasText(evidenceJson)) {
            return List.of();
        }
        try {
            Map<?, ?> evidence = objectMapper.readValue(evidenceJson, Map.class);
            List<String> lines = new ArrayList<>();
            appendEvidence(lines, "task", evidence.get("agentTaskId"));
            appendEvidence(lines, "run", evidence.get("agentRunId"));
            appendEvidence(lines, "trace", evidence.get("traceId"));
            appendEvidence(lines, "source", evidence.get("relatedBizType"));
            appendEvidence(lines, "resultSource", evidence.get("resultSource"));
            appendEvidence(lines, "titleHash", evidence.get("titleHash"));
            return lines;
        } catch (Exception ex) {
            return List.of("Evidence summary unavailable");
        }
    }

    private void appendEvidence(List<String> lines, String label, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            lines.add(label + "=" + safeText(String.valueOf(value), 80));
        }
    }

    private String planSummary(List<AgentWeekPlanItem> items, List<AgentTask> tasks, boolean refresh) {
        if (items.isEmpty()) {
            return refresh
                    ? "Week plan refreshed, but no active Agent tasks were available for this week."
                    : "Week plan created from current context, but no active Agent tasks were available for this week.";
        }
        long todayCount = items.stream().filter(item -> LAYER_TODAY.equals(item.getLayer())).count();
        long experimentCount = items.stream().filter(item -> LAYER_NEXT_EXPERIMENT.equals(item.getLayer())).count();
        return "Week plan snapshot contains " + items.size() + " items, including "
                + todayCount + " today item(s) and " + experimentCount
                + " next-experiment observation item(s).";
    }

    private String fallbackReason(List<AgentWeekPlanItem> items, List<AgentTask> tasks) {
        if (items.isEmpty()) {
            return "No persisted Agent tasks were found for the selected week and scope.";
        }
        long fallbackItems = items.stream().filter(item -> Integer.valueOf(1).equals(item.getFallback())).count();
        if (fallbackItems <= 0) {
            return null;
        }
        return fallbackItems + " plan item(s) have limited trace/source evidence and should be treated as weak coaching observations.";
    }

    private String layerFor(AgentTask task, LocalDate anchorDate) {
        if (task.getDueDate() != null && task.getDueDate().equals(anchorDate)) {
            return LAYER_TODAY;
        }
        String text = (firstText(task.getTaskType(), task.getRelatedBizType(), task.getTitle(), "")).toUpperCase(Locale.ROOT);
        if (text.contains("EXPERIMENT")) {
            return LAYER_NEXT_EXPERIMENT;
        }
        return LAYER_WEEK;
    }

    private String actionType(AgentTask task) {
        String type = firstText(task.getTaskType(), task.getRelatedBizType(), "OPEN").trim().toUpperCase(Locale.ROOT);
        if (type.contains("QUESTION") || type.contains("PRACTICE") || type.contains("REVIEW")) {
            return "PRACTICE";
        }
        if (type.contains("INTERVIEW")) {
            return "INTERVIEW";
        }
        if (type.contains("RESUME")) {
            return "RESUME";
        }
        if (type.contains("APPLICATION") || type.contains("FOLLOW")) {
            return "FOLLOW_UP";
        }
        return StringUtils.hasText(task.getActionUrl()) ? "OPEN" : "AGENT_TASK";
    }

    private BigDecimal confidence(AgentTask task, AgentRun run) {
        if (isExperimentSignal(task)) {
            return scale(0.45);
        }
        if (run != null && StringUtils.hasText(run.getTraceId()) && StringUtils.hasText(task.getRelatedBizType())) {
            return scale(0.80);
        }
        if (run != null || StringUtils.hasText(task.getRelatedBizType())) {
            return scale(0.60);
        }
        return scale(0.40);
    }

    private String trustStatus(AgentTask task, AgentRun run) {
        if (isFallbackItem(task, run)) {
            return TRUST_FALLBACK;
        }
        if (run != null && StringUtils.hasText(run.getTraceId()) && StringUtils.hasText(task.getRelatedBizType())) {
            return TRUST_VERIFIED;
        }
        return TRUST_PARTIAL;
    }

    private Boolean isFallbackItem(AgentTask task, AgentRun run) {
        return run == null
                || !StringUtils.hasText(run.getTraceId())
                || containsFallback(run.getResultSource())
                || AgentTaskStatusEnum.EXPIRED.name().equals(task.getStatus());
    }

    private String influenceStrength(AgentTask task) {
        if (isExperimentSignal(task) || AgentTaskStatusEnum.SKIPPED.name().equals(task.getStatus())
                || AgentTaskStatusEnum.DEFERRED.name().equals(task.getStatus())) {
            return "WEAK";
        }
        if (StringUtils.hasText(task.getRelatedBizType()) && task.getRelatedBizId() != null) {
            return "MEDIUM";
        }
        return "WEAK";
    }

    private boolean isExperimentSignal(AgentTask task) {
        String text = firstText(task.getTaskType(), task.getRelatedBizType(), task.getTitle(), "").toUpperCase(Locale.ROOT);
        return text.contains("EXPERIMENT");
    }

    private String toItemStatus(String taskStatus) {
        if (AgentTaskStatusEnum.DOING.name().equals(taskStatus)) {
            return "IN_PROGRESS";
        }
        if (AgentTaskStatusEnum.DONE.name().equals(taskStatus)
                || AgentTaskStatusEnum.SKIPPED.name().equals(taskStatus)
                || AgentTaskStatusEnum.DEFERRED.name().equals(taskStatus)) {
            return taskStatus;
        }
        if (AgentTaskStatusEnum.EXPIRED.name().equals(taskStatus)) {
            return "CANCELLED";
        }
        return "TODO";
    }

    private String confidenceLevel(BigDecimal confidence) {
        if (confidence == null) {
            return "UNKNOWN";
        }
        if (confidence.compareTo(BigDecimal.valueOf(0.75)) >= 0) {
            return "HIGH";
        }
        if (confidence.compareTo(BigDecimal.valueOf(0.50)) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean isLowSampleItem(AgentTask task, AgentRun run, String itemTraceId) {
        return isExperimentSignal(task) || run == null || !StringUtils.hasText(itemTraceId);
    }

    private String lowSampleWarning() {
        return "Low-sample or limited trace evidence; treat as weak coaching observation.";
    }

    private String safeWeekPlanTitle(AgentTask task) {
        String label = firstText(task.getTaskType(), task.getRelatedBizType(), "AGENT_TASK");
        String idPart = task.getId() == null ? "" : "#" + task.getId() + " ";
        return safeText("Agent task " + idPart + label, 240);
    }

    private String safeWeekPlanDescription(AgentTask task) {
        String source = firstText(task.getRelatedBizType(), task.getTaskType(), "Agent task");
        return safeText("Derived from " + source + " status and safe source metadata.", 500);
    }

    private String safeAdjustmentReason(String adjustmentType, String reason) {
        String type = firstText(adjustmentType, "TASK_UPDATED");
        if (StringUtils.hasText(reason)) {
            return safeText(type + " recorded; user note retained only as hash and length in metadata.", 500);
        }
        return safeText(type + " recorded from Agent task state change.", 500);
    }

    private String safeDefaultReason(AgentTask task) {
        if (StringUtils.hasText(task.getRelatedBizType())) {
            return "Derived from " + task.getRelatedBizType() + " signal.";
        }
        return "Derived from persisted Agent task status.";
    }

    private LocalDate normalizeDate(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }

    private LocalDate weekStart(LocalDate date) {
        return normalizeDate(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String targetScopeKey(Long targetJobId) {
        return targetJobId == null ? TARGET_SCOPE_ALL : String.valueOf(targetJobId);
    }

    private Long validateOwnedTargetJob(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return null;
        }
        try {
            Result<TargetJobContextVO> result = resumeAgentContextFeignClient.getTargetJob(userId, targetJobId);
            TargetJobContextVO target = result == null ? null : result.getData();
            if (target == null
                    || !Objects.equals(targetJobId, target.getId())
                    || !Objects.equals(userId, target.getUserId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不存在或无权访问");
            }
            return targetJobId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Agent week plan target validation failed userId={} targetJobId={} error={}",
                    userId, targetJobId, safeError(ex));
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标岗位不存在或无权访问");
        }
    }

    private String newTraceId() {
        return "week-plan-" + UUID.randomUUID();
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent week plan serialization failed");
        }
    }

    private String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String masked = EMAIL_PATTERN.matcher(value).replaceAll("[email]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[phone]");
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1=[secret]");
        masked = masked.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (masked.length() <= maxLength) {
            return masked;
        }
        return masked.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String hashOf(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean containsFallback(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("fallback") || normalized.contains("mock") || normalized.contains("degraded");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String safeError(Exception ex) {
        return safeText(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 160);
    }
}
