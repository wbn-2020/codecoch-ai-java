package com.codecoachai.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.entity.ReadinessScoreRecord;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.analytics.ApplicationCareerInsightSummaryVO;
import com.codecoachai.ai.agent.domain.vo.analytics.ApplicationQualityVO;
import com.codecoachai.ai.agent.domain.vo.analytics.CareerFunnelVO;
import com.codecoachai.ai.agent.domain.vo.analytics.CareerInsightOverviewVO;
import com.codecoachai.ai.agent.domain.vo.analytics.CareerRecommendedActionVO;
import com.codecoachai.ai.agent.domain.vo.analytics.InterviewWeaknessInsightVO;
import com.codecoachai.ai.agent.domain.vo.analytics.ResumeVersionEffectItemVO;
import com.codecoachai.ai.agent.domain.vo.analytics.ResumeVersionEffectVO;
import com.codecoachai.ai.agent.domain.vo.analytics.WeaknessInsightItemVO;
import com.codecoachai.ai.agent.feign.InterviewWeaknessInsightFeignClient;
import com.codecoachai.ai.agent.feign.ResumeCareerInsightFeignClient;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.mapper.ReadinessScoreRecordMapper;
import com.codecoachai.ai.agent.service.CareerInsightService;
import com.codecoachai.common.core.domain.Result;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerInsightServiceImpl implements CareerInsightService {

    private static final int LOW_SAMPLE_THRESHOLD = 3;
    private static final int LOW_READINESS_SCORE = 60;
    private static final double LOW_AGENT_COMPLETION_RATE = 60D;

    private final AgentTaskMapper agentTaskMapper;
    private final ReadinessScoreRecordMapper readinessScoreRecordMapper;
    private final ResumeCareerInsightFeignClient resumeCareerInsightFeignClient;
    private final InterviewWeaknessInsightFeignClient interviewWeaknessInsightFeignClient;

    @Override
    public CareerInsightOverviewVO personalCareerInsights(Long userId, Integer days) {
        int rangeDays = normalizeDays(days);
        CareerInsightOverviewVO overview = emptyOverview(rangeDays);

        enrichLocalAgentEvidence(userId, rangeDays, overview.getFunnel(), overview.getDataWarnings());
        fetchResumeSummary(userId, rangeDays, overview);
        fetchInterviewSummary(userId, rangeDays, overview);
        normalizeSamples(overview);
        overview.setRecommendedActions(buildRecommendedActions(overview));
        return overview;
    }

    private CareerInsightOverviewVO emptyOverview(int rangeDays) {
        CareerInsightOverviewVO overview = new CareerInsightOverviewVO();
        overview.setRangeDays(rangeDays);
        overview.setGeneratedAt(LocalDateTime.now());
        overview.setFunnel(new CareerFunnelVO());
        overview.setApplicationQuality(new ApplicationQualityVO());
        overview.setResumeVersionEffect(new ResumeVersionEffectVO());
        overview.setInterviewWeaknesses(new InterviewWeaknessInsightVO());
        overview.setRecommendedActions(new ArrayList<>());
        overview.setDataWarnings(new ArrayList<>());
        return overview;
    }

    private void enrichLocalAgentEvidence(Long userId, int rangeDays, CareerFunnelVO funnel, List<String> warnings) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(rangeDays - 1L);
        List<AgentTask> tasks = safeList(() -> agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .eq(AgentTask::getUserId, userId)
                .ge(AgentTask::getDueDate, startDate)
                .le(AgentTask::getDueDate, endDate)), "Agent 任务数据读取失败", warnings);
        long doneCount = tasks.stream()
                .filter(task -> AgentTaskStatusEnum.DONE.name().equals(task.getStatus()))
                .count();
        funnel.setAgentTaskCount((long) tasks.size());
        funnel.setAgentTaskDoneCount(doneCount);
        funnel.setAgentTaskCompletionRate(rate(doneCount, tasks.size()));

        ReadinessScoreRecord latestRecord = safeOne(() -> readinessScoreRecordMapper.selectOne(
                new LambdaQueryWrapper<ReadinessScoreRecord>()
                        .eq(ReadinessScoreRecord::getUserId, userId)
                        .orderByDesc(ReadinessScoreRecord::getScoreDate)
                        .orderByDesc(ReadinessScoreRecord::getCreatedAt)
                        .last("LIMIT 1")), "准备度数据读取失败", warnings);
        if (latestRecord != null) {
            funnel.setLatestReadinessScore(latestRecord.getScore());
        }
    }

    private void fetchResumeSummary(Long userId, int rangeDays, CareerInsightOverviewVO overview) {
        try {
            Result<ApplicationCareerInsightSummaryVO> result =
                    resumeCareerInsightFeignClient.careerInsightSummary(userId, rangeDays);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                overview.getDataWarnings().add("Resume 洞察数据暂不可用，已返回 AI 本地数据");
                return;
            }
            ApplicationCareerInsightSummaryVO summary = result.getData();
            ApplicationQualityVO applicationQuality = firstNonNull(summary.getApplicationQuality(), summary.getQuality());
            if (applicationQuality != null) {
                overview.setApplicationQuality(applicationQuality);
            }
            if (summary.getResumeVersionEffect() != null) {
                overview.setResumeVersionEffect(summary.getResumeVersionEffect());
            }
            mergeResumeFunnel(overview.getFunnel(), summary);
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch resume career insight summary, userId={}, days={}, error={}",
                    userId, rangeDays, ex.toString());
            overview.getDataWarnings().add("Resume 洞察数据暂不可用，已返回部分数据");
        }
    }

    private void fetchInterviewSummary(Long userId, int rangeDays, CareerInsightOverviewVO overview) {
        try {
            Result<InterviewWeaknessInsightVO> result =
                    interviewWeaknessInsightFeignClient.weaknessSummary(userId, rangeDays);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                overview.getDataWarnings().add("Interview 弱项数据暂不可用，已返回部分数据");
                return;
            }
            overview.setInterviewWeaknesses(normalizeInterview(result.getData()));
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch interview weakness summary, userId={}, days={}, error={}",
                    userId, rangeDays, ex.toString());
            overview.getDataWarnings().add("Interview 弱项数据暂不可用，已返回部分数据");
        }
    }

    private void mergeResumeFunnel(CareerFunnelVO target, ApplicationCareerInsightSummaryVO summary) {
        CareerFunnelVO source = summary.getFunnel();
        if (source != null) {
            target.setApplicationCount(safeLong(source.getApplicationCount()));
            target.setFollowedUpApplicationCount(safeLong(source.getFollowedUpApplicationCount()));
            target.setInterviewApplicationCount(safeLong(source.getInterviewApplicationCount()));
            target.setOfferApplicationCount(safeLong(source.getOfferApplicationCount()));
            target.setRejectedOrClosedApplicationCount(safeLong(source.getRejectedOrClosedApplicationCount()));
            target.setInterviewRate(source.getInterviewRate() == null
                    ? rate(target.getInterviewApplicationCount(), target.getApplicationCount())
                    : source.getInterviewRate());
            target.setOfferRate(source.getOfferRate() == null
                    ? rate(target.getOfferApplicationCount(), target.getApplicationCount())
                    : source.getOfferRate());
            return;
        }
        ApplicationQualityVO quality = summary.getApplicationQuality();
        if (quality == null) {
            quality = summary.getQuality();
        }
        if (quality != null) {
            target.setApplicationCount(safeLong(quality.getTotalApplications()));
        }
        target.setApplicationCount(safeLong(summary.getApplicationCount()));
        target.setFollowedUpApplicationCount(safeLong(summary.getFollowedUpApplicationCount()));
        target.setInterviewApplicationCount(safeLong(summary.getInterviewApplicationCount()));
        target.setOfferApplicationCount(safeLong(summary.getOfferApplicationCount()));
        target.setRejectedOrClosedApplicationCount(safeLong(summary.getRejectedOrClosedApplicationCount()));
        target.setInterviewRate(rate(target.getInterviewApplicationCount(), target.getApplicationCount()));
        target.setOfferRate(rate(target.getOfferApplicationCount(), target.getApplicationCount()));
    }

    private void normalizeSamples(CareerInsightOverviewVO overview) {
        ApplicationQualityVO quality = ensureApplicationQuality(overview);
        ResumeVersionEffectVO resumeEffect = ensureResumeVersionEffect(overview);
        InterviewWeaknessInsightVO interview = ensureInterviewWeaknesses(overview);

        if (safeLong(quality.getTotalApplications()) < LOW_SAMPLE_THRESHOLD) {
            addWarningOnce(overview.getDataWarnings(), "投递样本不足，暂不生成强结论");
        }
        if (safeLong(interview.getReportCount()) == 0L) {
            addWarningOnce(overview.getDataWarnings(), "面试报告样本不足，暂不生成弱项结论");
        }
        if (safeLong(resumeEffect.getVersionUsedCount()) < LOW_SAMPLE_THRESHOLD) {
            addWarningOnce(overview.getDataWarnings(), "简历版本样本不足，版本效果建议继续观察");
        }
        if (resumeEffect.getVersions() == null) {
            resumeEffect.setVersions(new ArrayList<>());
        }
        resumeEffect.getVersions().forEach(this::normalizeResumeVersionLabel);
    }

    private InterviewWeaknessInsightVO normalizeInterview(InterviewWeaknessInsightVO interview) {
        if (interview.getTopWeaknesses() == null) {
            interview.setTopWeaknesses(new ArrayList<>());
        }
        for (WeaknessInsightItemVO weakness : interview.getTopWeaknesses()) {
            if (!StringUtils.hasText(weakness.getActionPath())) {
                weakness.setActionPath("/weakness-analysis");
            }
            if (!StringUtils.hasText(weakness.getRecommendedActionType())) {
                weakness.setRecommendedActionType("PRACTICE_WEAKNESS");
            }
        }
        return interview;
    }

    private void normalizeResumeVersionLabel(ResumeVersionEffectItemVO version) {
        long applicationCount = safeLong(version.getApplicationCount());
        if (applicationCount < LOW_SAMPLE_THRESHOLD) {
            version.setSampleLevel("LOW");
            version.setInsightLabel("样本不足");
            return;
        }
        if (!StringUtils.hasText(version.getSampleLevel())) {
            version.setSampleLevel("NORMAL");
        }
        String label = version.getInsightLabel();
        if (!StringUtils.hasText(label) || containsExaggeratedLabel(label)) {
            version.setInsightLabel(safeLong(version.getInterviewCount()) > 0 ? "已进入面试" : "继续观察");
        }
    }

    private List<CareerRecommendedActionVO> buildRecommendedActions(CareerInsightOverviewVO overview) {
        List<CareerRecommendedActionVO> actions = new ArrayList<>();
        ApplicationQualityVO quality = ensureApplicationQuality(overview);
        ResumeVersionEffectVO resumeEffect = ensureResumeVersionEffect(overview);
        InterviewWeaknessInsightVO interview = ensureInterviewWeaknesses(overview);
        CareerFunnelVO funnel = overview.getFunnel();

        if (safeLong(quality.getOverdueFollowUpCount()) > 0) {
            actions.add(action("career-action-overdue-follow-up", "OVERDUE_FOLLOW_UP", "HIGH",
                    "先处理逾期跟进",
                    "有投递已经超过计划跟进时间，建议先补齐沟通记录。",
                    "逾期跟进 " + quality.getOverdueFollowUpCount() + " 条",
                    "查看投递", "/applications?followUp=overdue"));
        }
        if (safeLong(interview.getReportCount()) > 0 && !CollectionUtils.isEmpty(interview.getTopWeaknesses())) {
            WeaknessInsightItemVO weakness = interview.getTopWeaknesses().get(0);
            actions.add(action("career-action-interview-weakness", "INTERVIEW_WEAKNESS", "HIGH",
                    "优先练习面试弱项",
                    "最近的面试报告反复暴露同一类问题，建议安排专项练习。",
                    firstText(weakness.getEvidence(), weakness.getName()),
                    "去练弱项", firstText(weakness.getActionPath(), "/weakness-analysis")));
        }
        if (safeLong(resumeEffect.getApplicationsWithoutVersionCount()) > 0
                || safeLong(quality.getWithResumeVersionCount()) < safeLong(quality.getTotalApplications())) {
            actions.add(action("career-action-resume-version", "RESUME_VERSION_QUALITY", "MEDIUM",
                    "整理投递使用的简历版本",
                    "部分投递没有绑定简历版本，后续复盘会缺少关键证据。",
                    "未绑定版本 " + resumeEffect.getApplicationsWithoutVersionCount() + " 条",
                    "查看简历", "/resumes"));
        }
        if (funnel.getLatestReadinessScore() == null || funnel.getLatestReadinessScore() < LOW_READINESS_SCORE) {
            actions.add(action("career-action-readiness", "READINESS_LOW", "MEDIUM",
                    "补齐下一步求职准备",
                    "准备度分数偏低或暂无记录，建议先完成 Agent 推荐的准备动作。",
                    funnel.getLatestReadinessScore() == null ? "暂无准备度记录"
                            : "最近准备度 " + funnel.getLatestReadinessScore() + " 分",
                    "查看今日任务", "/agent/today"));
        }
        if (safeLong(funnel.getAgentTaskCount()) > 0
                && safeDouble(funnel.getAgentTaskCompletionRate()) < LOW_AGENT_COMPLETION_RATE) {
            actions.add(action("career-action-agent-completion", "AGENT_TASK_COMPLETION_LOW", "LOW",
                    "先完成最高优先级 Agent 任务",
                    "最近 Agent 任务完成率偏低，建议收敛任务量并先完成最高优先级事项。",
                    "完成率 " + funnel.getAgentTaskCompletionRate() + "%",
                    "查看今日任务", "/agent/today"));
        }
        return actions.stream().limit(3).toList();
    }

    private ApplicationQualityVO ensureApplicationQuality(CareerInsightOverviewVO overview) {
        if (overview.getApplicationQuality() == null) {
            overview.setApplicationQuality(new ApplicationQualityVO());
        }
        ApplicationQualityVO quality = overview.getApplicationQuality();
        if (quality.getWarnings() == null) {
            quality.setWarnings(new ArrayList<>());
        }
        return quality;
    }

    private ResumeVersionEffectVO ensureResumeVersionEffect(CareerInsightOverviewVO overview) {
        if (overview.getResumeVersionEffect() == null) {
            overview.setResumeVersionEffect(new ResumeVersionEffectVO());
        }
        return overview.getResumeVersionEffect();
    }

    private InterviewWeaknessInsightVO ensureInterviewWeaknesses(CareerInsightOverviewVO overview) {
        if (overview.getInterviewWeaknesses() == null) {
            overview.setInterviewWeaknesses(new InterviewWeaknessInsightVO());
        }
        InterviewWeaknessInsightVO interview = overview.getInterviewWeaknesses();
        if (interview.getTopWeaknesses() == null) {
            interview.setTopWeaknesses(new ArrayList<>());
        }
        return interview;
    }

    private CareerRecommendedActionVO action(String id, String type, String priority, String title,
                                             String description, String evidence, String actionLabel,
                                             String actionPath) {
        CareerRecommendedActionVO action = new CareerRecommendedActionVO();
        action.setId(id);
        action.setType(type);
        action.setPriority(priority);
        action.setTitle(title);
        action.setDescription(description);
        action.setEvidence(evidence);
        action.setActionLabel(actionLabel);
        action.setActionPath(actionPath);
        return action;
    }

    private <T> List<T> safeList(QueryListSupplier<T> supplier, String warning, List<String> warnings) {
        try {
            List<T> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (RuntimeException ex) {
            log.warn(warning, ex);
            addWarningOnce(warnings, warning);
            return List.of();
        }
    }

    private <T> T safeOne(QueryOneSupplier<T> supplier, String warning, List<String> warnings) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            log.warn(warning, ex);
            addWarningOnce(warnings, warning);
            return null;
        }
    }

    private void addWarningOnce(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private boolean containsExaggeratedLabel(String label) {
        return label.contains("最好") || label.contains("最佳") || label.contains("最差");
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return 30;
        }
        if (days <= 7) {
            return 7;
        }
        if (days <= 30) {
            return 30;
        }
        return 90;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round(numerator * 10000D / denominator) / 100D;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    @FunctionalInterface
    private interface QueryListSupplier<T> {
        List<T> get();
    }

    @FunctionalInterface
    private interface QueryOneSupplier<T> {
        T get();
    }
}
