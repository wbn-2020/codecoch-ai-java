package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.ai.agent.domain.dto.AgentPlanChangeConfirmDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanSuggestionIntentDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewPlanDecisionDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewPlanDecisionItemDTO;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeItem;
import com.codecoachai.ai.agent.domain.entity.AgentPlanChangeSet;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanDecisionRequest;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlan;
import com.codecoachai.ai.agent.domain.entity.AgentWeekPlanItem;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskPriorityEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeConfirmVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeItemVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeSummaryVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanDecisionSummaryVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanSuggestionListVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanSuggestionVO;
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
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.ai.agent.service.support.AgentPlanChangeJsonCodec;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentReviewPlanServiceImpl implements AgentReviewPlanService {

    private static final String REVIEW_TYPE_DAILY = "DAILY";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_IGNORED = "IGNORED";
    private static final String STATUS_SUPERSEDED = "SUPERSEDED";
    private static final String CHANGE_SET_PREVIEW_READY = "PREVIEW_READY";
    private static final String CHANGE_SET_STALE = "STALE";
    private static final String APPLY_SKIPPED_DUPLICATE = "SKIPPED_DUPLICATE";
    private static final int PREVIEW_EXPIRES_MINUTES = 30;
    private static final int MAX_SUGGESTIONS = 3;
    private static final Set<String> QUERYABLE_CHANGE_SET_STATUSES = Set.of(
            "PREVIEW_READY", "STALE", "CANCELLED", "CONFIRMED_WAITING_PLAN",
            "APPLYING", "APPLIED", "PARTIALLY_APPLIED", "APPLY_FAILED");

    private final AgentReviewMapper reviewMapper;
    private final AgentReviewPlanSuggestionMapper suggestionMapper;
    private final AgentReviewPlanDecisionRequestMapper decisionRequestMapper;
    private final AgentPlanChangeSetMapper changeSetMapper;
    private final AgentPlanChangeItemMapper changeItemMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentRunMapper runMapper;
    private final AgentWeekPlanMapper weekPlanMapper;
    private final AgentWeekPlanItemMapper weekPlanItemMapper;
    private final AgentPlanPreviewPlanner previewPlanner;
    private final AgentPlanSourceAdapter sourceAdapter;
    private final AgentPlanPreviewPersistenceService previewPersistenceService;
    private final AgentPlanChangeApplyService applyService;
    private final AgentPlanChangeJsonCodec jsonCodec;
    private final AgentBusinessTimeProvider timeProvider;

    @Override
    public AgentReviewPlanSuggestionListVO suggestions(Long userId, Long reviewId) {
        AgentReview review = requireDailyReview(userId, reviewId);
        return suggestionListVO(review, loadSuggestions(userId, reviewId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentReviewPlanSuggestionListVO decide(Long userId, Long reviewId, AgentReviewPlanDecisionDTO dto) {
        requireDecisionRequest(dto);
        List<AgentReviewPlanDecisionItemDTO> decisions = dto.getDecisions().stream()
                .sorted(Comparator.comparing(AgentReviewPlanDecisionItemDTO::getSuggestionId))
                .toList();
        Set<Long> ids = decisions.stream().map(AgentReviewPlanDecisionItemDTO::getSuggestionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() != decisions.size()) {
            throw validation("同一批请求中不能重复提交相同建议。");
        }
        String requestKeyHash = AgentAdaptivePlanHashUtils.sha256(dto.getIdempotencyKey().trim());
        String payloadHash = decisionPayloadHash(reviewId, dto.getExpectedReviewVersion(), decisions);
        boolean claimed;
        try {
            decisionRequestMapper.insertIdempotencyRequest(
                    userId,
                    reviewId,
                    requestKeyHash,
                    payloadHash,
                    safeText(dto.getRequestId(), 128));
            claimed = true;
        } catch (DuplicateKeyException ignored) {
            claimed = false;
        }
        AgentReviewPlanDecisionRequest request = decisionRequestMapper.selectByUserAndKeyForUpdate(
                userId, requestKeyHash);
        if (request == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    AgentErrorCode.PLAN_CHANGE_TEMPORARILY_UNAVAILABLE + "：建议决策幂等状态暂不可用，请稍后重试。");
        }
        if (!Objects.equals(request.getReviewId(), reviewId)
                || !Objects.equals(request.getDecisionPayloadHash(), payloadHash)) {
            throw conflict(AgentErrorCode.IDEMPOTENCY_KEY_REUSED, "同一幂等键不能用于不同的建议决策请求。");
        }
        if (!claimed) {
            AgentReview replayReview = requireDailyReview(userId, reviewId);
            return suggestionListVO(replayReview, loadSuggestions(userId, reviewId));
        }

        AgentReview review = reviewMapper.selectOwnedForUpdate(userId, reviewId);
        requireDailyReview(review, userId);
        if (!Objects.equals(review.getReviewVersion(), dto.getExpectedReviewVersion())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "复盘版本已变化，请刷新后重新决策。");
        }
        List<AgentReviewPlanSuggestion> suggestions = suggestionMapper.selectList(
                new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                        .eq(AgentReviewPlanSuggestion::getUserId, userId)
                        .eq(AgentReviewPlanSuggestion::getReviewId, reviewId)
                        .in(AgentReviewPlanSuggestion::getId, ids)
                        .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                        .orderByAsc(AgentReviewPlanSuggestion::getId)
                        .last("FOR UPDATE"));
        if (suggestions.size() != ids.size()) {
            throw forbidden("存在不属于当前用户或当前复盘的建议。");
        }
        Map<Long, AgentReviewPlanSuggestion> byId = suggestions.stream()
                .collect(Collectors.toMap(AgentReviewPlanSuggestion::getId, Function.identity()));
        boolean changed = false;
        LocalDateTime now = timeProvider.now();
        for (AgentReviewPlanDecisionItemDTO decision : decisions) {
            AgentReviewPlanSuggestion suggestion = byId.get(decision.getSuggestionId());
            changed |= applyDecision(suggestion, decision, now);
        }
        if (changed) {
            changeSetMapper.update(null, new LambdaUpdateWrapper<AgentPlanChangeSet>()
                    .eq(AgentPlanChangeSet::getUserId, userId)
                    .eq(AgentPlanChangeSet::getReviewId, reviewId)
                    .eq(AgentPlanChangeSet::getStatus, CHANGE_SET_PREVIEW_READY)
                    .eq(AgentPlanChangeSet::getDeleted, 0)
                    .set(AgentPlanChangeSet::getStatus, CHANGE_SET_STALE)
                    .set(AgentPlanChangeSet::getFailureCode, AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE)
                    .set(AgentPlanChangeSet::getFailureMessage, "建议决策已变化，需要重新生成预览。")
                    .set(AgentPlanChangeSet::getUpdatedAt, now));
        }
        return suggestionListVO(review, loadSuggestions(userId, reviewId));
    }

    @Override
    public AgentPlanChangePreviewVO preview(Long userId, Long reviewId, AgentPlanChangePreviewDTO dto) {
        requirePreviewRequest(dto);
        AgentReview review = requireDailyReview(userId, reviewId);
        if (!Objects.equals(review.getReviewVersion(), dto.getExpectedReviewVersion())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE, "复盘版本已变化，请刷新后重新生成预览。");
        }
        List<Long> selectedIds = dto.getAcceptedSuggestionIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (selectedIds.size() != dto.getAcceptedSuggestionIds().size()) {
            throw validation("已采纳建议列表包含空值或重复项。");
        }
        List<AgentReviewPlanSuggestion> selected = suggestionMapper.selectList(
                new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                        .eq(AgentReviewPlanSuggestion::getUserId, userId)
                        .eq(AgentReviewPlanSuggestion::getReviewId, reviewId)
                        .eq(AgentReviewPlanSuggestion::getReviewVersion, review.getReviewVersion())
                        .eq(AgentReviewPlanSuggestion::getDecisionStatus, STATUS_ACCEPTED)
                        .in(AgentReviewPlanSuggestion::getId, selectedIds)
                        .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                        .orderByAsc(AgentReviewPlanSuggestion::getId));
        if (selected.size() != selectedIds.size()) {
            throw validation("只有当前复盘版本中已采纳的建议可以进入预览。");
        }

        String requestKeyHash = AgentAdaptivePlanHashUtils.sha256(dto.getIdempotencyKey().trim());
        String payloadHash = previewPayloadHash(review, selected, dto);
        AgentPlanChangeSet existing = changeSetMapper.selectByPreviewRequestKey(userId, requestKeyHash);
        if (existing != null) {
            if (!Objects.equals(existing.getPreviewPayloadHash(), payloadHash)) {
                throw conflict(AgentErrorCode.IDEMPOTENCY_KEY_REUSED, "同一幂等键不能用于不同的预览请求。");
            }
            return toPreviewVO(existing, loadChangeItems(userId, existing.getId()));
        }

        AgentRun baseRun = latestDailyRun(userId, review.getTargetJobId(), dto.getTargetDate());
        List<AgentTask> targetTasks = targetTasks(userId, review.getTargetJobId(), dto.getTargetDate());
        List<AgentTask> sourceTasks = referencedTasks(userId, review.getTargetJobId(), selected);
        List<AgentTask> baselineTasks = mergeTasks(targetTasks, sourceTasks);
        AgentWeekPlan weekPlan = currentWeekPlan(userId, review.getTargetJobId(), dto.getTargetDate());
        List<AgentWeekPlanItem> weekItems = weekPlan == null ? List.of() : weekPlanItems(userId, weekPlan.getId());

        AgentPlanPreviewPlanner.PlanningResult result = previewPlanner.plan(
                new AgentPlanPreviewPlanner.PlanningInput(
                        review,
                        selected,
                        baselineTasks,
                        baselineTasks,
                        targetTasks,
                        baseRun,
                        weekPlan,
                        weekItems,
                        dto.getTargetDate(),
                        dto.getMaxTotalMinutes(),
                        timeProvider.today()));

        LocalDateTime now = timeProvider.now();
        LocalDateTime expiresAt = now.plusMinutes(PREVIEW_EXPIRES_MINUTES);
        String selectionHash = AgentAdaptivePlanHashUtils.selectionHash(selected);
        Map<String, Object> previewContent = previewContent(review, dto, selectionHash, baseRun, result, expiresAt);
        String previewHash = AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(previewContent));

        AgentPlanChangeSet changeSet = new AgentPlanChangeSet();
        changeSet.setUserId(userId);
        changeSet.setReviewId(reviewId);
        changeSet.setReviewVersion(review.getReviewVersion());
        changeSet.setTargetJobId(review.getTargetJobId());
        changeSet.setTargetScopeKey(review.getTargetScopeKey());
        changeSet.setTargetDate(dto.getTargetDate());
        changeSet.setStatus(CHANGE_SET_PREVIEW_READY);
        changeSet.setSelectionHash(selectionHash);
        changeSet.setSourceSnapshotHash(review.getSourceSnapshotHash());
        changeSet.setBaseDailyRunId(baseRun == null ? null : baseRun.getId());
        changeSet.setBaseDailyStatus(baseRun == null ? null : baseRun.getStatus());
        changeSet.setBaseDailyTaskHash(result.baseDailyTaskHash());
        changeSet.setBaseWeekPlanId(result.baseWeekPlanId());
        changeSet.setBaseWeekSnapshotVersion(result.baseWeekSnapshotVersion());
        changeSet.setBaseWeekItemHash(result.baseWeekItemHash());
        changeSet.setPreviewVersion(1);
        changeSet.setPreviewHash(previewHash);
        changeSet.setPreviewSummaryJson(jsonCodec.write(previewSummary(result)));
        changeSet.setResultSource(result.resultSource());
        changeSet.setFallback(result.fallback());
        changeSet.setPreviewRequestKeyHash(requestKeyHash);
        changeSet.setPreviewPayloadHash(payloadHash);
        changeSet.setLockVersion(1);
        changeSet.setExpiresAt(expiresAt);
        List<AgentPlanChangeItem> items = new ArrayList<>();
        for (AgentPlanPreviewPlanner.ItemDraft draft : result.items()) {
            AgentPlanChangeItem item = new AgentPlanChangeItem();
            item.setUserId(userId);
            item.setSuggestionId(draft.suggestionId());
            item.setItemKey(draft.itemKey());
            item.setChangeType(draft.changeType());
            item.setTargetDate(dto.getTargetDate());
            item.setSourceTaskId(draft.sourceTaskId());
            item.setBaseDailyRunId(baseRun == null ? null : baseRun.getId());
            item.setBeforeJson(jsonCodec.write(draft.before()));
            item.setAfterJson(jsonCodec.write(draft.after()));
            item.setDailyImpactJson(jsonCodec.write(Map.of("text", draft.dailyImpact())));
            item.setWeekImpactJson(jsonCodec.write(Map.of("text", draft.weekImpact())));
            item.setValidationStatus(draft.validationStatus());
            item.setWarningCodesJson(jsonCodec.write(draft.warningCodes()));
            item.setConfidenceLevel(draft.confidenceLevel());
            item.setFallback(draft.fallback());
            item.setApplyStatus(draft.applyStatus());
            item.setApplyCount(0);
            items.add(item);
        }
        List<Long> baselineTaskIds = baselineTasks.stream()
                .map(AgentTask::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        AgentPlanPreviewPersistenceService.PersistedPreview persisted = previewPersistenceService.persist(
                new AgentPlanPreviewPersistenceService.PreviewDraft(
                        changeSet, items, selectedIds, baselineTaskIds));
        return toPreviewVO(persisted.changeSet(), persisted.items());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanChangePreviewVO previewExternal(Long userId, AgentExternalPlanChangePreviewDTO dto) {
        requireExternalPreviewRequest(dto);
        String requestKeyHash = AgentAdaptivePlanHashUtils.sha256(dto.getIdempotencyKey().trim());
        String payloadHash = sourceAdapter.contextHash(dto);
        AgentPlanChangeSet existing = changeSetMapper.selectByPreviewRequestKey(userId, requestKeyHash);
        if (existing != null) {
            if (!Objects.equals(existing.getPreviewPayloadHash(), payloadHash)) {
                throw conflict(AgentErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "同一幂等键不能用于不同的外部计划预览。");
            }
            return toPreviewVO(existing, loadChangeItems(userId, existing.getId()));
        }

        staleExternalSourcePreviews(userId, dto);
        LocalDate targetDate = externalTargetDate(dto);
        List<AgentReviewPlanSuggestion> selected = sourceAdapter.toSuggestions(userId, dto);
        for (AgentReviewPlanSuggestion suggestion : selected) {
            suggestion.setDecisionVersion(1);
            suggestionMapper.insert(suggestion);
        }

        AgentReview syntheticReview = new AgentReview();
        syntheticReview.setUserId(userId);
        syntheticReview.setTargetJobId(dto.getTargetJobId());
        syntheticReview.setTargetScopeKey(AgentAdaptivePlanHashUtils.targetScopeKey(dto.getTargetJobId()));
        syntheticReview.setSourceSnapshotHash(dto.getSourceContextHash());
        syntheticReview.setConfidenceLevel(selected.stream()
                .anyMatch(item -> "LOW".equalsIgnoreCase(item.getConfidenceLevel())) ? "LOW" : "MEDIUM");
        syntheticReview.setFallback(selected.stream().anyMatch(item -> Boolean.TRUE.equals(item.getFallback())));

        AgentRun baseRun = latestDailyRun(userId, dto.getTargetJobId(), targetDate);
        List<AgentTask> targetTasks = targetTasks(userId, dto.getTargetJobId(), targetDate);
        AgentWeekPlan weekPlan = currentWeekPlan(userId, dto.getTargetJobId(), targetDate);
        List<AgentWeekPlanItem> weekItems = weekPlan == null ? List.of() : weekPlanItems(userId, weekPlan.getId());
        AgentPlanPreviewPlanner.PlanningResult result = previewPlanner.plan(
                new AgentPlanPreviewPlanner.PlanningInput(
                        syntheticReview, selected, targetTasks, targetTasks, targetTasks,
                        baseRun, weekPlan, weekItems, targetDate,
                        dto.getMaxTotalMinutes(), timeProvider.today()));

        LocalDateTime expiresAt = timeProvider.now().plusMinutes(PREVIEW_EXPIRES_MINUTES);
        String selectionHash = AgentAdaptivePlanHashUtils.selectionHash(selected);
        AgentPlanChangeSet changeSet = externalChangeSet(
                userId, dto, targetDate, baseRun, result, requestKeyHash,
                payloadHash, selectionHash, expiresAt);
        Map<Long, AgentReviewPlanSuggestion> suggestionById = selected.stream()
                .collect(Collectors.toMap(AgentReviewPlanSuggestion::getId, Function.identity()));
        List<AgentPlanChangeItem> items = externalChangeItems(
                userId, targetDate, baseRun, result, suggestionById);
        AgentPlanPreviewPersistenceService.PersistedPreview persisted = previewPersistenceService.persist(
                new AgentPlanPreviewPersistenceService.PreviewDraft(
                        changeSet,
                        items,
                        selected.stream().map(AgentReviewPlanSuggestion::getId).toList(),
                        targetTasks.stream().map(AgentTask::getId).toList()));
        return toPreviewVO(persisted.changeSet(), persisted.items());
    }

    @Override
    public AgentPlanChangePreviewVO changeSet(Long userId, Long changeSetId) {
        AgentPlanChangeSet changeSet = requireChangeSet(userId, changeSetId);
        return toPreviewVO(changeSet, loadChangeItems(userId, changeSetId));
    }

    @Override
    public List<AgentPlanChangePreviewVO> changeSets(Long userId, LocalDate targetDate, String statuses) {
        Set<String> requestedStatuses = parseStatuses(statuses);
        return changeSetMapper.selectList(new LambdaQueryWrapper<AgentPlanChangeSet>()
                        .eq(AgentPlanChangeSet::getUserId, userId)
                        .eq(targetDate != null, AgentPlanChangeSet::getTargetDate, targetDate)
                        .in(!requestedStatuses.isEmpty(), AgentPlanChangeSet::getStatus, requestedStatuses)
                        .eq(AgentPlanChangeSet::getDeleted, 0)
                        .orderByDesc(AgentPlanChangeSet::getCreatedAt)
                        .last("LIMIT 50"))
                .stream()
                .map(changeSet -> toPreviewVO(changeSet, loadChangeItems(userId, changeSet.getId())))
                .toList();
    }

    @Override
    public AgentPlanChangeConfirmVO confirm(Long userId, Long changeSetId, AgentPlanChangeConfirmDTO dto) {
        return applyService.confirm(userId, changeSetId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void materializeSuggestions(AgentReview review, List<AgentTask> sourceTasks, List<String> adjustments) {
        if (review == null || review.getId() == null || !REVIEW_TYPE_DAILY.equals(review.getReviewType())) {
            return;
        }
        int reviewVersion = review.getReviewVersion() == null ? 1 : review.getReviewVersion();
        suggestionMapper.update(null, new LambdaUpdateWrapper<AgentReviewPlanSuggestion>()
                .eq(AgentReviewPlanSuggestion::getUserId, review.getUserId())
                .eq(AgentReviewPlanSuggestion::getReviewId, review.getId())
                .lt(AgentReviewPlanSuggestion::getReviewVersion, reviewVersion)
                .ne(AgentReviewPlanSuggestion::getDecisionStatus, STATUS_SUPERSEDED)
                .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                .set(AgentReviewPlanSuggestion::getDecisionStatus, STATUS_SUPERSEDED)
                .set(AgentReviewPlanSuggestion::getUpdatedAt, timeProvider.now()));
        Long existingCount = suggestionMapper.selectCount(new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                .eq(AgentReviewPlanSuggestion::getReviewId, review.getId())
                .eq(AgentReviewPlanSuggestion::getReviewVersion, reviewVersion)
                .eq(AgentReviewPlanSuggestion::getDeleted, 0));
        if (existingCount != null && existingCount > 0) {
            return;
        }

        List<SuggestionCandidate> candidates = buildSuggestionCandidates(review, sourceTasks, adjustments);
        int index = 0;
        for (SuggestionCandidate candidate : candidates) {
            AgentReviewPlanSuggestion suggestion = new AgentReviewPlanSuggestion();
            suggestion.setUserId(review.getUserId());
            suggestion.setReviewId(review.getId());
            suggestion.setReviewVersion(reviewVersion);
            suggestion.setSuggestionFingerprint(candidate.fingerprint());
            suggestion.setSuggestionKey("v" + reviewVersion + "-"
                    + AgentAdaptivePlanHashUtils.sha256(index++ + "|" + candidate.fingerprint()).substring(0, 24));
            suggestion.setTitle(candidate.title());
            suggestion.setContent(candidate.content());
            suggestion.setReason(candidate.reason());
            suggestion.setIntentType(candidate.intentType());
            suggestion.setTargetScope(candidate.targetScope());
            suggestion.setIntentJson(jsonCodec.write(candidate.intent()));
            suggestion.setEvidenceJson(jsonCodec.write(candidate.evidence()));
            suggestion.setConfidenceLevel(firstText(review.getConfidenceLevel(), "LOW"));
            suggestion.setFallback(Boolean.TRUE.equals(review.getFallback()));
            suggestion.setDecisionStatus(STATUS_PENDING);
            suggestion.setDecisionVersion(1);
            try {
                suggestionMapper.insert(suggestion);
            } catch (DuplicateKeyException ignored) {
                // 同一复盘版本的稳定建议键已存在时，按幂等成功处理。
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewGenerationClaim claimDailyReview(AgentReview review) {
        requireDailyReviewIdentity(review);
        reviewMapper.insertDailyGenerationClaim(
                review.getUserId(),
                review.getTargetJobId(),
                review.getReviewDate(),
                review.getIdempotencyKey(),
                review.getTargetScopeKey(),
                review.getSourceSnapshotHash());
        AgentReview current = reviewMapper.selectDailyForUpdate(
                review.getUserId(), review.getReviewDate(), review.getTargetScopeKey());
        if (current == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    AgentErrorCode.PLAN_CHANGE_TEMPORARILY_UNAVAILABLE
                            + ": daily review generation state is unavailable");
        }
        int previousVersion = valueOrDefault(current.getReviewVersion(), 0);
        boolean newlyClaimed = previousVersion == 0;
        boolean sourceChanged = !Objects.equals(
                current.getSourceSnapshotHash(), review.getSourceSnapshotHash());
        return new ReviewGenerationClaim(
                current,
                review.getSourceSnapshotHash(),
                previousVersion,
                current.getSourceSnapshotHash(),
                newlyClaimed || sourceChanged,
                newlyClaimed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentReview completeClaimedDailyReview(ReviewGenerationClaim claim,
                                                  AgentReview review,
                                                  List<AgentTask> sourceTasks,
                                                  List<String> adjustments) {
        if (claim == null || claim.current() == null || review == null || !claim.shouldGenerate()) {
            throw validation("Daily review generation claim is incomplete.");
        }
        AgentReview current = claim.current();
        if (!Objects.equals(current.getId(), review.getId())
                || !Objects.equals(current.getUserId(), review.getUserId())
                || !Objects.equals(current.getReviewDate(), review.getReviewDate())
                || !Objects.equals(current.getTargetScopeKey(), review.getTargetScopeKey())
                || !Objects.equals(claim.requestedSourceSnapshotHash(), review.getSourceSnapshotHash())
                || !Objects.equals(claim.previousReviewVersion(),
                valueOrDefault(current.getReviewVersion(), 0))
                || !Objects.equals(claim.previousSourceSnapshotHash(), current.getSourceSnapshotHash())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE,
                    "Daily review generation claim is stale.");
        }
        copyReviewContent(review, current);
        current.setReviewVersion(claim.newlyClaimed()
                ? 1
                : valueOrDefault(claim.previousReviewVersion(), 1) + 1);
        reviewMapper.updateById(current);
        materializeSuggestions(current, sourceTasks, adjustments);
        return current;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentReview persistReviewWithSuggestions(AgentReview review,
                                                    List<AgentTask> sourceTasks,
                                                    List<String> adjustments) {
        if (review == null) {
            throw validation("复盘记录不能为空。");
        }
        AgentReview current = reviewMapper.selectDailyForUpdate(
                review.getUserId(), review.getReviewDate(), review.getTargetScopeKey());
        if (current != null && Objects.equals(current.getSourceSnapshotHash(), review.getSourceSnapshotHash())) {
            return current;
        }
        AgentReview persisted;
        if (current == null) {
            review.setId(null);
            review.setReviewVersion(1);
            try {
                reviewMapper.insert(review);
                persisted = review;
            } catch (DuplicateKeyException ex) {
                current = reviewMapper.selectDailyForUpdate(
                        review.getUserId(), review.getReviewDate(), review.getTargetScopeKey());
                if (current == null) {
                    throw ex;
                }
                if (Objects.equals(current.getSourceSnapshotHash(), review.getSourceSnapshotHash())) {
                    return current;
                }
                copyReviewContent(review, current);
                current.setReviewVersion(valueOrDefault(current.getReviewVersion(), 1) + 1);
                reviewMapper.updateById(current);
                persisted = current;
            }
        } else {
            copyReviewContent(review, current);
            current.setReviewVersion(valueOrDefault(current.getReviewVersion(), 1) + 1);
            reviewMapper.updateById(current);
            persisted = current;
        }
        materializeSuggestions(persisted, sourceTasks, adjustments);
        return persisted;
    }

    @Override
    public List<AgentReviewPlanSuggestionVO> suggestionVOs(Long userId, Long reviewId) {
        return toSuggestionVOs(userId, loadSuggestions(userId, reviewId));
    }

    @Override
    public AgentReviewPlanDecisionSummaryVO decisionSummary(Long userId, Long reviewId) {
        return summarize(loadSuggestions(userId, reviewId));
    }

    private boolean applyDecision(AgentReviewPlanSuggestion suggestion,
                                  AgentReviewPlanDecisionItemDTO decision,
                                  LocalDateTime now) {
        if (suggestion == null || STATUS_SUPERSEDED.equals(suggestion.getDecisionStatus())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_ALREADY_DECIDED, "该建议已失效，不能继续决策。");
        }
        String target = normalizeDecision(decision.getDecision());
        String current = suggestion.getDecisionStatus();
        if (Objects.equals(current, target)) {
            return false;
        }
        if (!Objects.equals(suggestion.getDecisionVersion(), decision.getExpectedDecisionVersion())) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_ALREADY_DECIDED, "建议决策版本已变化，请刷新后重试。");
        }
        if (!allowedDecisionTransition(current, target)) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_ALREADY_DECIDED,
                    "建议状态不允许直接变更，请先执行“重新考虑”。");
        }
        int nextVersion = valueOrDefault(suggestion.getDecisionVersion(), 1) + 1;
        int updated = suggestionMapper.update(null, new LambdaUpdateWrapper<AgentReviewPlanSuggestion>()
                .eq(AgentReviewPlanSuggestion::getId, suggestion.getId())
                .eq(AgentReviewPlanSuggestion::getUserId, suggestion.getUserId())
                .eq(AgentReviewPlanSuggestion::getDecisionVersion, suggestion.getDecisionVersion())
                .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                .set(AgentReviewPlanSuggestion::getDecisionStatus, target)
                .set(AgentReviewPlanSuggestion::getDecisionVersion, nextVersion)
                .set(AgentReviewPlanSuggestion::getDecidedAt, STATUS_PENDING.equals(target) ? null : now)
                .set(AgentReviewPlanSuggestion::getIgnoredReason,
                        STATUS_IGNORED.equals(target) ? safeText(decision.getReason(), 500) : null)
                .set(AgentReviewPlanSuggestion::getUpdatedAt, now));
        if (updated != 1) {
            throw conflict(AgentErrorCode.PLAN_CHANGE_ALREADY_DECIDED, "建议已被其他请求更新，请刷新后重试。");
        }
        suggestion.setDecisionStatus(target);
        suggestion.setDecisionVersion(nextVersion);
        suggestion.setDecidedAt(STATUS_PENDING.equals(target) ? null : now);
        suggestion.setIgnoredReason(STATUS_IGNORED.equals(target) ? safeText(decision.getReason(), 500) : null);
        return true;
    }

    private void copyReviewContent(AgentReview source, AgentReview target) {
        target.setTargetJobId(source.getTargetJobId());
        target.setReviewDate(source.getReviewDate());
        target.setReviewType(source.getReviewType());
        target.setSourceTaskId(source.getSourceTaskId());
        target.setIdempotencyKey(source.getIdempotencyKey());
        target.setTargetScopeKey(source.getTargetScopeKey());
        target.setSourceSnapshotHash(source.getSourceSnapshotHash());
        target.setSummary(source.getSummary());
        target.setDoneCount(source.getDoneCount());
        target.setSkippedCount(source.getSkippedCount());
        target.setTodoCount(source.getTodoCount());
        target.setCompletionRate(source.getCompletionRate());
        target.setReadinessScore(source.getReadinessScore());
        target.setNextActionsJson(source.getNextActionsJson());
        target.setReviewJson(source.getReviewJson());
        target.setAgentRunId(source.getAgentRunId());
        target.setAiCallLogId(source.getAiCallLogId());
        target.setConfidenceLevel(source.getConfidenceLevel());
        target.setFallback(source.getFallback());
    }

    private List<SuggestionCandidate> buildSuggestionCandidates(AgentReview review,
                                                                 List<AgentTask> sourceTasks,
                                                                 List<String> adjustments) {
        List<AgentTask> tasks = sourceTasks == null ? List.of() : sourceTasks;
        boolean weak = Boolean.TRUE.equals(review.getFallback())
                || "LOW".equalsIgnoreCase(review.getConfidenceLevel())
                || "INSUFFICIENT".equalsIgnoreCase(review.getConfidenceLevel());
        int limit = weak ? 1 : MAX_SUGGESTIONS;
        List<AgentTask> openTasks = tasks.stream()
                .filter(task -> !Integer.valueOf(1).equals(task.getDeleted()))
                .filter(task -> List.of(AgentTaskStatusEnum.TODO.name(), AgentTaskStatusEnum.DOING.name(),
                        AgentTaskStatusEnum.DEFERRED.name()).contains(task.getStatus()))
                .sorted(Comparator.comparingInt(this::importantPriority)
                        .thenComparing(AgentTask::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AgentTask::getId, Comparator.nullsLast(Long::compareTo)))
                .limit(limit)
                .toList();
        List<SuggestionCandidate> result = new ArrayList<>();
        for (AgentTask task : openTasks) {
            AgentPlanSuggestionIntentDTO intent = new AgentPlanSuggestionIntentDTO();
            intent.setSourceTaskId(task.getId());
            intent.setRelatedTaskRefs(List.of("task:" + task.getId()));
            intent.setTaskType(task.getTaskType());
            intent.setTitle(task.getTitle());
            intent.setDescription(task.getDescription());
            intent.setReason(task.getReason());
            intent.setPriority(task.getPriority());
            intent.setEstimatedMinutes(task.getEstimatedMinutes());
            intent.setRelatedSkillCode(task.getRelatedSkillCode());
            intent.setRelatedSkillName(task.getRelatedSkillName());
            intent.setRelatedBizType(task.getRelatedBizType());
            intent.setRelatedBizId(task.getRelatedBizId());
            intent.setActionUrl(safeActionUrl(task.getActionUrl()));
            String fingerprint = suggestionFingerprint("CARRY_OVER", "NEXT_DAY", intent);
            Map<String, Object> evidence = safeEvidence(task);
            result.add(new SuggestionCandidate(
                    safeText("保留未完成任务：" + firstText(task.getTitle(), "未命名任务"), 255),
                    safeText("将该开放任务作为下一日承接任务，原任务历史保持不变。", 1000),
                    safeText("该任务在本次复盘中仍未闭环，需要由用户确认是否继续保留。", 1000),
                    "CARRY_OVER",
                    "NEXT_DAY",
                    intent,
                    evidence,
                    fingerprint));
        }
        if (!result.isEmpty()) {
            return result;
        }

        AgentTask practiceSource = tasks.stream()
                .filter(task -> StringUtils.hasText(task.getRelatedSkillCode())
                        || StringUtils.hasText(task.getRelatedSkillName())
                        || (StringUtils.hasText(task.getRelatedBizType()) && task.getRelatedBizId() != null))
                .findFirst()
                .orElse(null);
        if (practiceSource != null) {
            AgentPlanSuggestionIntentDTO intent = new AgentPlanSuggestionIntentDTO();
            intent.setSourceTaskId(practiceSource.getId());
            intent.setRelatedTaskRefs(List.of("task:" + practiceSource.getId()));
            intent.setTaskType(firstText(practiceSource.getTaskType(), "STUDY_TASK"));
            intent.setTitle("增加一项针对性练习");
            intent.setDescription("围绕本次复盘中的已有技能或业务依据完成一项可验证练习。");
            intent.setReason("当前没有需要承接的开放任务，可通过一项小规模练习保持节奏。");
            intent.setPriority(AgentTaskPriorityEnum.MEDIUM.name());
            intent.setEstimatedMinutes(weak ? 30 : Math.min(60, valueOrDefault(practiceSource.getEstimatedMinutes(), 30)));
            intent.setRelatedSkillCode(practiceSource.getRelatedSkillCode());
            intent.setRelatedSkillName(practiceSource.getRelatedSkillName());
            intent.setRelatedBizType(practiceSource.getRelatedBizType());
            intent.setRelatedBizId(practiceSource.getRelatedBizId());
            intent.setActionUrl(safeActionUrl(practiceSource.getActionUrl()));
            return List.of(new SuggestionCandidate(
                    "增加一项针对性练习",
                    "基于本次复盘已有依据，在下一日增加一项小规模练习。",
                    "建议只引用当前任务中已有的技能或业务来源，不创建新的业务事实。",
                    "ADD_PRACTICE",
                    "NEXT_DAY",
                    intent,
                    safeEvidence(practiceSource),
                    suggestionFingerprint("ADD_PRACTICE", "NEXT_DAY", intent)));
        }

        String adjustment = adjustments == null ? null : adjustments.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        AgentPlanSuggestionIntentDTO intent = new AgentPlanSuggestionIntentDTO();
        intent.setReason(adjustment);
        return List.of(new SuggestionCandidate(
                "人工复核本次调整建议",
                safeText(firstText(adjustment, "当前复盘没有可安全映射到计划的结构化建议。"), 1000),
                "缺少可验证的任务或业务来源，本建议只展示，不允许确认写入计划。",
                "MANUAL_ONLY",
                "NEXT_DAY",
                intent,
                Map.of("reviewId", review.getId(), "reviewVersion", valueOrDefault(review.getReviewVersion(), 1)),
                suggestionFingerprint("MANUAL_ONLY", "NEXT_DAY", intent)));
    }

    private Map<String, Object> safeEvidence(AgentTask task) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("taskId", task.getId());
        evidence.put("status", task.getStatus());
        evidence.put("dueDate", task.getDueDate());
        evidence.put("taskType", task.getTaskType());
        evidence.put("priority", task.getPriority());
        evidence.put("estimatedMinutes", task.getEstimatedMinutes());
        evidence.put("titleHash", AgentAdaptivePlanHashUtils.sha256(
                AgentAdaptivePlanHashUtils.normalizeText(task.getTitle())));
        evidence.put("reasonHash", AgentAdaptivePlanHashUtils.sha256(
                AgentAdaptivePlanHashUtils.normalizeText(task.getReason())));
        return evidence;
    }

    private String suggestionFingerprint(String intentType,
                                         String targetScope,
                                         AgentPlanSuggestionIntentDTO intent) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("intentType", intentType);
        value.put("targetScope", targetScope);
        value.put("sourceTaskId", intent.getSourceTaskId());
        value.put("taskType", intent.getTaskType());
        value.put("priority", intent.getPriority());
        value.put("targetPriority", intent.getTargetPriority());
        value.put("estimatedMinutes", intent.getEstimatedMinutes());
        value.put("relatedSkillCode", intent.getRelatedSkillCode());
        value.put("relatedBizType", intent.getRelatedBizType());
        value.put("relatedBizId", intent.getRelatedBizId());
        value.put("titleHash", AgentAdaptivePlanHashUtils.sha256(
                AgentAdaptivePlanHashUtils.normalizeText(intent.getTitle())));
        return AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(value));
    }

    private AgentReviewPlanSuggestionListVO suggestionListVO(AgentReview review,
                                                              List<AgentReviewPlanSuggestion> suggestions) {
        AgentReviewPlanSuggestionListVO vo = new AgentReviewPlanSuggestionListVO();
        vo.setReviewId(review.getId());
        vo.setReviewVersion(review.getReviewVersion());
        vo.setReviewDate(review.getReviewDate());
        vo.setSourceSnapshotHash(review.getSourceSnapshotHash());
        vo.setSuggestions(toSuggestionVOs(review.getUserId(), suggestions));
        vo.setDecisionSummary(summarize(suggestions));
        return vo;
    }

    private List<AgentReviewPlanSuggestionVO> toSuggestionVOs(Long userId,
                                                               List<AgentReviewPlanSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }
        Set<String> fingerprints = suggestions.stream()
                .map(AgentReviewPlanSuggestion::getSuggestionFingerprint)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> previouslyIgnored = fingerprints.isEmpty() ? Set.of() : suggestionMapper.selectList(
                        new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                                .eq(AgentReviewPlanSuggestion::getUserId, userId)
                                .in(AgentReviewPlanSuggestion::getSuggestionFingerprint, fingerprints)
                                .and(wrapper -> wrapper.eq(AgentReviewPlanSuggestion::getDecisionStatus, STATUS_IGNORED)
                                        .or().isNotNull(AgentReviewPlanSuggestion::getIgnoredReason))
                                .eq(AgentReviewPlanSuggestion::getDeleted, 0))
                .stream()
                .map(AgentReviewPlanSuggestion::getSuggestionFingerprint)
                .collect(Collectors.toSet());
        return suggestions.stream().map(item -> {
            AgentReviewPlanSuggestionVO vo = new AgentReviewPlanSuggestionVO();
            vo.setId(item.getId());
            vo.setReviewId(item.getReviewId());
            vo.setReviewVersion(item.getReviewVersion());
            vo.setTitle(item.getTitle());
            vo.setContent(item.getContent());
            vo.setReason(item.getReason());
            vo.setIntentType(item.getIntentType());
            vo.setTargetScope(item.getTargetScope());
            vo.setConfidenceLevel(item.getConfidenceLevel());
            vo.setFallback(Boolean.TRUE.equals(item.getFallback()));
            vo.setDecisionStatus(item.getDecisionStatus());
            vo.setDecisionVersion(item.getDecisionVersion());
            vo.setDecidedAt(item.getDecidedAt());
            vo.setIgnoredReason(item.getIgnoredReason());
            vo.setPreviouslyIgnored(previouslyIgnored.contains(item.getSuggestionFingerprint()));
            vo.setActionable(!"MANUAL_ONLY".equals(item.getIntentType())
                    && !STATUS_SUPERSEDED.equals(item.getDecisionStatus()));
            return vo;
        }).toList();
    }

    private AgentReviewPlanDecisionSummaryVO summarize(List<AgentReviewPlanSuggestion> suggestions) {
        AgentReviewPlanDecisionSummaryVO summary = new AgentReviewPlanDecisionSummaryVO();
        if (suggestions == null) {
            return summary;
        }
        for (AgentReviewPlanSuggestion item : suggestions) {
            switch (firstText(item.getDecisionStatus(), STATUS_PENDING)) {
                case STATUS_ACCEPTED -> summary.setAcceptedCount(summary.getAcceptedCount() + 1);
                case STATUS_IGNORED -> summary.setIgnoredCount(summary.getIgnoredCount() + 1);
                case STATUS_SUPERSEDED -> summary.setSupersededCount(summary.getSupersededCount() + 1);
                default -> summary.setPendingCount(summary.getPendingCount() + 1);
            }
        }
        return summary;
    }

    private AgentPlanChangePreviewVO toPreviewVO(AgentPlanChangeSet changeSet,
                                                  List<AgentPlanChangeItem> items) {
        AgentPlanChangePreviewVO vo = new AgentPlanChangePreviewVO();
        vo.setChangeSetId(changeSet.getId());
        vo.setReviewId(changeSet.getReviewId());
        vo.setReviewVersion(changeSet.getReviewVersion());
        vo.setTargetJobId(changeSet.getTargetJobId());
        vo.setTargetDate(changeSet.getTargetDate());
        vo.setStatus(changeSet.getStatus());
        vo.setPreviewVersion(changeSet.getPreviewVersion());
        vo.setPreviewHash(changeSet.getPreviewHash());
        vo.setExpiresAt(changeSet.getExpiresAt());
        vo.setResultSource(changeSet.getResultSource());
        vo.setFallback(Boolean.TRUE.equals(changeSet.getFallback()));
        vo.setConfirmedAt(changeSet.getConfirmedAt());
        vo.setAppliedAt(changeSet.getAppliedAt());
        vo.setFailureCode(changeSet.getFailureCode());
        vo.setFailureMessage(changeSet.getFailureMessage());
        vo.setSourceType(changeSet.getSourceType());
        vo.setSourceId(changeSet.getSourceId());
        vo.setSourceVersion(changeSet.getSourceVersion());
        vo.setSourceContextHash(changeSet.getSourceContextHash());
        Map<String, Object> summaryPayload = jsonCodec.readObjectMap(changeSet.getPreviewSummaryJson());
        vo.setSummary(readSummary(summaryPayload.get("summary")));
        vo.setWarnings(toStringList(summaryPayload.get("warnings")));
        vo.setBlockers(toStringList(summaryPayload.get("blockers")));
        vo.setItems(items.stream().map(item -> toItemVO(changeSet, item)).toList());
        vo.setConfirmable(CHANGE_SET_PREVIEW_READY.equals(changeSet.getStatus())
                && (changeSet.getExpiresAt() == null || changeSet.getExpiresAt().isAfter(timeProvider.now()))
                && vo.getBlockers().isEmpty()
                && vo.getItems().stream().anyMatch(item -> !APPLY_SKIPPED_DUPLICATE.equals(item.getApplyStatus())));
        return vo;
    }

    private AgentPlanChangeItemVO toItemVO(AgentPlanChangeSet changeSet, AgentPlanChangeItem item) {
        AgentPlanChangeItemVO vo = new AgentPlanChangeItemVO();
        vo.setId(item.getId());
        vo.setItemKey(item.getItemKey());
        vo.setChangeType(item.getChangeType());
        vo.setTargetDate(item.getTargetDate());
        vo.setBefore(jsonCodec.readTaskSnapshot(item.getBeforeJson()));
        vo.setAfter(jsonCodec.readTaskSnapshot(item.getAfterJson()));
        vo.setTitle(firstText(vo.getAfter() == null ? null : vo.getAfter().getTitle(),
                vo.getBefore() == null ? null : vo.getBefore().getTitle(), "计划变更"));
        vo.setDailyImpact(impactText(item.getDailyImpactJson()));
        vo.setWeekImpact(impactText(item.getWeekImpactJson()));
        vo.setSourceReviewId(changeSet.getReviewId());
        vo.setSourceSuggestionId(item.getSuggestionId());
        vo.setValidationStatus(item.getValidationStatus());
        vo.setConfidenceLevel(item.getConfidenceLevel());
        vo.setFallback(Boolean.TRUE.equals(item.getFallback()));
        vo.setApplyStatus(item.getApplyStatus());
        vo.setWarnings(jsonCodec.readStringList(item.getWarningCodesJson()));
        vo.setSourceType(changeSet.getSourceType());
        vo.setSourceItemKey(item.getSourceItemKey());
        return vo;
    }

    private String impactText(String json) {
        Object text = jsonCodec.readObjectMap(json).get("text");
        return text == null ? null : text.toString();
    }

    private Map<String, Object> previewSummary(AgentPlanPreviewPlanner.PlanningResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("summary", result.summary());
        summary.put("warnings", result.warnings());
        summary.put("blockers", result.blockers());
        summary.put("confirmable", result.blockers().isEmpty() && !result.items().isEmpty());
        return summary;
    }

    private Map<String, Object> previewContent(AgentReview review,
                                               AgentPlanChangePreviewDTO dto,
                                               String selectionHash,
                                               AgentRun baseRun,
                                               AgentPlanPreviewPlanner.PlanningResult result,
                                               LocalDateTime expiresAt) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reviewId", review.getId());
        content.put("reviewVersion", review.getReviewVersion());
        content.put("sourceSnapshotHash", review.getSourceSnapshotHash());
        content.put("targetScopeKey", review.getTargetScopeKey());
        content.put("targetDate", dto.getTargetDate());
        content.put("selectionHash", selectionHash);
        content.put("baseDailyRunId", baseRun == null ? null : baseRun.getId());
        content.put("baseDailyStatus", baseRun == null ? null : baseRun.getStatus());
        content.put("baseDailyTaskHash", result.baseDailyTaskHash());
        content.put("baseWeekPlanId", result.baseWeekPlanId());
        content.put("baseWeekSnapshotVersion", result.baseWeekSnapshotVersion());
        content.put("baseWeekItemHash", result.baseWeekItemHash());
        content.put("summary", result.summary());
        content.put("items", result.items());
        content.put("warnings", result.warnings());
        content.put("blockers", result.blockers());
        content.put("expiresAt", expiresAt);
        return content;
    }

    private String previewPayloadHash(AgentReview review,
                                      List<AgentReviewPlanSuggestion> suggestions,
                                      AgentPlanChangePreviewDTO dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", review.getId());
        payload.put("expectedReviewVersion", dto.getExpectedReviewVersion());
        payload.put("acceptedSuggestionIds", suggestions.stream().map(AgentReviewPlanSuggestion::getId).toList());
        payload.put("selectionHash", AgentAdaptivePlanHashUtils.selectionHash(suggestions));
        payload.put("targetDate", dto.getTargetDate());
        payload.put("maxTotalMinutes", dto.getMaxTotalMinutes());
        return AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(payload));
    }

    private List<AgentTask> referencedTasks(Long userId,
                                            Long targetJobId,
                                            List<AgentReviewPlanSuggestion> suggestions) {
        Set<Long> taskIds = suggestions.stream()
                .map(item -> jsonCodec.readIntent(item.getIntentJson()))
                .map(this::referencedTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(targetJobId != null, AgentTask::getTargetJobId, targetJobId)
                .in(AgentTask::getId, taskIds)
                .eq(AgentTask::getDeleted, 0)
                .orderByAsc(AgentTask::getId));
    }

    private Long referencedTaskId(AgentPlanSuggestionIntentDTO intent) {
        if (intent.getSourceTaskId() != null) {
            return intent.getSourceTaskId();
        }
        if (intent.getRelatedTaskRefs() == null) {
            return null;
        }
        return intent.getRelatedTaskRefs().stream().map(this::parseTaskRef)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Long parseTaskRef(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith("task:")) {
            return null;
        }
        try {
            return Long.parseLong(value.substring(5));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<AgentTask> targetTasks(Long userId, Long targetJobId, LocalDate targetDate) {
        LambdaQueryWrapper<AgentTask> query = new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .eq(AgentTask::getDueDate, targetDate)
                .eq(AgentTask::getDeleted, 0)
                .orderByAsc(AgentTask::getSortOrder)
                .orderByAsc(AgentTask::getId);
        if (targetJobId != null) {
            query.eq(AgentTask::getTargetJobId, targetJobId);
        }
        return taskMapper.selectList(query);
    }

    private AgentRun latestDailyRun(Long userId, Long targetJobId, LocalDate targetDate) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(AgentRun::getAgentType, "JOB_COACH")
                .eq(AgentRun::getPlanDate, targetDate)
                .in(AgentRun::getStatus, AgentRunStatusEnum.RUNNING.name(), AgentRunStatusEnum.SUCCESS.name(),
                        AgentRunStatusEnum.FAILED.name())
                .eq(AgentRun::getDeleted, 0)
                .orderByDesc(AgentRun::getCreatedAt)
                .last("LIMIT 1");
        if (targetJobId == null) {
            query.isNull(AgentRun::getTargetJobId);
        } else {
            query.eq(AgentRun::getTargetJobId, targetJobId);
        }
        List<AgentRun> runs = runMapper.selectList(query);
        return runs == null || runs.isEmpty() ? null : runs.get(0);
    }

    private AgentWeekPlan currentWeekPlan(Long userId, Long targetJobId, LocalDate targetDate) {
        LocalDate weekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return weekPlanMapper.selectOne(new LambdaQueryWrapper<AgentWeekPlan>()
                .eq(AgentWeekPlan::getUserId, userId)
                .eq(AgentWeekPlan::getTargetScopeKey, AgentAdaptivePlanHashUtils.targetScopeKey(targetJobId))
                .eq(AgentWeekPlan::getWeekStartDate, weekStart)
                .eq(AgentWeekPlan::getDeleted, 0)
                .orderByDesc(AgentWeekPlan::getUpdatedAt)
                .last("LIMIT 1"));
    }

    private List<AgentWeekPlanItem> weekPlanItems(Long userId, Long weekPlanId) {
        return weekPlanItemMapper.selectList(new LambdaQueryWrapper<AgentWeekPlanItem>()
                .eq(AgentWeekPlanItem::getUserId, userId)
                .eq(AgentWeekPlanItem::getWeekPlanId, weekPlanId)
                .eq(AgentWeekPlanItem::getDeleted, 0)
                .orderByAsc(AgentWeekPlanItem::getId));
    }

    private List<AgentTask> mergeTasks(List<AgentTask> left, List<AgentTask> right) {
        Map<Long, AgentTask> merged = new LinkedHashMap<>();
        for (AgentTask task : concat(left, right)) {
            if (task != null && task.getId() != null) {
                merged.put(task.getId(), task);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<AgentTask> concat(List<AgentTask> left, List<AgentTask> right) {
        List<AgentTask> result = new ArrayList<>();
        if (left != null) {
            result.addAll(left);
        }
        if (right != null) {
            result.addAll(right);
        }
        return result;
    }

    private AgentReview requireDailyReview(Long userId, Long reviewId) {
        AgentReview review = reviewMapper.selectById(reviewId);
        requireDailyReview(review, userId);
        return review;
    }

    private void requireDailyReview(AgentReview review, Long userId) {
        if (review == null || !Objects.equals(review.getUserId(), userId)
                || Integer.valueOf(1).equals(review.getDeleted())) {
            throw forbidden("复盘不存在或不属于当前用户。");
        }
        if (!REVIEW_TYPE_DAILY.equals(review.getReviewType())) {
            throw validation("只有每日复盘可以生成自适应计划建议。");
        }
    }

    private void requireDailyReviewIdentity(AgentReview review) {
        if (review == null
                || review.getUserId() == null
                || review.getReviewDate() == null
                || !REVIEW_TYPE_DAILY.equals(review.getReviewType())
                || !StringUtils.hasText(review.getIdempotencyKey())
                || !StringUtils.hasText(review.getTargetScopeKey())
                || !StringUtils.hasText(review.getSourceSnapshotHash())) {
            throw validation("Daily review generation identity is incomplete.");
        }
    }

    private AgentPlanChangeSet requireChangeSet(Long userId, Long changeSetId) {
        AgentPlanChangeSet changeSet = changeSetMapper.selectById(changeSetId);
        if (changeSet == null || !Objects.equals(changeSet.getUserId(), userId)
                || Integer.valueOf(1).equals(changeSet.getDeleted())) {
            throw forbidden("计划变更集不存在或不属于当前用户。");
        }
        return changeSet;
    }

    private List<AgentReviewPlanSuggestion> loadSuggestions(Long userId, Long reviewId) {
        return suggestionMapper.selectList(new LambdaQueryWrapper<AgentReviewPlanSuggestion>()
                .eq(AgentReviewPlanSuggestion::getUserId, userId)
                .eq(AgentReviewPlanSuggestion::getReviewId, reviewId)
                .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                .orderByAsc(AgentReviewPlanSuggestion::getReviewVersion)
                .orderByAsc(AgentReviewPlanSuggestion::getId));
    }

    private List<AgentPlanChangeItem> loadChangeItems(Long userId, Long changeSetId) {
        return changeItemMapper.selectList(new LambdaQueryWrapper<AgentPlanChangeItem>()
                .eq(AgentPlanChangeItem::getUserId, userId)
                .eq(AgentPlanChangeItem::getChangeSetId, changeSetId)
                .eq(AgentPlanChangeItem::getDeleted, 0)
                .orderByAsc(AgentPlanChangeItem::getId));
    }

    private Set<String> parseStatuses(String statuses) {
        if (!StringUtils.hasText(statuses)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : statuses.split(",")) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (!QUERYABLE_CHANGE_SET_STATUSES.contains(normalized)) {
                throw validation("包含不受支持的计划变更状态：" + normalized);
            }
            result.add(normalized);
        }
        return result;
    }

    private String normalizeDecision(String value) {
        String decision = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (!Set.of(STATUS_PENDING, STATUS_ACCEPTED, STATUS_IGNORED).contains(decision)) {
            throw validation("建议决策只支持采纳、忽略或重新考虑。");
        }
        return decision;
    }

    private boolean allowedDecisionTransition(String current, String target) {
        if (STATUS_PENDING.equals(current)) {
            return STATUS_ACCEPTED.equals(target) || STATUS_IGNORED.equals(target);
        }
        return (STATUS_ACCEPTED.equals(current) || STATUS_IGNORED.equals(current))
                && STATUS_PENDING.equals(target);
    }

    private String decisionPayloadHash(Long reviewId,
                                       Integer expectedReviewVersion,
                                       List<AgentReviewPlanDecisionItemDTO> decisions) {
        List<Map<String, Object>> canonicalDecisions = new ArrayList<>();
        for (AgentReviewPlanDecisionItemDTO item : decisions) {
            String decision = normalizeDecision(item.getDecision());
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("suggestionId", item.getSuggestionId());
            canonical.put("decision", decision);
            canonical.put("expectedDecisionVersion", item.getExpectedDecisionVersion());
            canonical.put("reason", STATUS_IGNORED.equals(decision) ? safeText(item.getReason(), 500) : null);
            canonicalDecisions.add(canonical);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", reviewId);
        payload.put("expectedReviewVersion", expectedReviewVersion);
        payload.put("decisions", canonicalDecisions);
        return AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(payload));
    }

    private void requireDecisionRequest(AgentReviewPlanDecisionDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getIdempotencyKey())
                || dto.getExpectedReviewVersion() == null
                || dto.getDecisions() == null || dto.getDecisions().isEmpty()) {
            throw validation("建议决策请求不完整。");
        }
    }

    private void requirePreviewRequest(AgentPlanChangePreviewDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getIdempotencyKey())
                || dto.getExpectedReviewVersion() == null
                || dto.getAcceptedSuggestionIds() == null || dto.getAcceptedSuggestionIds().isEmpty()
                || dto.getTargetDate() == null) {
            throw validation("计划差异预览请求不完整。");
        }
    }

    private void requireExternalPreviewRequest(AgentExternalPlanChangePreviewDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSourceType())
                || dto.getSourceId() == null || dto.getSourceId() <= 0
                || dto.getSourceVersion() == null || dto.getSourceVersion() <= 0
                || !StringUtils.hasText(dto.getSourceContextHash())
                || dto.getIntents() == null || dto.getIntents().isEmpty()
                || !StringUtils.hasText(dto.getIdempotencyKey())) {
            throw validation("外部计划预览请求不完整。");
        }
        try {
            com.codecoachai.ai.agent.domain.enums.AgentPlanSourceType.parse(dto.getSourceType());
        } catch (IllegalArgumentException ex) {
            throw validation("不支持的外部计划来源类型。");
        }
    }

    private void staleExternalSourcePreviews(Long userId, AgentExternalPlanChangePreviewDTO dto) {
        LocalDateTime now = timeProvider.now();
        suggestionMapper.update(null, new LambdaUpdateWrapper<AgentReviewPlanSuggestion>()
                .eq(AgentReviewPlanSuggestion::getUserId, userId)
                .eq(AgentReviewPlanSuggestion::getSourceType, dto.getSourceType().trim().toUpperCase(Locale.ROOT))
                .eq(AgentReviewPlanSuggestion::getSourceId, dto.getSourceId())
                .ne(AgentReviewPlanSuggestion::getSourceSnapshotHash, dto.getSourceContextHash())
                .in(AgentReviewPlanSuggestion::getDecisionStatus, STATUS_PENDING, STATUS_ACCEPTED)
                .eq(AgentReviewPlanSuggestion::getDeleted, 0)
                .set(AgentReviewPlanSuggestion::getDecisionStatus, STATUS_SUPERSEDED)
                .set(AgentReviewPlanSuggestion::getUpdatedAt, now));
        changeSetMapper.update(null, new LambdaUpdateWrapper<AgentPlanChangeSet>()
                .eq(AgentPlanChangeSet::getUserId, userId)
                .eq(AgentPlanChangeSet::getSourceType, dto.getSourceType().trim().toUpperCase(Locale.ROOT))
                .eq(AgentPlanChangeSet::getSourceId, dto.getSourceId())
                .ne(AgentPlanChangeSet::getSourceContextHash, dto.getSourceContextHash())
                .eq(AgentPlanChangeSet::getStatus, CHANGE_SET_PREVIEW_READY)
                .eq(AgentPlanChangeSet::getDeleted, 0)
                .set(AgentPlanChangeSet::getStatus, CHANGE_SET_STALE)
                .set(AgentPlanChangeSet::getFailureCode, AgentErrorCode.PLAN_CHANGE_PREVIEW_STALE)
                .set(AgentPlanChangeSet::getFailureMessage, "外部来源快照已更新，请重新预览。")
                .set(AgentPlanChangeSet::getUpdatedAt, now));
    }

    private LocalDate externalTargetDate(AgentExternalPlanChangePreviewDTO dto) {
        if (dto.getTargetDate() != null) {
            return dto.getTargetDate();
        }
        return dto.getIntents().stream()
                .map(item -> item.getPlanDate())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(timeProvider.today().plusDays(1));
    }

    private AgentPlanChangeSet externalChangeSet(
            Long userId,
            AgentExternalPlanChangePreviewDTO dto,
            LocalDate targetDate,
            AgentRun baseRun,
            AgentPlanPreviewPlanner.PlanningResult result,
            String requestKeyHash,
            String payloadHash,
            String selectionHash,
            LocalDateTime expiresAt) {
        AgentPlanChangeSet changeSet = new AgentPlanChangeSet();
        changeSet.setUserId(userId);
        changeSet.setTargetJobId(dto.getTargetJobId());
        changeSet.setTargetScopeKey(AgentAdaptivePlanHashUtils.targetScopeKey(dto.getTargetJobId()));
        changeSet.setTargetDate(targetDate);
        changeSet.setStatus(CHANGE_SET_PREVIEW_READY);
        changeSet.setSelectionHash(selectionHash);
        changeSet.setSourceSnapshotHash(dto.getSourceContextHash());
        changeSet.setSourceType(dto.getSourceType().trim().toUpperCase(Locale.ROOT));
        changeSet.setSourceId(dto.getSourceId());
        changeSet.setSourceVersion(dto.getSourceVersion());
        changeSet.setSourceContextHash(dto.getSourceContextHash());
        changeSet.setBaseDailyRunId(baseRun == null ? null : baseRun.getId());
        changeSet.setBaseDailyStatus(baseRun == null ? null : baseRun.getStatus());
        changeSet.setBaseDailyTaskHash(result.baseDailyTaskHash());
        changeSet.setBaseWeekPlanId(result.baseWeekPlanId());
        changeSet.setBaseWeekSnapshotVersion(result.baseWeekSnapshotVersion());
        changeSet.setBaseWeekItemHash(result.baseWeekItemHash());
        changeSet.setPreviewVersion(1);
        changeSet.setPreviewSummaryJson(jsonCodec.write(previewSummary(result)));
        changeSet.setResultSource(result.resultSource());
        changeSet.setFallback(result.fallback());
        changeSet.setPreviewRequestKeyHash(requestKeyHash);
        changeSet.setPreviewPayloadHash(payloadHash);
        changeSet.setLockVersion(1);
        changeSet.setExpiresAt(expiresAt);
        Map<String, Object> previewIdentity = new LinkedHashMap<>();
        previewIdentity.put("sourceType", changeSet.getSourceType());
        previewIdentity.put("sourceId", changeSet.getSourceId());
        previewIdentity.put("sourceVersion", changeSet.getSourceVersion());
        previewIdentity.put("sourceContextHash", changeSet.getSourceContextHash());
        previewIdentity.put("selectionHash", selectionHash);
        previewIdentity.put("targetDate", targetDate);
        previewIdentity.put("items", result.items());
        changeSet.setPreviewHash(AgentAdaptivePlanHashUtils.sha256(jsonCodec.write(previewIdentity)));
        return changeSet;
    }

    private List<AgentPlanChangeItem> externalChangeItems(
            Long userId,
            LocalDate targetDate,
            AgentRun baseRun,
            AgentPlanPreviewPlanner.PlanningResult result,
            Map<Long, AgentReviewPlanSuggestion> suggestionById) {
        List<AgentPlanChangeItem> items = new ArrayList<>();
        for (AgentPlanPreviewPlanner.ItemDraft draft : result.items()) {
            AgentPlanChangeItem item = new AgentPlanChangeItem();
            item.setUserId(userId);
            item.setSuggestionId(draft.suggestionId());
            item.setItemKey(draft.itemKey());
            AgentReviewPlanSuggestion suggestion = suggestionById.get(draft.suggestionId());
            item.setSourceItemKey(suggestion == null ? null : suggestion.getSourceItemKey());
            item.setChangeType(draft.changeType());
            item.setTargetDate(targetDate);
            item.setSourceTaskId(draft.sourceTaskId());
            item.setBaseDailyRunId(baseRun == null ? null : baseRun.getId());
            item.setBeforeJson(jsonCodec.write(draft.before()));
            item.setAfterJson(jsonCodec.write(draft.after()));
            item.setDailyImpactJson(jsonCodec.write(Map.of("text", draft.dailyImpact())));
            item.setWeekImpactJson(jsonCodec.write(Map.of("text", draft.weekImpact())));
            item.setValidationStatus(draft.validationStatus());
            item.setWarningCodesJson(jsonCodec.write(draft.warningCodes()));
            item.setConfidenceLevel(draft.confidenceLevel());
            item.setFallback(draft.fallback());
            item.setApplyStatus(draft.applyStatus());
            item.setApplyCount(0);
            items.add(item);
        }
        return items;
    }

    private AgentPlanChangeSummaryVO readSummary(Object value) {
        if (value == null) {
            return new AgentPlanChangeSummaryVO();
        }
        AgentPlanChangeSummaryVO summary = jsonCodec.convert(value, AgentPlanChangeSummaryVO.class);
        return summary == null ? new AgentPlanChangeSummaryVO() : summary;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }

    private int importantPriority(AgentTask task) {
        return switch (firstText(task.getPriority(), "LOW")) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private String safeActionUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("/") && !trimmed.startsWith("//") ? trimmed : null;
    }

    private String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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

    private record SuggestionCandidate(
            String title,
            String content,
            String reason,
            String intentType,
            String targetScope,
            AgentPlanSuggestionIntentDTO intent,
            Map<String, Object> evidence,
            String fingerprint) {
    }
}
