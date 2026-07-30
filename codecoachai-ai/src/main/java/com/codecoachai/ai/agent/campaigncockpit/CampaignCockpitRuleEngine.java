package com.codecoachai.ai.agent.campaigncockpit;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.Application;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.Coverage;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CoverageSummary;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.DeadlineSummary;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceRef;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.OperatingProfile;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CampaignCockpitRuleEngine {

    private static final int DEFAULT_MINUTES = 30;
    private static final Set<String> TERMINAL_APPLICATION_STATUSES = Set.of(
            "ACCEPTED", "DECLINED", "REJECTED", "CLOSED", "WITHDRAWN");

    public CockpitView aggregate(EvidenceEnvelope evidence, LocalDateTime now) {
        CockpitView result = new CockpitView();
        result.setCampaign(campaign(evidence));
        result.setOperatingProfile(profile(evidence.getOperatingProfile()));
        result.setApplications(safeApplications(evidence.getApplications()));
        decorateApplications(result.getApplications(), result.getOperatingProfile(), now);
        result.setDataCutoffAt(evidence.getDataCutoffAt());
        result.setCoverage(evidence.getCoverage() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence.getCoverage()));
        result.setWarnings(evidence.getWarnings() == null
                ? new ArrayList<>() : new ArrayList<>(evidence.getWarnings()));
        result.setStageDistribution(stageDistribution(result.getApplications()));
        result.setDeadlineSummary(deadlines(result.getApplications(), now));
        result.setCoverageSummary(coverageSummary(result.getCoverage()));
        result.setActionQueue(actions(evidence, result.getApplications(), now));
        result.setCapacitySummary(capacity(result, now));
        result.setConfidenceLevel(confidence(result.getApplications(), result.getCoverage()));
        return result;
    }

    public String actionSourceHash(ActionItem item) {
        return item == null ? null : item.getSourceHash();
    }

    private OperatingProfile profile(OperatingProfile value) {
        OperatingProfile result = value == null ? new OperatingProfile() : value;
        result.setWeeklyApplicationTarget(defaultInt(result.getWeeklyApplicationTarget(), 5));
        result.setWeeklyTimeBudgetMinutes(defaultInt(result.getWeeklyTimeBudgetMinutes(), 300));
        result.setMaxActiveOpportunities(defaultInt(result.getMaxActiveOpportunities(), 8));
        result.setStaleAfterDays(defaultInt(result.getStaleAfterDays(), 14));
        result.setDefaultFollowUpDays(defaultInt(result.getDefaultFollowUpDays(), 5));
        return result;
    }

    private CampaignCockpitModels.Campaign campaign(EvidenceEnvelope evidence) {
        CampaignCockpitModels.Campaign result = evidence.getCampaign() == null
                ? new CampaignCockpitModels.Campaign() : evidence.getCampaign();
        if (result.getId() == null) {
            result.setId(evidence.getCampaignId());
        }
        if (!StringUtils.hasText(result.getName())) {
            result.setName(text(evidence.getCampaignTitle(), result.getTitle()));
        }
        if (!StringUtils.hasText(result.getTitle())) {
            result.setTitle(result.getName());
        }
        if (!StringUtils.hasText(result.getStatus())) {
            result.setStatus(evidence.getCampaignStatus());
        }
        return result;
    }

    private List<Application> safeApplications(List<Application> applications) {
        return applications == null ? new ArrayList<>() : applications.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.stableId() != null)
                .sorted(Comparator.comparing(Application::stableId))
                .toList();
    }

    private void decorateApplications(
            List<Application> applications, OperatingProfile operatingProfile, LocalDateTime now) {
        for (Application application : applications) {
            boolean active = !TERMINAL_APPLICATION_STATUSES.contains(
                    text(application.getStatus(), "").toUpperCase(Locale.ROOT));
            LocalDateTime activityAt = application.getStageUpdatedAt() == null
                    ? application.getUpdatedAt() : application.getStageUpdatedAt();
            application.setActive(active);
            application.setStale(active && activityAt != null
                    && !activityAt.isAfter(now.minusDays(operatingProfile.getStaleAfterDays())));
            application.setActionUrl("/applications/" + application.stableId());
        }
    }

    private Map<String, Integer> stageDistribution(List<Application> applications) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Application application : applications) {
            String stage = text(application.getStage(), text(application.getStatus(), "UNKNOWN"));
            result.put(stage, result.getOrDefault(stage, 0) + 1);
        }
        return result;
    }

    private DeadlineSummary deadlines(List<Application> applications, LocalDateTime now) {
        DeadlineSummary result = new DeadlineSummary();
        for (Application application : applications) {
            for (LocalDateTime dueAt : java.util.Arrays.asList(
                    application.getNextFollowUpAt(),
                    application.getOfferDeadlineAt(),
                    application.getInterviewAt(),
                    application.getContactFollowUpAt())) {
                if (dueAt == null) {
                    continue;
                }
                if (dueAt.isBefore(now)) {
                    result.setOverdueCount(result.getOverdueCount() + 1);
                } else if (dueAt.toLocalDate().equals(now.toLocalDate())) {
                    result.setDueTodayCount(result.getDueTodayCount() + 1);
                } else if (!dueAt.isAfter(now.plusDays(7))) {
                    result.setDueWithinSevenDaysCount(result.getDueWithinSevenDaysCount() + 1);
                }
            }
        }
        return result;
    }

    private CoverageSummary coverageSummary(Map<String, Coverage> coverage) {
        CoverageSummary result = new CoverageSummary();
        for (Coverage value : coverage.values()) {
            if (value == null || Boolean.FALSE.equals(value.getAvailable())) {
                result.setUnavailableSections(result.getUnavailableSections() + 1);
            } else {
                result.setAvailableSections(result.getAvailableSections() + 1);
            }
            if (value != null && Boolean.TRUE.equals(value.getTruncated())) {
                result.setTruncatedSections(result.getTruncatedSections() + 1);
            }
        }
        return result;
    }

    private List<ActionItem> actions(
            EvidenceEnvelope evidence, List<Application> applications, LocalDateTime now) {
        List<ActionItem> result = new ArrayList<>();
        for (Application application : applications) {
            if (!Boolean.TRUE.equals(application.getActive())) {
                continue;
            }
            Long id = application.stableId();
            String scope = String.valueOf(id);
            addFollowUp(result, application, evidence, now, scope);
            addInterview(result, application, evidence, now, scope);
            addOffer(result, application, evidence, now, scope);
            addStale(result, application, evidence, now, scope);
            addCoverage(result, application, evidence, now, scope);
        }
        int totalMinutes = result.stream()
                .map(ActionItem::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int budget = profile(evidence.getOperatingProfile()).getWeeklyTimeBudgetMinutes();
        if (totalMinutes > budget) {
            ActionItem overload = new ActionItem();
            overload.setSemanticKey("PLAN_CAPACITY_OVERLOAD:" + evidence.getCampaignId()
                    + ":0:current");
            overload.setSourceHash(
                    com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils.sha256(
                            evidence.getCampaignId() + "|" + totalMinutes + "|" + budget + "|"
                                    + evidence.getDataCutoffAt()));
            overload.setActionType("PLAN_CAPACITY_OVERLOAD");
            overload.setTitle("调整本周行动容量");
            overload.setDescription("当前规则行动预计时间超过用户设置的每周时间预算。");
            overload.setPriority("HIGH");
            overload.setPriorityReasons(List.of("预计行动时间超过每周预算"));
            overload.setEstimatedMinutes(DEFAULT_MINUTES);
            overload.setRelatedBizType("CAREER_CAMPAIGN");
            overload.setRelatedBizId(evidence.getCampaignId());
            overload.setActionUrl("/career-campaigns/" + evidence.getCampaignId() + "/cockpit");
            overload.setConfidenceLevel("MEDIUM");
            result.add(overload);
        }
        return result.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ActionItem::getSemanticKey, item -> item,
                        (first, ignored) -> first, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparingInt(this::priorityRank)
                        .thenComparing(ActionItem::getDueAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ActionItem::getSemanticKey))
                .toList();
    }

    private void addFollowUp(
            List<ActionItem> result, Application app, EvidenceEnvelope evidence,
            LocalDateTime now, String scope) {
        LocalDateTime dueAt = app.getNextFollowUpAt();
        if (dueAt == null) {
            return;
        }
        boolean overdue = dueAt.isBefore(now);
        boolean soon = !overdue && !dueAt.isAfter(now.plusHours(72));
        if (!overdue && !soon) {
            return;
        }
        ActionItem item = base(overdue ? "FOLLOW_UP_OVERDUE" : "FOLLOW_UP_DUE_SOON",
                app, evidence, scope, dueAt);
        item.setActionType(overdue ? "FOLLOW_UP_OVERDUE" : "FOLLOW_UP_DUE_SOON");
        item.setTitle(overdue ? "处理逾期跟进" : "准备近期跟进");
        item.setDescription(overdue ? "该机会的下一次跟进时间已经过去。" : "该机会将在 72 小时内需要跟进。");
        item.setPriority(overdue ? "HIGH" : "MEDIUM");
        item.setPriorityReasons(List.of(overdue ? "明确跟进已逾期" : "明确跟进即将到期"));
        result.add(item);
    }

    private void addInterview(
            List<ActionItem> result, Application app, EvidenceEnvelope evidence,
            LocalDateTime now, String scope) {
        if (app.getInterviewAt() == null) {
            return;
        }
        if (app.getInterviewAt().isAfter(now) && interviewPrepMissing(app)) {
            ActionItem item = base(
                    "INTERVIEW_PREP_MISSING", app, evidence, scope, app.getInterviewAt());
            item.setActionType("INTERVIEW_PREP_MISSING");
            item.setTitle("补齐面试准备");
            item.setDescription("已有面试时间，但准备材料状态未确认完成。");
            item.setPriority(app.getInterviewAt().isBefore(now.plusDays(3))
                    ? "CRITICAL" : "HIGH");
            item.setPriorityReasons(List.of("面试时间已明确", "准备状态未完成"));
            result.add(item);
        }
        if (app.getInterviewAt().isBefore(now) && interviewReviewMissing(app)) {
            ActionItem review = base("INTERVIEW_REVIEW_MISSING", app, evidence, scope,
                    app.getInterviewAt());
            review.setActionType("INTERVIEW_REVIEW_MISSING");
            review.setTitle("补充面试复盘");
            review.setDescription("面试时间已过去，但复盘状态未确认完成。");
            review.setPriority("MEDIUM");
            review.setPriorityReasons(List.of("面试已结束", "复盘状态未完成"));
            result.add(review);
        }
    }

    private void addOffer(
            List<ActionItem> result, Application app, EvidenceEnvelope evidence,
            LocalDateTime now, String scope) {
        LocalDateTime dueAt = app.getOfferDeadlineAt();
        if (dueAt == null || dueAt.isAfter(now.plusDays(7))) {
            return;
        }
        ActionItem item = base("OFFER_DEADLINE", app, evidence, scope, dueAt);
        item.setActionType("OFFER_DEADLINE");
        item.setTitle(dueAt.isBefore(now) ? "处理已过期 Offer 截止事项" : "准备 Offer 截止事项");
        item.setDescription("Offer 截止时间已明确，需要在既有事实范围内处理。");
        item.setPriority(dueAt.isBefore(now) ? "CRITICAL" : "HIGH");
        item.setPriorityReasons(List.of(dueAt.isBefore(now) ? "Offer 截止已逾期" : "Offer 截止在 7 天内"));
        result.add(item);
    }

    private void addStale(
            List<ActionItem> result, Application app, EvidenceEnvelope evidence,
            LocalDateTime now, String scope) {
        OperatingProfile profile = profile(evidence.getOperatingProfile());
        LocalDateTime updatedAt = app.getStageUpdatedAt() == null
                ? app.getUpdatedAt() : app.getStageUpdatedAt();
        if (updatedAt == null || updatedAt.isAfter(now.minusDays(profile.getStaleAfterDays()))) {
            return;
        }
        ActionItem item = base("APPLICATION_STALE", app, evidence, scope, updatedAt);
        item.setActionType("APPLICATION_STALE");
        item.setTitle("检查长期未更新机会");
        item.setDescription("该机会超过用户设定的停滞阈值未见阶段变化。");
        item.setPriority("MEDIUM");
        item.setPriorityReasons(List.of("超过用户设定的停滞阈值"));
        result.add(item);
    }

    private void addCoverage(
            List<ActionItem> result, Application app, EvidenceEnvelope evidence,
            LocalDateTime now, String scope) {
        if (materialCoverageLow(app)) {
            ActionItem item = base("MATERIAL_COVERAGE_LOW", app, evidence, scope, null);
            item.setActionType("MATERIAL_COVERAGE_LOW");
            item.setTitle("补齐投递材料覆盖");
            item.setDescription("当前材料覆盖低于规则阈值 60%。");
            item.setPriority("MEDIUM");
            item.setPriorityReasons(List.of("材料覆盖低于 60%"));
            result.add(item);
        }
        if (researchCoverageLow(app)) {
            ActionItem item = base("RESEARCH_COVERAGE_LOW", app, evidence, scope, null);
            item.setActionType("RESEARCH_COVERAGE_LOW");
            item.setTitle("补齐机会研究覆盖");
            item.setDescription("当前研究覆盖低于规则阈值 60%。");
            item.setPriority("LOW");
            item.setPriorityReasons(List.of("研究覆盖低于 60%"));
            result.add(item);
        }
        if (app.getContactFollowUpAt() != null
                && !app.getContactFollowUpAt().isAfter(now.plusHours(72))) {
            ActionItem item = base("CONTACT_FOLLOW_UP_DUE", app, evidence, scope,
                    app.getContactFollowUpAt());
            item.setActionType("CONTACT_FOLLOW_UP_DUE");
            item.setTitle("处理联系人跟进");
            item.setDescription("联系人跟进时间已进入行动队列。");
            item.setPriority(app.getContactFollowUpAt().isBefore(now)
                    ? "HIGH" : "MEDIUM");
            item.setPriorityReasons(List.of("联系人跟进时间已明确"));
            result.add(item);
        }
    }

    private ActionItem base(
            String actionType, Application app, EvidenceEnvelope evidence,
            String scope, LocalDateTime dueAt) {
        ActionItem item = new ActionItem();
        item.setSemanticKey(actionType + ":" + evidence.getCampaignId() + ":"
                + app.stableId() + ":" + scope);
        item.setSourceHash(sourceHash(app, evidence));
        item.setApplicationId(app.stableId());
        item.setApplicationPriority(app.getPriorityLevel());
        item.setRelatedBizType("JOB_APPLICATION");
        item.setRelatedBizId(app.stableId());
        item.setDueAt(dueAt);
        item.setEstimatedMinutes(DEFAULT_MINUTES);
        item.setConfidenceLevel("MEDIUM");
        item.setActionUrl("/applications/" + app.stableId());
        item.setEvidenceRefs(app.getEvidenceRefs() == null
                ? new ArrayList<>() : new ArrayList<>(app.getEvidenceRefs()));
        if (item.getEvidenceRefs().isEmpty()) {
            EvidenceRef ref = new EvidenceRef();
            ref.setSourceType("JOB_APPLICATION");
            ref.setSourceId(app.stableId());
            ref.setCampaignId(evidence.getCampaignId());
            ref.setObservedAt(evidence.getDataCutoffAt());
            ref.setSummary("机会摘要");
            item.setEvidenceRefs(List.of(ref));
        }
        return item;
    }

    private String sourceHash(Application app, EvidenceEnvelope evidence) {
        if (StringUtils.hasText(app.getSourceHash())) {
            return app.getSourceHash().trim();
        }
        StringBuilder value = new StringBuilder()
                .append(evidence.getCampaignId()).append('|')
                .append(app.stableId()).append('|')
                .append(app.getStatus()).append('|')
                .append(app.getStage()).append('|')
                .append(app.getNextFollowUpAt()).append('|')
                .append(app.getInterviewAt()).append('|')
                .append(app.getOfferDeadlineAt()).append('|')
                .append(app.getContactFollowUpAt()).append('|')
                .append(app.getInterviewPrepMissing()).append('|')
                .append(app.getInterviewReviewMissing()).append('|')
                .append(app.getMaterialCoverageLow()).append('|')
                .append(app.getResearchCoverageLow()).append('|')
                .append(app.getUpdatedAt());
        return com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils.sha256(
                value.toString());
    }

    private com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CapacitySummary capacity(
            CockpitView view, LocalDateTime now) {
        com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CapacitySummary result =
                new com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CapacitySummary();
        int budget = view.getOperatingProfile().getWeeklyTimeBudgetMinutes();
        int open = view.getActionQueue().stream()
                .filter(item -> !"LOW".equalsIgnoreCase(item.getConfidenceLevel()))
                .map(ActionItem::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        result.setWeeklyBudgetMinutes(budget);
        result.setOpenActionMinutes(open);
        result.setAvailableMinutes(budget);
        result.setUsedMinutes(open);
        result.setRemainingMinutes(Math.max(0, budget - open));
        result.setActiveOpportunityCount((int) view.getApplications().stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .count());
        result.setMaxActiveOpportunities(
                view.getOperatingProfile().getMaxActiveOpportunities());
        result.setWeeklyApplicationTarget(
                view.getOperatingProfile().getWeeklyApplicationTarget());
        LocalDateTime weekStart = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        result.setWeeklyApplications((int) view.getApplications().stream()
                .map(Application::getCreatedAt)
                .filter(Objects::nonNull)
                .filter(createdAt -> !createdAt.isBefore(weekStart)
                        && !createdAt.isAfter(now))
                .count());
        result.setOverloaded(open > budget);
        return result;
    }

    private boolean interviewPrepMissing(Application application) {
        return application.getInterviewPrepMissing() != null
                ? Boolean.TRUE.equals(application.getInterviewPrepMissing())
                : !Boolean.TRUE.equals(application.getInterviewPreparationReady());
    }

    private boolean interviewReviewMissing(Application application) {
        return application.getInterviewReviewMissing() != null
                ? Boolean.TRUE.equals(application.getInterviewReviewMissing())
                : !Boolean.TRUE.equals(application.getInterviewReviewReady());
    }

    private boolean materialCoverageLow(Application application) {
        return application.getMaterialCoverageLow() != null
                ? Boolean.TRUE.equals(application.getMaterialCoverageLow())
                : application.getMaterialCoveragePercent() != null
                && application.getMaterialCoveragePercent() < 60;
    }

    private boolean researchCoverageLow(Application application) {
        return application.getResearchCoverageLow() != null
                ? Boolean.TRUE.equals(application.getResearchCoverageLow())
                : application.getResearchCoveragePercent() != null
                && application.getResearchCoveragePercent() < 60;
    }

    private String confidence(
            List<Application> applications, Map<String, Coverage> coverage) {
        boolean unavailable = coverage.values().stream()
                .anyMatch(item -> item != null && Boolean.FALSE.equals(item.getAvailable()));
        long activeApplications = applications.stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .count();
        if (activeApplications < 3 || unavailable) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private int priorityRank(ActionItem item) {
        return switch (text(item.getPriority(), "LOW").toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
