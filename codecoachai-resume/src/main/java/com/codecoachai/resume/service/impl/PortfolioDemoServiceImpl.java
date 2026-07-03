package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import com.codecoachai.resume.domain.entity.JobSearchExperimentReview;
import com.codecoachai.resume.domain.entity.PortfolioDemoDataset;
import com.codecoachai.resume.domain.vo.PortfolioDemoStatusVO;
import com.codecoachai.resume.domain.vo.PortfolioDemoStorylineVO;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentReviewMapper;
import com.codecoachai.resume.mapper.PortfolioDemoDatasetMapper;
import com.codecoachai.resume.service.PortfolioDemoService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioDemoServiceImpl implements PortfolioDemoService {

    private static final String DATASET_KEY = "portfolio-3b-v1";
    private static final String DATASET_NAME = "CodeCoachAI 作品集演示";

    private final PortfolioDemoDatasetMapper datasetMapper;
    private final JobSearchExperimentMapper experimentMapper;
    private final JobSearchExperimentRelationMapper relationMapper;
    private final JobSearchExperimentReviewMapper reviewMapper;

    @Override
    public PortfolioDemoStatusVO status() {
        PortfolioDemoDataset dataset = currentDataset();
        return toStatus(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioDemoStatusVO load() {
        Long userId = currentUserId();
        PortfolioDemoDataset dataset = currentDataset();
        if (dataset == null) {
            dataset = new PortfolioDemoDataset();
            dataset.setUserId(userId);
            dataset.setDatasetKey(DATASET_KEY);
            dataset.setDatasetName(DATASET_NAME);
            dataset.setVersion("v1");
            dataset.setDemoFlag(1);
        }
        dataset.setStatus("LOADED");
        dataset.setLoadedAt(LocalDateTime.now());
        dataset.setDemoUserId(userId);
        if (dataset.getId() == null) {
            datasetMapper.insert(dataset);
        } else {
            datasetMapper.updateById(dataset);
        }
        ensureDemoExperiment(userId);
        return toStatus(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioDemoStatusVO reset() {
        Long userId = currentUserId();
        experimentMapper.delete(new LambdaQueryWrapper<JobSearchExperiment>()
                .eq(JobSearchExperiment::getUserId, userId)
                .eq(JobSearchExperiment::getDemoFlag, 1));
        relationMapper.delete(new LambdaQueryWrapper<JobSearchExperimentRelation>()
                .eq(JobSearchExperimentRelation::getUserId, userId)
                .eq(JobSearchExperimentRelation::getDemoFlag, 1));
        reviewMapper.delete(new LambdaQueryWrapper<JobSearchExperimentReview>()
                .eq(JobSearchExperimentReview::getUserId, userId)
                .eq(JobSearchExperimentReview::getDemoFlag, 1));
        PortfolioDemoDataset dataset = currentDataset();
        if (dataset == null) {
            dataset = new PortfolioDemoDataset();
            dataset.setUserId(userId);
            dataset.setDatasetKey(DATASET_KEY);
            dataset.setDatasetName(DATASET_NAME);
            dataset.setVersion("v1");
            dataset.setDemoFlag(1);
            datasetMapper.insert(dataset);
        }
        dataset.setStatus("RESET");
        dataset.setResetAt(LocalDateTime.now());
        datasetMapper.updateById(dataset);
        return toStatus(dataset);
    }

    @Override
    public PortfolioDemoStorylineVO storyline() {
        PortfolioDemoStorylineVO vo = new PortfolioDemoStorylineVO();
        vo.setStatus(status());
        JobSearchExperiment experiment = latestDemoExperiment();
        vo.getSteps().add(step("jd", "目标 JD", "/job-targets", "TARGET_JOB", null,
                "查看真实 JD 分析，并说明岗位能力要求。", "READY"));
        vo.getSteps().add(step("match", "简历匹配", "/resume-match", "RESUME_MATCH", null,
                "展示简历版本如何和 JD 做匹配分析。", "READY"));
        vo.getSteps().add(step("evidence", "项目证据", "/project-evidence", "PROJECT_EVIDENCE", null,
                "查看可复用项目证据和 JD 覆盖情况。", "READY"));
        vo.getSteps().add(step("interview", "面试报告", "/interviews/history", "INTERVIEW_REPORT", null,
                "复盘 Rubric、追问树和有证据来源的建议。", "READY"));
        vo.getSteps().add(step("ability", "能力地图", "/ability-map", "ABILITY_PROFILE", null,
                "展示能力状态，同时避免低样本强结论。", "READY"));
        vo.getSteps().add(step("experiment", "求职实验",
                experiment == null ? "/job-experiments" : "/job-experiments/" + experiment.getId(),
                "JOB_EXPERIMENT", experiment == null ? null : experiment.getId(),
                "查看 3A 实验指标、样本不足提示和下一轮策略。", experiment == null ? "MISSING" : "READY"));
        vo.getSteps().add(step("agent", "Agent 今日任务", "/agent/today", "AGENT_TASK", null,
                "把实验复盘后的行动交给今日任务承接。", "READY"));
        vo.getOpsSteps().add(step("ai-ops", "AI Ops", "/admin/analytics/ai", "AI_OPS", null,
                "查看 AI 调用状态、耗时、Token 成本和降级摘要。", "READY"));
        vo.getOpsSteps().add(step("agent-ops", "Agent 运行", "/admin/agent/runs", "AGENT_RUN", null,
                "查看 Agent 运行和任务执行记录。", "READY"));
        vo.getOpsSteps().add(step("prompt", "Prompt 治理", "/admin/ai/prompts", "PROMPT", null,
                "查看 Prompt 模板和回归入口。", "READY"));
        vo.getOpsSteps().add(step("logs", "日志治理", "/admin/operation-logs", "LOG", null,
                "查看操作日志和慢 SQL 入口，不暴露敏感正文。", "READY"));
        return vo;
    }

    private void ensureDemoExperiment(Long userId) {
        JobSearchExperiment existing = latestDemoExperiment();
        if (existing != null) {
            return;
        }
        JobSearchExperiment experiment = new JobSearchExperiment();
        experiment.setUserId(userId);
        experiment.setTitle("Java 后端 Redis 方向投递实验");
        experiment.setGoal("验证 Redis 项目证据强化后，是否改善面试邀约信号。");
        experiment.setTargetDirection("Java 后端 / Redis / 高并发");
        experiment.setStartDate(LocalDate.now().minusDays(7));
        experiment.setEndDate(LocalDate.now().plusDays(7));
        experiment.setStatus("RUNNING");
        experiment.setSampleCount(3);
        experiment.setConfidenceLevel("LOW");
        experiment.setSampleWarning("样本不足：演示数据已明确标记，不进入真实用户统计。");
        experiment.setSummary("演示实验只使用脱敏链路和摘要级证据。");
        experiment.setNextStrategy("继续积累可比较投递，并补强 Redis 项目证据。");
        experiment.setDemoFlag(1);
        experimentMapper.insert(experiment);

        JobSearchExperimentReview review = new JobSearchExperimentReview();
        review.setUserId(userId);
        review.setExperimentId(experiment.getId());
        review.setFactSummary("演示链路包含 JD、简历匹配、项目证据、面试报告、能力地图、求职实验和 Agent 任务。");
        review.setInsightSummary("这是演示专用弱信号，不是真实候选人结论。");
        review.setUnsupportedConclusion("不要把演示用户和真实用户做对比或排名。");
        review.setSampleWarning(experiment.getSampleWarning());
        review.setNextAction(experiment.getNextStrategy());
        review.setConfidenceLevel("LOW");
        review.setDemoFlag(1);
        reviewMapper.insert(review);
    }

    private PortfolioDemoDataset currentDataset() {
        Long userId = currentUserId();
        return datasetMapper.selectOne(new LambdaQueryWrapper<PortfolioDemoDataset>()
                .eq(PortfolioDemoDataset::getUserId, userId)
                .eq(PortfolioDemoDataset::getDatasetKey, DATASET_KEY)
                .eq(PortfolioDemoDataset::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private JobSearchExperiment latestDemoExperiment() {
        Long userId = currentUserId();
        return experimentMapper.selectOne(new LambdaQueryWrapper<JobSearchExperiment>()
                .eq(JobSearchExperiment::getUserId, userId)
                .eq(JobSearchExperiment::getDemoFlag, 1)
                .eq(JobSearchExperiment::getDeleted, CommonConstants.NO)
                .orderByDesc(JobSearchExperiment::getUpdatedAt)
                .last("limit 1"));
    }

    private PortfolioDemoStatusVO toStatus(PortfolioDemoDataset dataset) {
        PortfolioDemoStatusVO vo = new PortfolioDemoStatusVO();
        vo.setDatasetKey(DATASET_KEY);
        vo.setDatasetName(DATASET_NAME);
        vo.setDemoData(true);
        vo.setReadOnly(true);
        if (dataset == null) {
            vo.setLoaded(false);
            vo.setStatus("EMPTY");
            vo.setVersion("v1");
            vo.setMessage("当前用户还没有加载演示数据。");
            return vo;
        }
        vo.setLoaded("LOADED".equalsIgnoreCase(dataset.getStatus()));
        vo.setStatus(dataset.getStatus());
        vo.setVersion(dataset.getVersion());
        vo.setDemoUserId(dataset.getDemoUserId());
        vo.setLoadedAt(dataset.getLoadedAt());
        vo.setResetAt(dataset.getResetAt());
        vo.setMessage("演示数据带有隔离标记，不会触发真实通知、投递或外部调用。");
        return vo;
    }

    private PortfolioDemoStorylineVO.Step step(String key, String title, String route, String entityType,
                                               Long entityId, String summary, String status) {
        PortfolioDemoStorylineVO.Step step = new PortfolioDemoStorylineVO.Step();
        step.setKey(key);
        step.setTitle(title);
        step.setRoute(safeDemoRoute(key, route, entityId));
        step.setEntityType(entityType);
        step.setEntityId(entityId);
        step.setEvidenceSummary(summary);
        step.setStatus(status);
        step.setDemoData(true);
        return step;
    }

    private String safeDemoRoute(String key, String route, Long entityId) {
        if ("experiment".equals(key) && entityId != null) {
            return "/job-experiments/" + entityId + "?demoFlag=true";
        }
        if ("ai-ops".equals(key)) {
            return "/portfolio-demo?ops=ai";
        }
        if ("agent-ops".equals(key)) {
            return "/portfolio-demo?ops=agent";
        }
        if ("prompt".equals(key)) {
            return "/portfolio-demo?ops=prompt";
        }
        if ("logs".equals(key)) {
            return "/portfolio-demo?ops=logs";
        }
        return "/portfolio-demo?step=" + key;
    }

    private Long currentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
