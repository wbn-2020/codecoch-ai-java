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
    private static final String VERSION = "v1";

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
            dataset.setVersion(VERSION);
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
            dataset.setVersion(VERSION);
            dataset.setDemoFlag(1);
            datasetMapper.insert(dataset);
        }
        dataset.setStatus("RESET");
        dataset.setLoadedAt(null);
        dataset.setDemoUserId(userId);
        dataset.setResetAt(LocalDateTime.now());
        datasetMapper.updateById(dataset);
        return toStatus(dataset);
    }

    @Override
    public PortfolioDemoStorylineVO storyline() {
        PortfolioDemoStorylineVO vo = new PortfolioDemoStorylineVO();
        vo.setStatus(status());
        JobSearchExperiment experiment = latestDemoExperiment();
        Long experimentId = experiment == null ? null : experiment.getId();

        vo.getSteps().add(step("target-job", "目标岗位", "/job-targets?demoFlag=true", "TARGET_JOB", null,
                "当前演示数据未自动写入目标岗位实体；讲解时按已有页面和脱敏示例说明目标岗位如何驱动后续训练。", "MVP", false));
        vo.getSteps().add(step("jd-match", "JD 匹配报告", "/resume-match?demoFlag=true", "RESUME_MATCH", null,
                "当前演示数据未自动生成 JD 匹配报告实体；讲解时按匹配页能力和样本边界说明。", "MVP", false));
        vo.getSteps().add(step("project-evidence", "项目证据", "/project-evidence?demoFlag=true", "PROJECT_EVIDENCE", null,
                "当前演示数据未自动写入项目证据实体；讲解时按项目证据页面和摘要化证据口径说明。", "MVP", false));
        vo.getSteps().add(step("interview-training", "面试训练室", "/interviews/create?demoFlag=true", "INTERVIEW_TRAINING", null,
                "当前演示数据未自动创建面试训练记录；可展示训练入口，但不计入已覆盖演示数据。", "MVP", false));
        vo.getSteps().add(step("interview-report", "面试报告", "/interviews/history?demoFlag=true", "INTERVIEW_REPORT", null,
                "当前演示数据未自动生成面试报告实体；若需点击报告详情，应先补齐面试报告 seed。", "MVP", false));
        vo.getSteps().add(step("ability-map", "能力图谱", "/ability-map?demoFlag=true", "ABILITY_PROFILE", null,
                "当前演示数据未自动写入能力画像实体；讲解时按能力图谱页面和证据来源口径说明。", "MVP", false));
        vo.getSteps().add(step("job-experiment-review", "求职实验复盘", experimentRoute(experimentId),
                "JOB_EXPERIMENT", experimentId,
                "演示实验仅使用脱敏摘要级证据，样本数不足时只给弱建议和下一轮策略。",
                experimentId == null ? "MISSING" : "READY", experimentId != null));
        vo.getSteps().add(step("agent-today", "Agent 今日任务", "/agent/today?demoFlag=true", "AGENT_TASK", null,
                "当前演示数据未自动写入 Agent 今日任务；可展示任务入口，但不计入已覆盖演示数据。", "MVP", false));

        vo.getOpsSteps().add(step("agent-runs", "Agent 运行记录", "/admin/agent/runs?demoFlag=true", "AGENT_RUN", null,
                "当前演示数据未自动写入 Agent 运行记录；管理侧可按已有环境数据或截图兜底讲解。", "MVP", false));
        vo.getOpsSteps().add(step("prompt-template", "Prompt 模板", "/admin/ai/prompts?demoFlag=true", "PROMPT_TEMPLATE", null,
                "当前演示数据未自动写入 Prompt 模板；管理侧按运行环境配置展示，不计入演示 seed 覆盖。", "MVP", false));
        vo.getOpsSteps().add(step("prompt-regression", "Prompt 回归", "/admin/ai/prompt-regression?demoFlag=true", "PROMPT_REGRESSION", null,
                "当前演示数据未自动写入 Prompt 回归样例；需要发布环境已有样例或截图兜底。", "MVP", false));
        vo.getOpsSteps().add(step("ai-call-logs", "AI 调用日志", "/admin/ai/logs?demoFlag=true", "AI_CALL_LOG", null,
                "当前演示数据未自动写入 AI 调用日志；日志原文如保留，默认仅摘要/hash/脱敏预览可见。", "MVP", false));
        vo.getOpsSteps().add(step("async-tasks", "异步任务中心", "/admin/async-tasks?demoFlag=true", "ASYNC_TASK", null,
                "当前演示数据未自动写入异步任务；发布环境需单独验收任务队列数据。", "MVP", false));
        vo.getOpsSteps().add(step("metrics-dictionary", "指标字典", "/admin/analytics/metrics?demoFlag=true", "METRIC_DICTIONARY", null,
                "当前演示数据未自动写入指标字典；可按管理侧已有配置说明指标口径。", "MVP", false));
        vo.getOpsSteps().add(step("ai-ops-dashboard", "AI 运营看板", "/admin/analytics/ai?demoFlag=true", "AI_OPS", null,
                "当前演示数据未自动写入 AI 运营看板指标；发布环境需用真实验收数据确认。", "MVP", false));
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
        review.setFactSummary("演示链路包含 JD、简历匹配、项目证据、面试训练、面试报告、能力图谱、求职实验和 Agent 任务。");
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
                .eq(PortfolioDemoDataset::getDemoFlag, 1)
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
        vo.setReadOnly(true);
        if (dataset == null) {
            vo.setLoaded(false);
            vo.setDemoData(false);
            vo.setStatus("EMPTY");
            vo.setVersion(VERSION);
            vo.setMessage("当前用户还没有加载演示数据。");
            return vo;
        }
        vo.setLoaded("LOADED".equalsIgnoreCase(dataset.getStatus()));
        vo.setDemoData(Integer.valueOf(1).equals(dataset.getDemoFlag()));
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
        return step(key, title, route, entityType, entityId, summary, status, true);
    }

    private PortfolioDemoStorylineVO.Step step(String key, String title, String route, String entityType,
                                                Long entityId, String summary, String status, boolean demoData) {
        PortfolioDemoStorylineVO.Step step = new PortfolioDemoStorylineVO.Step();
        step.setKey(key);
        step.setTitle(title);
        step.setRoute(route);
        step.setEntityType(entityType);
        step.setEntityId(entityId);
        step.setEvidenceSummary(summary);
        step.setStatus(status);
        step.setDemoData(demoData);
        return step;
    }

    private String experimentRoute(Long entityId) {
        if (entityId == null) {
            return "/job-experiments?demoFlag=true";
        }
        return "/job-experiments/" + entityId + "/review?demoFlag=true";
    }

    private Long currentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
