package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceRef;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PulseView;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CampaignPulseRuleEngine {

    public Computation compute(CockpitView current, PulseView previous) {
        Computation result = new Computation();
        result.setDataCutoffAt(current.getDataCutoffAt());
        result.setFacts(facts(current));
        result.setMetrics(metrics(current));
        result.setChanges(changes(current, previous));
        result.setDriftSignals(drift(current, previous));
        result.setLimits(limits(current, previous));
        result.setActionSeeds(copyActions(current.getActionQueue()));
        result.setSources(sources(current));
        result.setConfidenceLevel(confidence(current, previous));
        result.setFallback(false);
        result.setInputHash(inputHash(result));
        return result;
    }

    private Map<String, Object> facts(CockpitView cockpit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("campaignId", cockpit.getCampaign() == null ? null : cockpit.getCampaign().getId());
        result.put("campaignStatus", cockpit.getCampaign() == null ? null : cockpit.getCampaign().getStatus());
        result.put("applicationCount", cockpit.getApplications().size());
        result.put("activeApplicationCount",
                cockpit.getCapacitySummary().getActiveOpportunityCount());
        result.put("stageDistribution", new LinkedHashMap<>(cockpit.getStageDistribution()));
        result.put("deadlineSummary", cockpit.getDeadlineSummary());
        result.put("coverageSummary", cockpit.getCoverageSummary());
        return result;
    }

    private Map<String, Object> metrics(CockpitView cockpit) {
        Map<String, Object> result = new LinkedHashMap<>();
        long overdue = cockpit.getActionQueue().stream()
                .filter(item -> item.getDueAt() != null
                        && item.getDueAt().isBefore(cockpit.getDataCutoffAt()))
                .count();
        result.put("openActionCount", cockpit.getActionQueue().size());
        result.put("openActionMinutes", cockpit.getCapacitySummary().getOpenActionMinutes());
        result.put("weeklyBudgetMinutes", cockpit.getCapacitySummary().getWeeklyBudgetMinutes());
        result.put("activeApplicationCount",
                cockpit.getCapacitySummary().getActiveOpportunityCount());
        result.put("overdueActionCount", overdue);
        result.put("criticalActionCount", cockpit.getActionQueue().stream()
                .filter(item -> "CRITICAL".equalsIgnoreCase(item.getPriority()))
                .count());
        return result;
    }

    private List<String> changes(CockpitView current, PulseView previous) {
        List<String> result = new ArrayList<>();
        if (previous == null) {
            result.add("这是该周期的首个脉搏快照，不伪造前后趋势。");
            return result;
        }
        int previousApplications = intMetric(previous, "activeApplicationCount");
        int currentApplications = current.getCapacitySummary().getActiveOpportunityCount();
        addDelta(result, "活动机会数", currentApplications - previousApplications);

        int previousActions = intMetric(previous, "openActionCount");
        addDelta(result, "待处理行动数", current.getActionQueue().size() - previousActions);

        int previousOverdue = intMetric(previous, "overdueActionCount");
        int currentOverdue = (int) current.getActionQueue().stream()
                .filter(item -> item.getDueAt() != null
                        && item.getDueAt().isBefore(current.getDataCutoffAt()))
                .count();
        addDelta(result, "逾期行动数", currentOverdue - previousOverdue);
        if (result.isEmpty()) {
            result.add("与上一快照相比，核心计数未发生变化。");
        }
        return result;
    }

    private List<String> drift(CockpitView current, PulseView previous) {
        List<String> result = new ArrayList<>();
        int applications = current.getCapacitySummary().getActiveOpportunityCount();
        int maxApplications = current.getOperatingProfile().getMaxActiveOpportunities();
        if (applications > maxApplications) {
            result.add("活动机会数超过用户设置的同时推进上限。");
        }
        if (Boolean.TRUE.equals(current.getCapacitySummary().getOverloaded())) {
            result.add("当前行动预计时间超过用户设置的每周时间预算。");
        }
        long overdue = current.getActionQueue().stream()
                .filter(item -> item.getDueAt() != null
                        && item.getDueAt().isBefore(current.getDataCutoffAt()))
                .count();
        if (overdue > 0) {
            result.add("当前存在明确逾期行动。");
        }
        if (previous != null && comparable(current.getDataCutoffAt(), previous.getDataCutoffAt())) {
            int previousOverdue = intMetric(previous, "overdueActionCount");
            if (overdue > previousOverdue) {
                result.add("相对上一可比较快照，逾期行动数量上升。");
            }
        }
        return result;
    }

    private List<String> limits(CockpitView current, PulseView previous) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(current.getWarnings());
        current.getCoverage().forEach((key, value) -> {
            if (value == null || Boolean.FALSE.equals(value.getAvailable())) {
                result.add("来源区块 " + key + " 不可用，相关结论已降级。");
            } else if (Boolean.TRUE.equals(value.getTruncated())) {
                result.add("来源区块 " + key + " 已截断。");
            }
        });
        if (current.getCapacitySummary().getActiveOpportunityCount() < 3) {
            result.add("活动机会少于 3 个，仅视为低样本弱信号。");
        }
        if (previous == null) {
            result.add("缺少上一快照，不输出趋势结论。");
        } else if (!comparable(current.getDataCutoffAt(), previous.getDataCutoffAt())) {
            result.add("前后快照间隔不足 3 天，仅列举事实变化。");
        }
        return new ArrayList<>(result);
    }

    private List<ActionItem> copyActions(List<ActionItem> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private List<EvidenceRef> sources(CockpitView current) {
        Map<String, EvidenceRef> result = new LinkedHashMap<>();
        for (ActionItem action : current.getActionQueue()) {
            for (EvidenceRef ref : action.getEvidenceRefs()) {
                if (ref == null) {
                    continue;
                }
                String key = Objects.toString(ref.getSourceType(), "") + ":"
                        + Objects.toString(ref.getSourceId(), "") + ":"
                        + Objects.toString(ref.getSourceVersion(), "") + ":"
                        + Objects.toString(ref.getSourceHash(), "");
                result.putIfAbsent(key, ref);
            }
        }
        return new ArrayList<>(result.values());
    }

    private String confidence(CockpitView current, PulseView previous) {
        if ("LOW".equalsIgnoreCase(current.getConfidenceLevel())
                || current.getCapacitySummary().getActiveOpportunityCount() < 3
                || (previous != null && !comparable(
                current.getDataCutoffAt(), previous.getDataCutoffAt()))) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private String inputHash(Computation result) {
        return AgentAdaptivePlanHashUtils.sha256(List.of(
                result.getDataCutoffAt(),
                result.getFacts(),
                result.getMetrics(),
                result.getChanges(),
                result.getDriftSignals(),
                result.getLimits(),
                result.getActionSeeds().stream()
                        .map(item -> item.getSemanticKey() + "|" + item.getSourceHash())
                        .sorted().toList()).toString());
    }

    private int intMetric(PulseView view, String key) {
        Object value = view.getMetrics().get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void addDelta(List<String> target, String label, int delta) {
        if (delta > 0) {
            target.add(label + "增加 " + delta + "。");
        } else if (delta < 0) {
            target.add(label + "减少 " + Math.abs(delta) + "。");
        }
    }

    private boolean comparable(LocalDateTime current, LocalDateTime previous) {
        return current != null && previous != null
                && Math.abs(Duration.between(previous, current).toDays()) >= 3;
    }
}
