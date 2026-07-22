package com.codecoachai.ai.agent.campaigncockpit;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionDecisionRequest;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionDecisionView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.Campaign;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ScenarioPreview;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ScenarioRequest;
import com.codecoachai.ai.agent.campaigncockpit.domain.entity.CampaignActionDecision;
import com.codecoachai.ai.agent.campaigncockpit.mapper.CampaignActionDecisionMapper;
import com.codecoachai.ai.agent.campaigncockpit.V8FeatureGate;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignCockpitServiceImpl implements CampaignCockpitService {

    private final CampaignCockpitEvidenceClient evidenceClient;
    private final CampaignCockpitRuleEngine ruleEngine;
    private final CampaignActionDecisionMapper decisionMapper;
    private final AgentBusinessTimeProvider timeProvider;

    @Override
    public CockpitView get(Long userId, Long campaignId) {
        requireIds(userId, campaignId);
        EvidenceEnvelope evidence = evidence(userId, campaignId);
        CockpitView result = ruleEngine.aggregate(evidence, timeProvider.now());
        result.setActionQueue(projectDecisions(userId, campaignId, result.getActionQueue()));
        if (result.getCampaign() == null) {
            Campaign campaign = new Campaign();
            campaign.setId(campaignId);
            campaign.setTitle(evidence.getCampaignTitle());
            campaign.setStatus(evidence.getCampaignStatus());
            result.setCampaign(campaign);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ActionDecisionView decide(Long userId, Long campaignId, ActionDecisionRequest request) {
        requireIds(userId, campaignId);
        if (request == null || !StringUtils.isNotBlank(request.getSemanticKey())
                || !StringUtils.isNotBlank(request.getSourceHash())
                || !StringUtils.isNotBlank(request.getDecisionStatus())
                || !StringUtils.isNotBlank(request.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行动决策请求不完整。");
        }
        String status = request.getDecisionStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SNOOZED", "DISMISSED", "REOPENED", "PLAN_PREVIEWED").contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的行动决策状态。");
        }
        if ("SNOOZED".equals(status)
                && (request.getSnoozedUntil() == null
                || !request.getSnoozedUntil().isAfter(timeProvider.now()))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "稍后处理必须设置未来时间。");
        }
        CockpitView cockpit = get(userId, campaignId);
        ActionItem action = cockpit.getActionQueue().stream()
                .filter(item -> Objects.equals(item.getSemanticKey(), request.getSemanticKey())
                        && Objects.equals(item.getSourceHash(), request.getSourceHash()))
                .findFirst().orElse(null);
        if (action == null) {
            throw new BusinessException(ErrorCode.STALE_SOURCE_VERSION, "行动来源已经变化，请刷新后重试。");
        }
        String idempotencyHash = AgentAdaptivePlanHashUtils.sha256(request.getIdempotencyKey().trim());
        String payloadHash = AgentAdaptivePlanHashUtils.sha256(
                request.getSemanticKey().trim() + "|" + request.getSourceHash().trim() + "|"
                        + status + "|" + Objects.toString(request.getSnoozedUntil(), "")
                        + "|" + Objects.toString(request.getReason(), ""));
        CampaignActionDecision existing = decisionMapper.selectByIdempotency(userId, idempotencyHash);
        if (existing != null) {
            if (!Objects.equals(existing.getPayloadHash(), payloadHash)) {
                throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "同一幂等键不能用于不同的行动决策。");
            }
            return toView(existing);
        }
        CampaignActionDecision decision = new CampaignActionDecision();
        decision.setUserId(userId);
        decision.setCampaignId(campaignId);
        decision.setSemanticKey(action.getSemanticKey());
        decision.setSourceHash(action.getSourceHash());
        decision.setActionType(action.getActionType());
        decision.setDecisionStatus(status);
        decision.setSnoozedUntil(request.getSnoozedUntil());
        decision.setReason(request.getReason());
        decision.setIdempotencyKeyHash(idempotencyHash);
        decision.setPayloadHash(payloadHash);
        decision.setDecidedAt(timeProvider.now());
        decision.setDeleted(0);
        try {
            decisionMapper.insert(decision);
        } catch (DuplicateKeyException ex) {
            CampaignActionDecision concurrent = decisionMapper.selectByIdempotency(
                    userId, idempotencyHash);
            if (concurrent != null && Objects.equals(concurrent.getPayloadHash(), payloadHash)) {
                return toView(concurrent);
            }
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "行动决策发生并发冲突，请重试。");
        }
        return toView(decision);
    }

    public List<ActionDecisionView> decisions(Long userId, Long campaignId) {
        requireIds(userId, campaignId);
        return decisionMapper.selectByCampaign(userId, campaignId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public ScenarioPreview previewScenario(Long userId, Long campaignId, ScenarioRequest request) {
        requireIds(userId, campaignId);
        if (request == null || request.getAvailableMinutes() == null
                || !StringUtils.isNotBlank(request.getFocusMode())
                || request.getAvailableMinutes() < 0
                || request.getMaxApplications() == null
                || request.getMaxApplications() < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "情景预览参数不完整。");
        }
        String focusMode = request.getFocusMode().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DEADLINE_FIRST", "HIGH_PRIORITY_FIRST", "BALANCED").contains(focusMode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的情景关注策略。");
        }
        CockpitView cockpit = get(userId, campaignId);
        List<ActionItem> candidates = cockpit.getActionQueue().stream()
                .filter(item -> Boolean.TRUE.equals(request.getIncludeLowConfidence())
                        || !"LOW".equalsIgnoreCase(item.getConfidenceLevel()))
                .sorted(scenarioComparator(focusMode))
                .toList();
        ScenarioPreview result = new ScenarioPreview();
        result.setSourceHash(actionQueueHash(candidates));
        Set<Long> applications = new HashSet<>();
        int used = 0;
        for (ActionItem item : candidates) {
            int minutes = item.getEstimatedMinutes() == null ? 30 : item.getEstimatedMinutes();
            boolean applicationAvailable = item.getApplicationId() == null
                    || applications.contains(item.getApplicationId())
                    || applications.size() < request.getMaxApplications();
            if (used + minutes <= request.getAvailableMinutes() && applicationAvailable) {
                result.getSelectedActions().add(item);
                used += minutes;
                if (item.getApplicationId() != null) {
                    applications.add(item.getApplicationId());
                }
            } else {
                result.getDeferredActions().add(item);
            }
        }
        result.setTotalEstimatedMinutes(used);
        result.setCapacityRemainingMinutes(Math.max(0, request.getAvailableMinutes() - used));
        if (!result.getDeferredActions().isEmpty()) {
            result.getTradeoffs().add("容量不足，部分行动进入 deferred，不会被静默删除。");
        }
        if ("DEADLINE_FIRST".equals(focusMode)) {
            result.getTradeoffs().add("当前策略优先安排有明确截止时间的行动。");
        } else if ("HIGH_PRIORITY_FIRST".equals(focusMode)) {
            result.getTradeoffs().add("当前策略优先安排高优先级机会，同时保留 CRITICAL 截止事项。");
        } else {
            result.getTradeoffs().add("当前策略按行动类型和规则优先级做确定性平衡。");
        }
        if (Boolean.FALSE.equals(request.getIncludeLowConfidence())) {
            result.getLimits().add("低置信度行动未纳入本次情景。");
        }
        return result;
    }

    private EvidenceEnvelope evidence(Long userId, Long campaignId) {
        try {
            Result<CampaignCockpitModels.EvidenceEnvelope> response = evidenceClient.get(
                    userId, campaignId, timeProvider.now(), 100, 100);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周期事实证据暂不可用。");
            }
            EvidenceEnvelope evidence = response.getData();
            if (!Objects.equals(userId, evidence.getUserId())
                    || !Objects.equals(campaignId, evidence.getCampaignId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "周期事实归属校验失败。");
            }
            return evidence;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Campaign cockpit evidence lookup failed campaignId={}", campaignId, ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "周期事实证据暂不可用。");
        }
    }

    private List<ActionItem> projectDecisions(
            Long userId, Long campaignId, List<ActionItem> actions) {
        LocalDateTime now = timeProvider.now();
        List<ActionItem> result = new ArrayList<>();
        for (ActionItem item : actions) {
            CampaignActionDecision decision = decisionMapper.selectBySemanticSource(
                    userId, campaignId, item.getSemanticKey(), item.getSourceHash());
            if (decision == null) {
                result.add(item);
                continue;
            }
            String status = decision.getDecisionStatus();
            if ("DISMISSED".equals(status)) {
                continue;
            }
            if ("SNOOZED".equals(status)) {
                if (decision.getSnoozedUntil() != null
                        && decision.getSnoozedUntil().isAfter(now)) {
                    continue;
                }
                item.setDecisionStatus("OPEN");
            } else {
                item.setDecisionStatus("REOPENED".equals(status) ? "OPEN" : status);
            }
            result.add(item);
        }
        return result;
    }

    private Comparator<ActionItem> scenarioComparator(String focusMode) {
        Comparator<ActionItem> base = Comparator.comparingInt(this::priorityRank)
                .thenComparing(ActionItem::getDueAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ActionItem::getSemanticKey);
        if ("DEADLINE_FIRST".equals(focusMode)) {
            return Comparator.comparing(ActionItem::getDueAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(base);
        }
        if ("HIGH_PRIORITY_FIRST".equals(focusMode)) {
            return Comparator.comparingInt(this::applicationPriorityRank)
                    .thenComparing(base);
        }
        return base;
    }

    private int priorityRank(ActionItem item) {
        return switch (Objects.toString(item.getPriority(), "LOW").toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    private int applicationPriorityRank(ActionItem item) {
        int actionRank = priorityRank(item);
        String priority = Objects.toString(item.getApplicationPriority(), "MEDIUM")
                .toUpperCase(Locale.ROOT);
        int applicationRank = switch (priority) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
        return Math.min(actionRank, applicationRank);
    }

    private String actionQueueHash(List<ActionItem> items) {
        return AgentAdaptivePlanHashUtils.sha256(items.stream()
                .map(item -> item.getSemanticKey() + "|" + item.getSourceHash())
                .sorted()
                .toList()
                .toString());
    }

    private ActionDecisionView toView(CampaignActionDecision source) {
        ActionDecisionView result = new ActionDecisionView();
        result.setId(source.getId());
        result.setCampaignId(source.getCampaignId());
        result.setSemanticKey(source.getSemanticKey());
        result.setSourceHash(source.getSourceHash());
        result.setActionType(source.getActionType());
        result.setDecisionStatus(source.getDecisionStatus());
        result.setSnoozedUntil(source.getSnoozedUntil());
        result.setReason(source.getReason());
        result.setDecidedAt(source.getDecidedAt());
        return result;
    }

    private void requireIds(Long userId, Long campaignId) {
        if (userId == null || campaignId == null || userId <= 0 || campaignId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户和周期不能为空。");
        }
    }
}
