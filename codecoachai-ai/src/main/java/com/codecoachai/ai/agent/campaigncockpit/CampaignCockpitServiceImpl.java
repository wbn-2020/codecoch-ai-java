package com.codecoachai.ai.agent.campaigncockpit;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
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

    private void requireIds(Long userId, Long campaignId) {
        if (userId == null || campaignId == null || userId <= 0 || campaignId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户和周期不能为空。");
        }
    }
}
