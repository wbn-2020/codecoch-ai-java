package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobSearchExperimentQueryDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentRelationSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentReviewSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentSaveDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import com.codecoachai.resume.domain.entity.JobSearchExperimentReview;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.vo.JobExperimentAgentContextVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentDetailVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentListVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentMetricsVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentRelationVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentReviewVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentStrategyVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentReviewMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.JobSearchExperimentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class JobSearchExperimentServiceImpl implements JobSearchExperimentService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> STATUSES = Set.of("DRAFT", "RUNNING", "REVIEWED", "ARCHIVED");
    private static final Set<String> RELATION_TYPES = Set.of(
            "RESUME_VERSION", "TARGET_JOB", "JD_ANALYSIS", "JOB_APPLICATION", "INTERVIEW_REPORT",
            "INTERVIEW_SESSION", "PROJECT_EVIDENCE", "ABILITY_PROFILE", "AGENT_TASK", "MATCH_REPORT");

    private final JobSearchExperimentMapper experimentMapper;
    private final JobSearchExperimentRelationMapper relationMapper;
    private final JobSearchExperimentReviewMapper reviewMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    private final ResumeJobMatchReportMapper matchReportMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final JobApplicationEventMapper jobApplicationEventMapper;
    private final ProjectEvidenceMapper projectEvidenceMapper;
    private final UserAbilityProfileMapper userAbilityProfileMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<JobSearchExperimentListVO> list(JobSearchExperimentQueryDTO query) {
        Long userId = currentUserId();
        JobSearchExperimentQueryDTO request = query == null ? new JobSearchExperimentQueryDTO() : query;
        long pageNo = normalizePageNo(request.getPageNo());
        long pageSize = normalizePageSize(request.getPageSize());
        LambdaQueryWrapper<JobSearchExperiment> wrapper = new LambdaQueryWrapper<JobSearchExperiment>()
                .eq(JobSearchExperiment::getUserId, userId)
                .eq(JobSearchExperiment::getDeleted, CommonConstants.NO)
                .eq(StringUtils.hasText(request.getStatus()), JobSearchExperiment::getStatus,
                        normalizeStatus(request.getStatus()))
                .eq(JobSearchExperiment::getDemoFlag,
                        request.getDemoFlag() == null ? CommonConstants.NO : boolFlag(request.getDemoFlag()))
                .ge(request.getStartDate() != null, JobSearchExperiment::getStartDate, request.getStartDate())
                .le(request.getEndDate() != null, JobSearchExperiment::getEndDate, request.getEndDate())
                .and(StringUtils.hasText(request.getKeyword()), w -> w
                        .like(JobSearchExperiment::getTitle, request.getKeyword())
                        .or()
                        .like(JobSearchExperiment::getTargetDirection, request.getKeyword()))
                .orderByDesc(JobSearchExperiment::getUpdatedAt);
        Page<JobSearchExperiment> page = experimentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<JobSearchExperimentListVO> records = page.getRecords().stream()
                .map(this::toListVO)
                .toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobSearchExperimentDetailVO create(JobSearchExperimentSaveDTO dto) {
        Long userId = currentUserId();
        JobSearchExperiment experiment = new JobSearchExperiment();
        experiment.setUserId(userId);
        fillExperiment(experiment, dto);
        experiment.setDemoFlag(CommonConstants.NO);
        experimentMapper.insert(experiment);
        return detail(experiment.getId());
    }

    @Override
    public JobSearchExperimentDetailVO detail(Long id) {
        JobSearchExperiment experiment = ownedExperiment(id);
        JobSearchExperimentDetailVO vo = toDetailVO(experiment);
        vo.setRelations(listRelationEntities(experiment).stream().map(this::toRelationVO).toList());
        List<JobSearchExperimentReviewVO> reviews = listReviews(id);
        vo.setReviews(reviews);
        vo.setLatestReview(reviews.stream()
                .max(Comparator.comparing(JobSearchExperimentReviewVO::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null));
        vo.setMetrics(buildMetrics(experiment, vo.getRelations()));
        vo.setStrategy(buildStrategy(experiment, vo.getMetrics(), vo.getRelations()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobSearchExperimentDetailVO update(Long id, JobSearchExperimentSaveDTO dto) {
        JobSearchExperiment experiment = ownedExperiment(id);
        assertMutableExperiment(experiment);
        fillExperiment(experiment, dto);
        experimentMapper.updateById(experiment);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        JobSearchExperiment experiment = ownedExperiment(id);
        assertMutableExperiment(experiment);
        experimentMapper.deleteById(experiment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobSearchExperimentRelationVO addRelation(Long experimentId, JobSearchExperimentRelationSaveDTO dto) {
        JobSearchExperiment experiment = ownedExperiment(experimentId);
        assertMutableExperiment(experiment);
        String type = normalizeRelationType(dto == null ? null : dto.getRelationType());
        RelationSummary summary = verifyRelation(experiment.getUserId(), type, dto.getRelationId());
        JobSearchExperimentRelation existing = relationMapper.selectOne(new LambdaQueryWrapper<JobSearchExperimentRelation>()
                .eq(JobSearchExperimentRelation::getExperimentId, experimentId)
                .eq(JobSearchExperimentRelation::getRelationType, type)
                .eq(JobSearchExperimentRelation::getRelationId, dto.getRelationId())
                .eq(JobSearchExperimentRelation::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (existing != null) {
            return toRelationVO(existing);
        }
        JobSearchExperimentRelation relation = new JobSearchExperimentRelation();
        relation.setUserId(experiment.getUserId());
        relation.setExperimentId(experimentId);
        relation.setRelationType(type);
        relation.setRelationId(dto.getRelationId());
        relation.setRelationSummary(firstText(dto.getRelationSummary(), summary.summary()));
        relation.setMetadataJson(writeJson(safeMetadata(dto.getMetadata(), summary)));
        relation.setDemoFlag(experiment.getDemoFlag());
        relationMapper.insert(relation);
        refreshExperimentSnapshot(experimentId);
        return toRelationVO(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long experimentId, Long relationId) {
        JobSearchExperiment experiment = ownedExperiment(experimentId);
        assertMutableExperiment(experiment);
        JobSearchExperimentRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<JobSearchExperimentRelation>()
                .eq(JobSearchExperimentRelation::getId, relationId)
                .eq(JobSearchExperimentRelation::getExperimentId, experimentId)
                .eq(JobSearchExperimentRelation::getUserId, experiment.getUserId())
                .eq(JobSearchExperimentRelation::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (relation == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Experiment relation not found");
        }
        relationMapper.deleteById(relation);
        refreshExperimentSnapshot(experimentId);
    }

    @Override
    public JobSearchExperimentMetricsVO metrics(Long id) {
        JobSearchExperiment experiment = ownedExperiment(id);
        return buildMetrics(experiment, listRelationEntities(experiment).stream().map(this::toRelationVO).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobSearchExperimentReviewVO createReview(Long experimentId, JobSearchExperimentReviewSaveDTO dto) {
        JobSearchExperiment experiment = ownedExperiment(experimentId);
        assertMutableExperiment(experiment);
        List<JobSearchExperimentRelationVO> relations = listRelationEntities(experiment).stream().map(this::toRelationVO).toList();
        JobSearchExperimentMetricsVO metrics = buildMetrics(experiment, relations);
        JobSearchExperimentStrategyVO strategy = buildStrategy(experiment, metrics, relations);
        JobSearchExperimentReview review = new JobSearchExperimentReview();
        review.setUserId(experiment.getUserId());
        review.setExperimentId(experimentId);
        review.setFactSummary(String.join("; ", metrics.getFacts()));
        review.setInsightSummary(insightSummary(metrics));
        review.setUnsupportedConclusion(joinedUnsupportedConclusions(metrics));
        review.setSampleWarning(metrics.getSampleWarning());
        review.setNextAction(strategy.getContent());
        review.setStrategyJson(writeJson(safeReviewStrategy(strategy, metrics)));
        review.setAiTraceId(dto == null ? null : dto.getAiTraceId());
        review.setConfidenceLevel(metrics.getConfidenceLevel());
        review.setDemoFlag(experiment.getDemoFlag());
        reviewMapper.insert(review);
        updateExperimentReviewSnapshot(experiment, review, metrics);
        return toReviewVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobSearchExperimentReviewVO generateReview(Long experimentId) {
        JobSearchExperiment experiment = ownedExperiment(experimentId);
        assertMutableExperiment(experiment);
        List<JobSearchExperimentRelationVO> relations = listRelationEntities(experiment).stream().map(this::toRelationVO).toList();
        JobSearchExperimentMetricsVO metrics = buildMetrics(experiment, relations);
        JobSearchExperimentStrategyVO strategy = buildStrategy(experiment, metrics, relations);
        JobSearchExperimentReviewSaveDTO dto = new JobSearchExperimentReviewSaveDTO();
        dto.setFactSummary(String.join("; ", metrics.getFacts()));
        dto.setInsightSummary(insightSummary(metrics));
        dto.setUnsupportedConclusion(joinedUnsupportedConclusions(metrics));
        dto.setSampleWarning(metrics.getSampleWarning());
        dto.setNextAction(strategy.getContent());
        dto.setConfidenceLevel(metrics.getConfidenceLevel());
        Map<String, Object> strategyMap = new LinkedHashMap<>();
        strategyMap.put("title", strategy.getTitle());
        strategyMap.put("content", strategy.getContent());
        strategyMap.put("confidenceLevel", strategy.getConfidenceLevel());
        strategyMap.put("sampleInsufficient", strategy.getSampleInsufficient());
        strategyMap.put("sampleWarning", strategy.getSampleWarning());
        strategyMap.put("actionUrl", strategy.getActionUrl());
        strategyMap.put("resultSource", strategy.getResultSource());
        strategyMap.put("fallback", strategy.getFallback());
        strategyMap.put("sampleBoundary", strategy.getSampleBoundary());
        strategyMap.put("unsupportedConclusions", strategy.getUnsupportedConclusions());
        strategyMap.put("weakObservations", strategy.getWeakObservations());
        strategyMap.put("nextActions", strategy.getNextActions());
        strategyMap.put("actionCandidates", strategy.getActionCandidates());
        strategyMap.put("facts", metrics.getFacts());
        strategyMap.put("evidenceSources", strategy.getEvidenceSources());
        strategyMap.put("qualityGate", strategy.getQualityGate());
        strategyMap.put("reviewDsl", strategy.getReviewDsl());
        dto.setStrategy(strategyMap);
        return createReview(experimentId, dto);
    }

    @Override
    public List<JobSearchExperimentReviewVO> listReviews(Long experimentId) {
        JobSearchExperiment experiment = ownedExperiment(experimentId);
        return reviewMapper.selectList(new LambdaQueryWrapper<JobSearchExperimentReview>()
                        .eq(JobSearchExperimentReview::getUserId, experiment.getUserId())
                        .eq(JobSearchExperimentReview::getExperimentId, experimentId)
                        .eq(JobSearchExperimentReview::getDeleted, CommonConstants.NO)
                        .orderByDesc(JobSearchExperimentReview::getCreatedAt))
                .stream().map(this::toReviewVO).toList();
    }

    @Override
    public List<JobExperimentAgentContextVO> listAgentContextForUser(Long userId, Long targetJobId) {
        if (userId == null) {
            return List.of();
        }
        Set<Long> scopedExperimentIds = scopedExperimentIds(userId, targetJobId);
        if (targetJobId != null && scopedExperimentIds.isEmpty()) {
            return List.of();
        }
        return experimentMapper.selectList(new LambdaQueryWrapper<JobSearchExperiment>()
                        .eq(JobSearchExperiment::getUserId, userId)
                        .eq(JobSearchExperiment::getDeleted, CommonConstants.NO)
                        .eq(JobSearchExperiment::getDemoFlag, CommonConstants.NO)
                        .in(targetJobId != null, JobSearchExperiment::getId, scopedExperimentIds)
                        .in(JobSearchExperiment::getStatus, List.of("DRAFT", "RUNNING", "REVIEWED"))
                        .orderByDesc(JobSearchExperiment::getUpdatedAt)
                        .last("limit 5"))
                .stream()
                .map(this::toAgentContextVO)
                .toList();
    }

    private void fillExperiment(JobSearchExperiment experiment, JobSearchExperimentSaveDTO dto) {
        experiment.setTitle(firstText(dto == null ? null : dto.getTitle(), "Untitled experiment"));
        experiment.setGoal(dto == null ? null : dto.getGoal());
        experiment.setTargetDirection(dto == null ? null : dto.getTargetDirection());
        experiment.setStartDate(dto == null ? null : dto.getStartDate());
        experiment.setEndDate(dto == null ? null : dto.getEndDate());
        experiment.setStatus(normalizeStatus(dto == null ? null : dto.getStatus()));
        if (experiment.getDemoFlag() == null) {
            experiment.setDemoFlag(CommonConstants.NO);
        }
        if (experiment.getSampleCount() == null) {
            experiment.setSampleCount(0);
        }
        if (!StringUtils.hasText(experiment.getConfidenceLevel())) {
            experiment.setConfidenceLevel("LOW");
        }
    }

    private void refreshExperimentSnapshot(Long experimentId) {
        JobSearchExperiment experiment = ownedExperiment(experimentId);
        List<JobSearchExperimentRelationVO> relations = listRelationEntities(experiment).stream().map(this::toRelationVO).toList();
        JobSearchExperimentMetricsVO metrics = buildMetrics(experiment, relations);
        experiment.setSampleCount(metrics.getSampleCount());
        experiment.setConfidenceLevel(metrics.getConfidenceLevel());
        experiment.setSampleWarning(metrics.getSampleWarning());
        experiment.setNextStrategy(buildStrategy(experiment, metrics, relations).getContent());
        experimentMapper.updateById(experiment);
    }

    private void updateExperimentReviewSnapshot(JobSearchExperiment experiment, JobSearchExperimentReview review,
                                                JobSearchExperimentMetricsVO metrics) {
        experiment.setStatus("REVIEWED");
        experiment.setSampleCount(metrics.getSampleCount());
        experiment.setConfidenceLevel(metrics.getConfidenceLevel());
        experiment.setSampleWarning(review.getSampleWarning());
        experiment.setSummary(review.getFactSummary());
        experiment.setNextStrategy(review.getNextAction());
        experimentMapper.updateById(experiment);
    }

    private JobSearchExperimentMetricsVO buildMetrics(JobSearchExperiment experiment,
                                                      List<JobSearchExperimentRelationVO> relations) {
        JobSearchExperimentMetricsVO metrics = new JobSearchExperimentMetricsVO();
        if (relations == null) {
            relations = List.of();
        }
        Map<String, List<JobSearchExperimentRelationVO>> grouped = relations.stream()
                .collect(Collectors.groupingBy(JobSearchExperimentRelationVO::getRelationType));
        List<Long> appIds = ids(grouped.get("JOB_APPLICATION"));
        List<Long> relatedResumeVersionIds = ids(grouped.get("RESUME_VERSION"));
        metrics.setApplicationCount(appIds.size());
        metrics.setTargetJobCount(ids(grouped.get("TARGET_JOB")).size());
        metrics.setProjectEvidenceCount(ids(grouped.get("PROJECT_EVIDENCE")).size());
        metrics.setAgentTaskCount(ids(grouped.get("AGENT_TASK")).size());
        List<JobApplication> apps = List.of();
        if (!appIds.isEmpty()) {
            apps = safeList(jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getUserId, experiment.getUserId())
                    .eq(JobApplication::getDeleted, CommonConstants.NO)
                    .in(JobApplication::getId, appIds)));
            metrics.setFeedbackCount((int) apps.stream().filter(app -> !"SAVED".equalsIgnoreCase(app.getStatus())).count());
            metrics.setInterviewInviteCount((int) apps.stream().filter(app -> "INTERVIEWING".equalsIgnoreCase(app.getStatus())
                    || "OFFER".equalsIgnoreCase(app.getStatus())).count());
            metrics.setOfferCount((int) apps.stream().filter(app -> "OFFER".equalsIgnoreCase(app.getStatus())).count());
            metrics.setRejectedCount((int) apps.stream().filter(app -> "REJECTED".equalsIgnoreCase(app.getStatus())).count());
            List<JobApplicationEvent> events = safeList(jobApplicationEventMapper.selectList(new LambdaQueryWrapper<JobApplicationEvent>()
                    .eq(JobApplicationEvent::getUserId, experiment.getUserId())
                    .eq(JobApplicationEvent::getDeleted, CommonConstants.NO)
                    .in(JobApplicationEvent::getApplicationId, appIds)));
            metrics.setInterviewCompletedCount((int) events.stream()
                    .filter(event -> "INTERVIEW_COMPLETED".equalsIgnoreCase(event.getEventType())).count());
        }
        Map<Long, Integer> resumeUsageCounts = apps.stream()
                .map(JobApplication::getResumeVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(id -> id, id -> 1, Integer::sum, LinkedHashMap::new));
        relatedResumeVersionIds.forEach(id -> resumeUsageCounts.putIfAbsent(id, 0));
        metrics.setResumeVersionUsageCounts(resumeUsageCounts);
        metrics.setResumeVersionCount(resumeUsageCounts.isEmpty() ? relatedResumeVersionIds.size() : resumeUsageCounts.size());
        int sample = Math.max(metrics.getApplicationCount(), metrics.getInterviewCompletedCount());
        metrics.setSampleCount(sample);
        int relationInterviewCount = ids(grouped.get("INTERVIEW_REPORT")).size() + ids(grouped.get("INTERVIEW_SESSION")).size();
        metrics.setInterviewCompletedCount(Math.max(metrics.getInterviewCompletedCount(), relationInterviewCount));
        sample = Math.max(metrics.getApplicationCount(), metrics.getInterviewCompletedCount());
        metrics.setSampleCount(sample);
        metrics.getFacts().add("投递数：" + metrics.getApplicationCount());
        metrics.getFacts().add("反馈数：" + metrics.getFeedbackCount());
        metrics.getFacts().add("面试邀约数：" + metrics.getInterviewInviteCount());
        metrics.getFacts().add("完成面试数：" + metrics.getInterviewCompletedCount());
        metrics.getFacts().add("关联简历版本数：" + metrics.getResumeVersionCount());
        metrics.getFacts().add("关联项目证据数：" + metrics.getProjectEvidenceCount());
        metrics.setResumeVersionSampleInsufficient(resumeVersionSampleInsufficient(metrics));
        metrics.setSampleInsufficient(metrics.getApplicationCount() < 10 || metrics.getInterviewCompletedCount() < 3);
        metrics.setConfidenceLevel(confidenceLevel(metrics));
        metrics.getUnsupportedConclusions().addAll(unsupportedConclusions(metrics));
        if (metrics.getApplicationCount() >= 5 && metrics.getApplicationCount() < 10) {
            metrics.getWeakObservations().add("低置信度弱观察：当前投递已有 5-9 条，可以记录反馈方向，但不能判断策略优劣。");
        } else if (metrics.getApplicationCount() >= 10 && metrics.getInterviewCompletedCount() < 3) {
            metrics.getWeakObservations().add("趋势观察：投递样本达到 10 条，可观察反馈趋势，但面试样本不足，不能判断面试能力变化。");
        } else if (metrics.getApplicationCount() >= 10) {
            metrics.getWeakObservations().add("趋势观察：投递和面试样本达到复盘口径，可观察反馈节奏，但仍需保留岗位、渠道和时间窗口边界。");
        }
        metrics.setSampleWarning(sampleWarning(metrics));
        metrics.setSampleBoundary(buildSampleBoundary(metrics));
        return metrics;
    }

    private JobSearchExperimentStrategyVO buildStrategy(JobSearchExperiment experiment, JobSearchExperimentMetricsVO metrics,
                                                        List<JobSearchExperimentRelationVO> relations) {
        JobSearchExperimentStrategyVO strategy = new JobSearchExperimentStrategyVO();
        strategy.setTitle(strategyTitle(metrics));
        strategy.setConfidenceLevel(metrics.getConfidenceLevel());
        strategy.setSampleInsufficient(metrics.getSampleInsufficient());
        strategy.setSampleWarning(metrics.getSampleWarning());
        strategy.setResultSource("RULE");
        strategy.setFallback(false);
        strategy.setSampleBoundary(metrics.getSampleBoundary());
        strategy.getUnsupportedConclusions().addAll(metrics.getUnsupportedConclusions());
        strategy.getWeakObservations().addAll(metrics.getWeakObservations());
        strategy.setQualityGate(buildStrategyQualityGate(metrics));
        if (metrics.getApplicationCount() < 5) {
            strategy.setContent("当前只展示事实：继续记录投递、反馈和岗位来源，累计到 5 条可比较投递前不判断策略有效性。");
        } else if (metrics.getApplicationCount() < 10) {
            strategy.setContent("可以给低置信度弱观察：保留当前实验方向，补足到 10 条可比较投递后再做策略判断。");
        } else if (metrics.getInterviewCompletedCount() < 3) {
            strategy.setContent("投递样本达到 10 条，可做带边界的趋势观察；先补齐面试复盘样本，完成面试达到 3 次前不判断面试能力变化。");
        } else {
            strategy.setContent("基于当前样本进入下一轮实验：复盘 JD 关键词、项目证据、渠道和反馈节奏，并标注这些影响因素。");
        }
        if (Boolean.TRUE.equals(metrics.getResumeVersionSampleInsufficient())) {
            strategy.setContent(strategy.getContent() + " 简历版本样本不足，本轮不比较简历版本优劣。");
        }
        for (JobSearchExperimentRelationVO relation : relations == null ? List.<JobSearchExperimentRelationVO>of() : relations) {
            JobSearchExperimentStrategyVO.EvidenceSource source = new JobSearchExperimentStrategyVO.EvidenceSource();
            source.setSourceType(relation.getRelationType());
            source.setSourceId(relation.getRelationId());
            source.setSourceSummary(relation.getRelationSummary());
            source.setTrustStatus("VERIFIED");
            source.setSourceUpdatedAt(relation.getCreatedAt());
            source.setMetadata(relation.getMetadata());
            strategy.getEvidenceSources().add(source);
            if (strategy.getEvidenceSources().size() >= 5) {
                break;
            }
        }
        List<Map<String, Object>> actionCandidates = buildActionCandidates(experiment, metrics);
        strategy.setActionCandidates(actionCandidates);
        strategy.setNextActions(actionCandidates);
        strategy.setActionUrl(firstActionRoute(actionCandidates));
        strategy.setReviewDsl(buildReviewDsl(experiment, metrics, strategy));
        return strategy;
    }

    private Map<String, Object> buildSampleBoundary(JobSearchExperimentMetricsVO metrics) {
        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("sampleLevel", sampleLevel(metrics));
        boundary.put("applicationCount", metrics.getApplicationCount());
        boundary.put("feedbackCount", metrics.getFeedbackCount());
        boundary.put("interviewCompletedCount", metrics.getInterviewCompletedCount());
        boundary.put("resumeVersionUsageCounts", metrics.getResumeVersionUsageCounts());
        boundary.put("sampleInsufficient", metrics.getSampleInsufficient());
        boundary.put("resumeVersionSampleInsufficient", metrics.getResumeVersionSampleInsufficient());
        boundary.put("sampleWarning", metrics.getSampleWarning());
        boundary.put("blockedConclusionTypes", blockedConclusionTypes(metrics));
        boundary.put("minComparableApplications", 10);
        boundary.put("minInterviewTrendSamples", 3);
        boundary.put("minResumeVersionUsage", 3);
        return boundary;
    }

    private String sampleLevel(JobSearchExperimentMetricsVO metrics) {
        if (metrics.getApplicationCount() < 5) {
            return "FACT_ONLY";
        }
        if (metrics.getApplicationCount() < 10) {
            return "LOW_CONFIDENCE_OBSERVATION";
        }
        if (metrics.getInterviewCompletedCount() < 3) {
            return "TREND_WITH_INTERVIEW_BOUNDARY";
        }
        return "REVIEWABLE_WITH_BOUNDARY";
    }

    private List<String> blockedConclusionTypes(JobSearchExperimentMetricsVO metrics) {
        List<String> types = new ArrayList<>();
        if (metrics.getApplicationCount() < 5) {
            types.add("STRATEGY_EFFECTIVENESS");
            types.add("CHANNEL_QUALITY");
        } else if (metrics.getApplicationCount() < 10) {
            types.add("STRONG_STRATEGY");
            types.add("DIRECTION_WINNER");
        }
        if (metrics.getInterviewCompletedCount() < 3) {
            types.add("INTERVIEW_ABILITY_TREND");
        }
        if (Boolean.TRUE.equals(metrics.getResumeVersionSampleInsufficient())) {
            types.add("RESUME_VERSION_COMPARISON");
        }
        types.add("SINGLE_FACTOR_CAUSAL_ATTRIBUTION");
        return types;
    }

    private List<Map<String, Object>> buildActionCandidates(JobSearchExperiment experiment,
                                                            JobSearchExperimentMetricsVO metrics) {
        List<Map<String, Object>> actions = new ArrayList<>();
        String evidenceRoute = "/job-experiments/" + experiment.getId() + "?tab=evidence";
        if (metrics.getApplicationCount() < 10) {
            actions.add(actionCandidate(experiment, metrics, "COLLECT_APPLICATION_SAMPLE",
                    "补充投递样本",
                    "继续记录投递、反馈和岗位来源，达到 10 条可比较投递前不做强策略判断。",
                    "当前投递数为 " + metrics.getApplicationCount() + "，样本门槛未达到。",
                    "/applications?experimentId=" + experiment.getId(),
                    metrics.getApplicationCount() < 5 ? "HIGH" : "MEDIUM",
                    false));
        }
        if (metrics.getTargetJobCount() < 1) {
            actions.add(actionCandidate(experiment, metrics, "ADD_TARGET_JOB_EVIDENCE",
                    "补充目标岗位证据",
                    "为实验绑定目标岗位或岗位方向，避免把投递生命周期误写成岗位质量结论。",
                    "当前实验缺少目标岗位证据。",
                    evidenceRoute,
                    "MEDIUM",
                    false));
        }
        if (metrics.getResumeVersionCount() < 1) {
            actions.add(actionCandidate(experiment, metrics, "BIND_RESUME_VERSION",
                    "绑定简历版本",
                    "为本轮实验绑定实际使用的简历版本；没有版本证据时不比较简历策略。",
                    "当前实验缺少简历版本证据。",
                    evidenceRoute,
                    "MEDIUM",
                    false));
        } else if (Boolean.TRUE.equals(metrics.getResumeVersionSampleInsufficient())) {
            actions.add(actionCandidate(experiment, metrics, "COLLECT_RESUME_VERSION_SAMPLE",
                    "补齐简历版本使用样本",
                    "单个简历版本使用少于 3 次时，只记录事实，不比较版本优劣。",
                    "简历版本使用样本不足。",
                    evidenceRoute,
                    "LOW",
                    false));
        }
        if (metrics.getProjectEvidenceCount() < 1) {
            actions.add(actionCandidate(experiment, metrics, "ADD_PROJECT_EVIDENCE",
                    "补充项目证据",
                    "绑定项目证据或匹配报告后，再观察 JD 缺口和反馈之间的关系。",
                    "当前实验缺少项目证据。",
                    evidenceRoute,
                    "MEDIUM",
                    false));
        }
        if (metrics.getApplicationCount() >= 10 && metrics.getInterviewCompletedCount() < 3) {
            actions.add(actionCandidate(experiment, metrics, "COLLECT_INTERVIEW_REVIEW_SAMPLE",
                    "补充面试复盘样本",
                    "记录面试完成事件或面试复盘；少于 3 次完成面试前不判断面试能力趋势。",
                    "投递样本已达到 10 条，但面试样本不足。",
                    null,
                    "MEDIUM",
                    false));
        }
        if (actions.isEmpty()) {
            actions.add(actionCandidate(experiment, metrics, "PLAN_NEXT_EXPERIMENT_ROUND",
                    "规划下一轮实验",
                    "围绕 JD 关键词、项目证据、渠道和反馈节奏设计下一轮实验，保留样本边界。",
                    "当前样本达到复盘口径，但仍不能做单因素因果归因。",
                    "/job-experiments/" + experiment.getId() + "/review",
                    "MEDIUM",
                    !Boolean.TRUE.equals(metrics.getSampleInsufficient())));
        }
        return actions;
    }

    private Map<String, Object> actionCandidate(JobSearchExperiment experiment, JobSearchExperimentMetricsVO metrics,
                                                String actionType, String title, String description, String reason,
                                                String targetRoute, String priority, boolean strongRecommendation) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", "job-experiment:" + experiment.getId() + ":" + actionType.toLowerCase().replace("_", "-"));
        action.put("dedupeKey", actionType + ":job-experiment:" + experiment.getId());
        action.put("sourceType", "JOB_EXPERIMENT_REVIEW");
        action.put("sourceId", experiment.getId());
        action.put("actionType", actionType);
        action.put("title", title);
        action.put("description", description);
        action.put("reason", reason);
        action.put("priority", priority);
        action.put("status", "TODO");
        action.put("confidenceLevel", metrics.getConfidenceLevel());
        action.put("sampleInsufficient", metrics.getSampleInsufficient());
        action.put("strongRecommendation", strongRecommendation && !Boolean.TRUE.equals(metrics.getSampleInsufficient()));
        action.put("qualityGate", buildStrategyQualityGate(metrics));
        if (StringUtils.hasText(targetRoute)) {
            action.put("targetRoute", targetRoute);
            action.put("actionUrl", targetRoute);
        } else {
            action.put("targetRouteMissing", true);
        }
        return action;
    }

    private String firstActionRoute(List<Map<String, Object>> actions) {
        for (Map<String, Object> action : actions == null ? List.<Map<String, Object>>of() : actions) {
            Object route = action.get("targetRoute");
            if (route instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private Map<String, Object> buildReviewDsl(JobSearchExperiment experiment, JobSearchExperimentMetricsVO metrics,
                                               JobSearchExperimentStrategyVO strategy) {
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("schemaVersion", "V4_JOB_EXPERIMENT_REVIEW_DSL_MVP");
        dsl.put("facts", metrics.getFacts());
        dsl.put("limits", metrics.getSampleBoundary());
        dsl.put("sampleBoundary", metrics.getSampleBoundary());
        dsl.put("weakObservations", structuredWeakObservations(metrics));
        dsl.put("unsupportedConclusions", structuredUnsupportedConclusions(metrics));
        dsl.put("hypotheses", List.of(buildHypothesis(experiment)));
        dsl.put("nextActions", strategy.getNextActions());
        dsl.put("actionCandidates", strategy.getActionCandidates());
        dsl.put("evidenceSources", strategy.getEvidenceSources());
        dsl.put("qualityGate", strategy.getQualityGate());
        return dsl;
    }

    private Map<String, Object> buildHypothesis(JobSearchExperiment experiment) {
        Map<String, Object> hypothesis = new LinkedHashMap<>();
        hypothesis.put("targetDirection", experiment.getTargetDirection());
        hypothesis.put("assumption", firstText(experiment.getGoal(), "围绕当前目标方向观察投递反馈与证据覆盖关系"));
        hypothesis.put("timeWindowStart", experiment.getStartDate() == null ? null : experiment.getStartDate().toString());
        hypothesis.put("timeWindowEnd", experiment.getEndDate() == null ? null : experiment.getEndDate().toString());
        hypothesis.put("expectedSignal", "反馈、面试完成样本、简历版本使用次数和项目证据覆盖");
        return hypothesis;
    }

    private List<Map<String, Object>> structuredWeakObservations(JobSearchExperimentMetricsVO metrics) {
        List<Map<String, Object>> observations = new ArrayList<>();
        for (String observation : metrics.getWeakObservations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("observationType", sampleLevel(metrics));
            item.put("text", observation);
            item.put("evidenceCount", metrics.getApplicationCount());
            item.put("confidenceLevel", metrics.getConfidenceLevel());
            item.put("actionHint", Boolean.TRUE.equals(metrics.getSampleInsufficient()) ? "先补样本或补证据" : "进入下一轮实验前保留样本边界");
            observations.add(item);
        }
        return observations;
    }

    private List<Map<String, Object>> structuredUnsupportedConclusions(JobSearchExperimentMetricsVO metrics) {
        List<Map<String, Object>> conclusions = new ArrayList<>();
        for (String conclusion : metrics.getUnsupportedConclusions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conclusionType", conclusionType(conclusion));
            item.put("blockedReason", conclusion);
            item.put("requiredSampleHint", requiredSampleHint(conclusion));
            conclusions.add(item);
        }
        return conclusions;
    }

    private String conclusionType(String conclusion) {
        if (conclusion == null) {
            return "UNKNOWN";
        }
        if (conclusion.contains("面试")) {
            return "INTERVIEW_ABILITY_TREND";
        }
        if (conclusion.contains("简历版本")) {
            return "RESUME_VERSION_COMPARISON";
        }
        if (conclusion.contains("策略") || conclusion.contains("岗位方向")) {
            return "STRONG_STRATEGY";
        }
        return "SINGLE_FACTOR_CAUSAL_ATTRIBUTION";
    }

    private String requiredSampleHint(String conclusion) {
        String type = conclusionType(conclusion);
        if ("INTERVIEW_ABILITY_TREND".equals(type)) {
            return "完成面试至少 3 次后再观察面试能力趋势。";
        }
        if ("RESUME_VERSION_COMPARISON".equals(type)) {
            return "每个参与比较的简历版本至少使用 3 次。";
        }
        if ("STRONG_STRATEGY".equals(type)) {
            return "投递达到 10 条后才允许观察趋势，仍不能做强因果归因。";
        }
        return "需要结合岗位、渠道、时间窗口和多类证据人工复核。";
    }

    private String sampleWarning(JobSearchExperimentMetricsVO metrics) {
        String base;
        if (metrics.getApplicationCount() < 5) {
            base = "样本不足：投递少于 5 条。当前只展示事实，不判断策略有效性。";
        } else if (metrics.getApplicationCount() < 10) {
            base = "样本有限：投递处于 5-9 条。允许低置信度弱建议，不输出强结论。";
        } else if (metrics.getInterviewCompletedCount() < 3) {
            base = "面试样本不足：投递已达到 10 条，但完成面试少于 3 次。可做趋势观察，不判断面试能力变化。";
        } else {
            base = "样本可用于高置信复盘，但仍需说明岗位、渠道、时间窗口等影响因素。";
        }
        if (Boolean.TRUE.equals(metrics.getResumeVersionSampleInsufficient())) {
            base = base + " 简历版本样本不足：单一版本或版本使用次数少于 3 次，不比较简历版本优劣。";
        }
        return base;
    }

    private String confidenceLevel(JobSearchExperimentMetricsVO metrics) {
        if (metrics.getApplicationCount() < 10) {
            return "LOW";
        }
        if (metrics.getInterviewCompletedCount() < 3) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private boolean resumeVersionSampleInsufficient(JobSearchExperimentMetricsVO metrics) {
        if (metrics.getResumeVersionCount() < 2) {
            return true;
        }
        return metrics.getResumeVersionUsageCounts().values().stream().anyMatch(count -> count < 3);
    }

    private List<String> unsupportedConclusions(JobSearchExperimentMetricsVO metrics) {
        List<String> conclusions = new ArrayList<>();
        if (metrics.getApplicationCount() < 5) {
            conclusions.add("不能判断策略有效性或渠道质量。");
        } else if (metrics.getApplicationCount() < 10) {
            conclusions.add("不能输出强策略结论或判断岗位方向明显更优。");
        }
        if (metrics.getInterviewCompletedCount() < 3) {
            conclusions.add("不判断面试能力变化或面试表现趋势。");
        }
        if (Boolean.TRUE.equals(metrics.getResumeVersionSampleInsufficient())) {
            conclusions.add("不比较简历版本优劣。");
        }
        if (conclusions.isEmpty()) {
            conclusions.add("不能完全归因到单一因素，需结合岗位、渠道、简历和面试样本人工复核。");
        }
        return conclusions;
    }

    private String insightSummary(JobSearchExperimentMetricsVO metrics) {
        if (metrics.getApplicationCount() < 5) {
            return "当前投递样本少于 5 条，只展示事实，不给策略强判断。";
        }
        if (metrics.getApplicationCount() < 10) {
            return "当前投递样本为 5-9 条，只给低置信度弱建议。";
        }
        if (metrics.getInterviewCompletedCount() < 3) {
            return "投递样本达到 10 条，可做趋势观察，但面试样本不足。";
        }
        return "样本达到阶段二高置信复盘门槛，但仍需结合影响因素人工复核。";
    }

    private String strategyTitle(JobSearchExperimentMetricsVO metrics) {
        if (metrics.getApplicationCount() < 5) {
            return "继续积累事实样本";
        }
        if (metrics.getApplicationCount() < 10) {
            return "低置信度弱建议";
        }
        if (metrics.getInterviewCompletedCount() < 3) {
            return "趋势观察与面试补样本";
        }
        return "下一轮实验策略";
    }

    private String joinedUnsupportedConclusions(JobSearchExperimentMetricsVO metrics) {
        return String.join("；", metrics.getUnsupportedConclusions());
    }

    private String boundedUnsupportedConclusion(String requested, JobSearchExperimentMetricsVO metrics) {
        String generated = joinedUnsupportedConclusions(metrics);
        if (!StringUtils.hasText(requested)) {
            return generated;
        }
        return generated + "；" + requested.trim();
    }

    private Map<String, Object> buildTrustedSuggestion(JobSearchExperimentStrategyVO reviewStrategy,
                                                       JobSearchExperimentMetricsVO metrics) {
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("schemaVersion", "V4_TRUSTED_RESULT_V1");
        suggestion.put("title", reviewStrategy.getTitle());
        suggestion.put("content", reviewStrategy.getContent());
        suggestion.put("confidenceLevel", metrics.getConfidenceLevel());
        suggestion.put("sampleInsufficient", metrics.getSampleInsufficient());
        suggestion.put("sampleWarning", metrics.getSampleWarning());
        suggestion.put("unsupportedConclusions", metrics.getUnsupportedConclusions());
        suggestion.put("weakObservations", metrics.getWeakObservations());
        suggestion.put("qualityGate", reviewStrategy.getQualityGate());
        suggestion.put("evidenceSources", reviewStrategy.getEvidenceSources());
        suggestion.put("nextAction", reviewStrategy.getActionCandidates().isEmpty()
                ? null
                : reviewStrategy.getActionCandidates().get(0));
        suggestion.put("fallback", false);
        suggestion.put("resultSource", "RULE");
        return suggestion;
    }

    private Map<String, Object> safeReviewStrategy(JobSearchExperimentStrategyVO reviewStrategy, JobSearchExperimentMetricsVO metrics) {
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("title", reviewStrategy.getTitle());
        strategy.put("content", reviewStrategy.getContent());
        strategy.put("actionUrl", reviewStrategy.getActionUrl());
        strategy.put("evidenceSources", reviewStrategy.getEvidenceSources());
        strategy.put("confidenceLevel", metrics.getConfidenceLevel());
        strategy.put("sampleInsufficient", metrics.getSampleInsufficient());
        strategy.put("sampleWarning", metrics.getSampleWarning());
        strategy.put("sampleBoundary", metrics.getSampleBoundary());
        strategy.put("unsupportedConclusions", metrics.getUnsupportedConclusions());
        strategy.put("weakObservations", metrics.getWeakObservations());
        strategy.put("nextActions", reviewStrategy.getNextActions());
        strategy.put("actionCandidates", reviewStrategy.getActionCandidates());
        strategy.put("qualityGate", buildStrategyQualityGate(metrics));
        strategy.put("facts", metrics.getFacts());
        strategy.put("reviewDsl", reviewStrategy.getReviewDsl());
        strategy.put("trustedSuggestion", buildTrustedSuggestion(reviewStrategy, metrics));
        return strategy;
    }

    private Map<String, Object> buildStrategyQualityGate(JobSearchExperimentMetricsVO metrics) {
        Map<String, Object> gate = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        if (Boolean.TRUE.equals(metrics.getSampleInsufficient())) {
            gate.put("gateStatus", "WARN");
            gate.put("suggestionStrength", "LOW_SAMPLE");
            reasons.add(StringUtils.hasText(metrics.getSampleWarning())
                    ? metrics.getSampleWarning()
                    : "样本不足，仅作为下一轮实验假设");
        } else if ("LOW".equalsIgnoreCase(metrics.getConfidenceLevel())) {
            gate.put("gateStatus", "WARN");
            gate.put("suggestionStrength", "WEAK");
            reasons.add("置信度较低，不能作为强结论");
        } else {
            gate.put("gateStatus", "PASS");
            gate.put("suggestionStrength", "NORMAL");
            reasons.add("样本边界、弱观察和不可下结论项已标注");
        }
        gate.put("reasons", reasons);
        gate.put("blockedConclusions", metrics.getUnsupportedConclusions());
        gate.put("sampleSize", metrics.getApplicationCount());
        gate.put("minSampleSize", 10);
        gate.put("sampleBoundary", metrics.getSampleBoundary());
        gate.put("sampleLevel", sampleLevel(metrics));
        gate.put("strongRecommendationAllowed", !Boolean.TRUE.equals(metrics.getSampleInsufficient())
                && !"LOW".equalsIgnoreCase(metrics.getConfidenceLevel()));
        return gate;
    }

    private RelationSummary verifyRelation(Long userId, String type, Long relationId) {
        if (relationId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Relation id is required");
        }
        return switch (type) {
            case "RESUME_VERSION" -> {
                ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getId, relationId).eq(ResumeVersion::getUserId, userId)
                        .eq(ResumeVersion::getDeleted, CommonConstants.NO).last("limit 1"));
                if (version == null) throw relationNotFound(type);
                yield new RelationSummary(version.getVersionName(), Map.of("resumeId", version.getResumeId()));
            }
            case "TARGET_JOB" -> {
                TargetJob job = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                        .eq(TargetJob::getId, relationId).eq(TargetJob::getUserId, userId)
                        .eq(TargetJob::getDeleted, CommonConstants.NO).last("limit 1"));
                if (job == null) throw relationNotFound(type);
                yield new RelationSummary(firstText(job.getCompanyName(), "Target") + " / " + job.getJobTitle(),
                        Map.of("jobTitle", firstText(job.getJobTitle(), "")));
            }
            case "JD_ANALYSIS" -> {
                JobDescriptionAnalysis analysis = jobDescriptionAnalysisMapper.selectOne(
                        new LambdaQueryWrapper<JobDescriptionAnalysis>()
                                .eq(JobDescriptionAnalysis::getId, relationId)
                                .eq(JobDescriptionAnalysis::getUserId, userId)
                                .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)
                                .last("limit 1"));
                if (analysis == null) throw relationNotFound(type);
                yield new RelationSummary(firstText(analysis.getSummary(),
                        firstText(analysis.getCompanyName(), "JD") + " / " + firstText(analysis.getJobTitle(), "分析")),
                        Map.of("targetJobId", analysis.getTargetJobId()));
            }
            case "MATCH_REPORT" -> {
                ResumeJobMatchReport report = matchReportMapper.selectOne(new LambdaQueryWrapper<ResumeJobMatchReport>()
                        .eq(ResumeJobMatchReport::getId, relationId).eq(ResumeJobMatchReport::getUserId, userId)
                        .eq(ResumeJobMatchReport::getDeleted, CommonConstants.NO).last("limit 1"));
                if (report == null) throw relationNotFound(type);
                yield new RelationSummary(firstText(report.getSummary(), "简历匹配报告 #" + relationId),
                        Map.of("status", firstText(report.getStatus(), "")));
            }
            case "JOB_APPLICATION" -> {
                JobApplication app = jobApplicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getId, relationId).eq(JobApplication::getUserId, userId)
                        .eq(JobApplication::getDeleted, CommonConstants.NO).last("limit 1"));
                if (app == null) throw relationNotFound(type);
                yield new RelationSummary(firstText(app.getCompanyName(), "Company") + " / " + firstText(app.getJobTitle(), "Job"),
                        Map.of("status", firstText(app.getStatus(), "")));
            }
            case "PROJECT_EVIDENCE" -> {
                ProjectEvidence evidence = projectEvidenceMapper.selectOne(new LambdaQueryWrapper<ProjectEvidence>()
                        .eq(ProjectEvidence::getId, relationId).eq(ProjectEvidence::getUserId, userId)
                        .eq(ProjectEvidence::getDeleted, CommonConstants.NO).last("limit 1"));
                if (evidence == null) throw relationNotFound(type);
                yield new RelationSummary(evidence.getTitle(), Map.of("completeness", firstText(evidence.getCompletenessStatus(), "")));
            }
            case "ABILITY_PROFILE" -> {
                UserAbilityProfile profile = userAbilityProfileMapper.selectOne(new LambdaQueryWrapper<UserAbilityProfile>()
                        .eq(UserAbilityProfile::getId, relationId)
                        .eq(UserAbilityProfile::getUserId, userId)
                        .eq(UserAbilityProfile::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
                if (profile == null) throw relationNotFound(type);
                yield new RelationSummary(firstText(profile.getSkillCode(), "Ability profile") + " / "
                                + firstText(profile.getStatus(), "UNKNOWN"),
                        Map.of("skillCode", firstText(profile.getSkillCode(), ""),
                                "status", firstText(profile.getStatus(), ""),
                                "confidence", firstText(profile.getConfidence(), "")));
            }
            case "INTERVIEW_REPORT", "INTERVIEW_SESSION", "AGENT_TASK" -> verifyExternalRelation(userId, type, relationId);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported relation type");
        };
    }

    private RelationSummary verifyExternalRelation(Long userId, String type, Long relationId) {
        try {
            List<RelationSummary> rows = switch (type) {
                case "INTERVIEW_REPORT" -> jdbcTemplate.query("""
                                SELECT r.id, r.session_id, r.status, r.summary, s.title AS session_title
                                FROM interview_report r
                                LEFT JOIN interview_session s ON s.id = r.session_id AND s.deleted = 0
                                WHERE r.id = ? AND r.user_id = ? AND r.deleted = 0
                                LIMIT 1
                                """,
                        (rs, rowNum) -> new RelationSummary(
                                firstText(rs.getString("summary"), "Interview report #" + relationId),
                                Map.of("status", firstText(rs.getString("status"), ""),
                                        "sessionId", rs.getLong("session_id"),
                                        "sessionTitle", firstText(rs.getString("session_title"), ""))),
                        relationId, userId);
                case "INTERVIEW_SESSION" -> jdbcTemplate.query("""
                                SELECT id, title, status, report_status, target_position
                                FROM interview_session
                                WHERE id = ? AND user_id = ? AND deleted = 0
                                LIMIT 1
                                """,
                        (rs, rowNum) -> new RelationSummary(
                                firstText(rs.getString("title"), rs.getString("target_position"), "Interview session #" + relationId),
                                Map.of("status", firstText(rs.getString("status"), ""),
                                        "reportStatus", firstText(rs.getString("report_status"), ""),
                                        "targetPosition", firstText(rs.getString("target_position"), ""))),
                        relationId, userId);
                case "AGENT_TASK" -> jdbcTemplate.query("""
                                SELECT id, title, status, task_type, target_job_id, related_biz_type, related_biz_id
                                FROM agent_task
                                WHERE id = ? AND user_id = ? AND deleted = 0
                                LIMIT 1
                                """,
                        (rs, rowNum) -> new RelationSummary(
                                firstText(rs.getString("title"), rs.getString("task_type"), "Agent task #" + relationId),
                                Map.of("status", firstText(rs.getString("status"), ""),
                                        "taskType", firstText(rs.getString("task_type"), ""),
                                        "targetJobId", rs.getLong("target_job_id"),
                                        "relatedBizType", firstText(rs.getString("related_biz_type"), ""),
                                        "relatedBizId", rs.getLong("related_biz_id"))),
                        relationId, userId);
                default -> List.of();
            };
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (DataAccessException ignored) {
            // Cross-module evidence lives in shared business tables; missing schema or rows fail closed.
        }
        throw relationNotFound(type);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Set<Long> scopedExperimentIds(Long userId, Long targetJobId) {
        if (targetJobId == null) {
            return Set.of();
        }
        List<Long> jdAnalysisIds = safeList(jobDescriptionAnalysisMapper.selectList(
                new LambdaQueryWrapper<JobDescriptionAnalysis>()
                        .eq(JobDescriptionAnalysis::getUserId, userId)
                        .eq(JobDescriptionAnalysis::getTargetJobId, targetJobId)
                        .eq(JobDescriptionAnalysis::getDeleted, CommonConstants.NO)))
                .stream()
                .map(JobDescriptionAnalysis::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Long> applicationIds = safeList(jobApplicationMapper.selectList(
                new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(JobApplication::getTargetJobId, targetJobId)
                        .eq(JobApplication::getDeleted, CommonConstants.NO)))
                .stream()
                .map(JobApplication::getId)
                .filter(Objects::nonNull)
                .toList();
        return safeList(relationMapper.selectList(new LambdaQueryWrapper<JobSearchExperimentRelation>()
                        .eq(JobSearchExperimentRelation::getUserId, userId)
                        .eq(JobSearchExperimentRelation::getDeleted, CommonConstants.NO)
                        .eq(JobSearchExperimentRelation::getDemoFlag, CommonConstants.NO)
                        .and(wrapper -> {
                            wrapper.eq(JobSearchExperimentRelation::getRelationType, "TARGET_JOB")
                                    .eq(JobSearchExperimentRelation::getRelationId, targetJobId);
                            if (!jdAnalysisIds.isEmpty()) {
                                wrapper.or()
                                        .eq(JobSearchExperimentRelation::getRelationType, "JD_ANALYSIS")
                                        .in(JobSearchExperimentRelation::getRelationId, jdAnalysisIds);
                            }
                            if (!applicationIds.isEmpty()) {
                                wrapper.or()
                                        .eq(JobSearchExperimentRelation::getRelationType, "JOB_APPLICATION")
                                        .in(JobSearchExperimentRelation::getRelationId, applicationIds);
                            }
                        })))
                .stream()
                .map(JobSearchExperimentRelation::getExperimentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private BusinessException relationNotFound(String type) {
        return new BusinessException(ErrorCode.PARAM_ERROR, type + " relation is not available for current user");
    }

    private List<JobSearchExperimentRelation> listRelationEntities(JobSearchExperiment experiment) {
        return relationMapper.selectList(new LambdaQueryWrapper<JobSearchExperimentRelation>()
                .eq(JobSearchExperimentRelation::getUserId, experiment.getUserId())
                .eq(JobSearchExperimentRelation::getExperimentId, experiment.getId())
                .eq(JobSearchExperimentRelation::getDeleted, CommonConstants.NO)
                .orderByAsc(JobSearchExperimentRelation::getCreatedAt));
    }

    private JobSearchExperiment ownedExperiment(Long id) {
        Long userId = currentUserId();
        JobSearchExperiment experiment = experimentMapper.selectOne(new LambdaQueryWrapper<JobSearchExperiment>()
                .eq(JobSearchExperiment::getId, id)
                .eq(JobSearchExperiment::getUserId, userId)
                .eq(JobSearchExperiment::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (experiment == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Experiment not found");
        }
        return experiment;
    }

    private void assertMutableExperiment(JobSearchExperiment experiment) {
        if (experiment != null && CommonConstants.YES.equals(experiment.getDemoFlag())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Demo experiment is read-only");
        }
    }

    private JobSearchExperimentListVO toListVO(JobSearchExperiment experiment) {
        JobSearchExperimentListVO vo = new JobSearchExperimentListVO();
        vo.setId(experiment.getId());
        vo.setTitle(experiment.getTitle());
        vo.setGoal(experiment.getGoal());
        vo.setTargetDirection(experiment.getTargetDirection());
        vo.setStartDate(experiment.getStartDate());
        vo.setEndDate(experiment.getEndDate());
        vo.setStatus(experiment.getStatus());
        vo.setSampleCount(experiment.getSampleCount());
        vo.setConfidenceLevel(experiment.getConfidenceLevel());
        vo.setSampleWarning(experiment.getSampleWarning());
        vo.setSummary(experiment.getSummary());
        vo.setNextStrategy(experiment.getNextStrategy());
        vo.setDemoFlag(experiment.getDemoFlag());
        vo.setCreatedAt(experiment.getCreatedAt());
        vo.setUpdatedAt(experiment.getUpdatedAt());
        return vo;
    }

    private JobSearchExperimentDetailVO toDetailVO(JobSearchExperiment experiment) {
        JobSearchExperimentDetailVO vo = new JobSearchExperimentDetailVO();
        JobSearchExperimentListVO base = toListVO(experiment);
        vo.setId(base.getId());
        vo.setTitle(base.getTitle());
        vo.setGoal(base.getGoal());
        vo.setTargetDirection(base.getTargetDirection());
        vo.setStartDate(base.getStartDate());
        vo.setEndDate(base.getEndDate());
        vo.setStatus(base.getStatus());
        vo.setSampleCount(base.getSampleCount());
        vo.setConfidenceLevel(base.getConfidenceLevel());
        vo.setSampleWarning(base.getSampleWarning());
        vo.setSummary(base.getSummary());
        vo.setNextStrategy(base.getNextStrategy());
        vo.setDemoFlag(base.getDemoFlag());
        vo.setCreatedAt(base.getCreatedAt());
        vo.setUpdatedAt(base.getUpdatedAt());
        return vo;
    }

    private JobSearchExperimentRelationVO toRelationVO(JobSearchExperimentRelation relation) {
        JobSearchExperimentRelationVO vo = new JobSearchExperimentRelationVO();
        vo.setId(relation.getId());
        vo.setExperimentId(relation.getExperimentId());
        vo.setRelationType(relation.getRelationType());
        vo.setRelationId(relation.getRelationId());
        vo.setRelationSummary(relation.getRelationSummary());
        vo.setMetadata(readMap(relation.getMetadataJson()));
        vo.setDemoFlag(relation.getDemoFlag());
        vo.setCreatedAt(relation.getCreatedAt());
        return vo;
    }

    private JobSearchExperimentReviewVO toReviewVO(JobSearchExperimentReview review) {
        JobSearchExperimentReviewVO vo = new JobSearchExperimentReviewVO();
        vo.setId(review.getId());
        vo.setExperimentId(review.getExperimentId());
        vo.setFactSummary(review.getFactSummary());
        vo.setInsightSummary(review.getInsightSummary());
        vo.setUnsupportedConclusion(review.getUnsupportedConclusion());
        vo.setSampleWarning(review.getSampleWarning());
        vo.setNextAction(review.getNextAction());
        Map<String, Object> strategy = readMap(review.getStrategyJson());
        vo.setStrategy(strategy);
        vo.setFacts(toStringList(strategy.get("facts")));
        vo.setSampleBoundary(toStringKeyMap(strategy.get("sampleBoundary")));
        vo.setUnsupportedConclusions(toStringList(strategy.get("unsupportedConclusions")));
        vo.setWeakObservations(toStringList(strategy.get("weakObservations")));
        List<Map<String, Object>> actionCandidates = toMapList(strategy.get("actionCandidates"));
        if (actionCandidates.isEmpty()) {
            actionCandidates = toMapList(strategy.get("nextActions"));
        }
        vo.setActionCandidates(actionCandidates);
        vo.setReviewDsl(toStringKeyMap(strategy.get("reviewDsl")));
        vo.setTrustedSuggestion(toStringKeyMap(strategy.get("trustedSuggestion")));
        vo.setAiTraceId(review.getAiTraceId());
        vo.setTraceId(review.getAiTraceId());
        vo.setAiCallLogId(null);
        vo.setResultSource("RULE");
        vo.setFallback(false);
        Object qualityGate = strategy.get("qualityGate");
        vo.setQualityGate(toStringKeyMap(qualityGate));
        vo.setConfidenceLevel(review.getConfidenceLevel());
        vo.setDemoFlag(review.getDemoFlag());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setUpdatedAt(review.getUpdatedAt());
        return vo;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> safe = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = toStringKeyMap(item);
            if (map != null) {
                safe.add(map);
            }
        }
        return safe;
    }

    private Map<String, Object> toStringKeyMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                safe.put(String.valueOf(key), item);
            }
        });
        return safe;
    }

    private JobExperimentAgentContextVO toAgentContextVO(JobSearchExperiment experiment) {
        JobExperimentAgentContextVO vo = new JobExperimentAgentContextVO();
        vo.setId(experiment.getId());
        vo.setTitle(experiment.getTitle());
        vo.setTargetDirection(experiment.getTargetDirection());
        vo.setStatus(experiment.getStatus());
        vo.setSampleCount(experiment.getSampleCount());
        vo.setConfidenceLevel(experiment.getConfidenceLevel());
        vo.setSampleWarning(experiment.getSampleWarning());
        vo.setNextStrategy(experiment.getNextStrategy());
        return vo;
    }

    private Map<String, Object> safeMetadata(Map<String, Object> requested, RelationSummary summary) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (summary.metadata() != null) {
            safe.putAll(summary.metadata());
        }
        if (requested != null) {
            requested.forEach((key, value) -> {
                if (StringUtils.hasText(key) && safe.size() < 12) {
                    safe.put(key, value == null ? null : String.valueOf(value));
                }
            });
        }
        return safe;
    }

    private List<Long> ids(List<JobSearchExperimentRelationVO> relations) {
        return relations == null ? List.of() : relations.stream()
                .map(JobSearchExperimentRelationVO::getRelationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Long currentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase() : "DRAFT";
        return STATUSES.contains(value) ? value : "DRAFT";
    }

    private String normalizeRelationType(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
        if (!RELATION_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Unsupported relation type");
        }
        return "MATCH_REPORT".equals(value) ? "MATCH_REPORT" : value;
    }

    private int boolFlag(boolean value) {
        return value ? 1 : 0;
    }

    private long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100L);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
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

    private record RelationSummary(String summary, Map<String, Object> metadata) {
    }
}
