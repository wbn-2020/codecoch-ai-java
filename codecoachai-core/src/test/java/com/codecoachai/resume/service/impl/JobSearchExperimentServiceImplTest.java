package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.config.V12FeatureGate;
import com.codecoachai.resume.domain.dto.JobSearchExperimentRelationSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentReviewSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentSaveDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import com.codecoachai.resume.domain.entity.JobSearchExperimentReview;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.JobSearchExperimentDetailVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentRelationVO;
import com.codecoachai.resume.domain.vo.JobExperimentAgentContextVO;
import com.codecoachai.resume.domain.vo.JobSearchExperimentReviewVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentReviewMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.service.support.ExperimentQualityGatePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.sql.ResultSet;
import java.sql.Timestamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JobSearchExperimentServiceImplTest {

    private static final AtomicLong APPLICATION_ID_SEQUENCE = new AtomicLong(100L);

    @Mock
    private JobSearchExperimentMapper experimentMapper;
    @Mock
    private JobSearchExperimentRelationMapper relationMapper;
    @Mock
    private JobSearchExperimentReviewMapper reviewMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private ResumeJobMatchReportMapper matchReportMapper;
    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private JobApplicationEventMapper jobApplicationEventMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private UserAbilityProfileMapper userAbilityProfileMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private JobSearchExperimentServiceImpl service;

    @BeforeEach
    void setUp() {
        APPLICATION_ID_SEQUENCE.set(100L);
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("phase3-service-user")
                .build());
        service = new JobSearchExperimentServiceImpl(
                experimentMapper,
                relationMapper,
                reviewMapper,
                resumeVersionMapper,
                targetJobMapper,
                jobDescriptionAnalysisMapper,
                matchReportMapper,
                jobApplicationMapper,
                jobApplicationEventMapper,
                projectEvidenceMapper,
                userAbilityProfileMapper,
                jdbcTemplate,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    /**
     * V13 块D②漂移防护：Nacos 阈值改动后，策略文案与置信判断必须跟着配置走，
     * 不再回落到 15/3 字面量。16 条投递在默认阈值下是趋势观察（MEDIUM），
     * 在自定义 20/5 阈值下必须仍是弱观察（LOW）且文案显示 20。
     */
    @Test
    void generateReviewCopyFollowsConfiguredThresholds() {
        V12FeatureGate customGate = new V12FeatureGate();
        customGate.getExperimentSampleThresholds().setMinApplications(20);
        customGate.getExperimentSampleThresholds().setMinInterviews(5);
        JobSearchExperimentServiceImpl customService = new JobSearchExperimentServiceImpl(
                experimentMapper, relationMapper, reviewMapper, resumeVersionMapper,
                targetJobMapper, jobDescriptionAnalysisMapper, matchReportMapper,
                jobApplicationMapper, jobApplicationEventMapper, projectEvidenceMapper,
                userAbilityProfileMapper, jdbcTemplate, new ObjectMapper(),
                new ExperimentQualityGatePolicy(customGate));

        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        when(relationMapper.selectList(any())).thenReturn(applicationRelations(16));
        List<JobApplication> applications = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            applications.add(application("APPLIED"));
        }
        when(jobApplicationMapper.selectList(any())).thenReturn(applications);
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.<JobApplicationEvent>of());
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(100L);
            return 1;
        });

        JobSearchExperimentReviewVO review = customService.generateReview(7L);

        assertEquals("LOW", review.getConfidenceLevel());
        assertTrue(review.getNextAction().contains("补足到 20 条投递后"));
        assertTrue(review.getSampleWarning().contains("投递处于 5-19 条"));
    }

    @Test
    void generateReviewKeepsConclusionsWeakWhenApplicationSampleIsInsufficient() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(List.of(
                relation(101L),
                relation(102L),
                relation(103L)));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(
                application("SAVED"),
                application("APPLIED"),
                application("REJECTED")));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.<JobApplicationEvent>of());
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(99L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("LOW", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("投递少于 5"));
        assertTrue(review.getInsightSummary().contains("只展示事实"));
        assertTrue(review.getUnsupportedConclusion().contains("不能判断策略有效性"));
        assertTrue(review.getUnsupportedConclusion().contains("每个证据或简历版本使用少于 3 次"));
        assertTrue(review.getNextAction().contains("只展示事实"));
        assertTrue(review.getFactSummary().contains("投递数：3"));
        Map<?, ?> qualityGate = (Map<?, ?>) review.getStrategy().get("qualityGate");
        assertEquals("BLOCKED", qualityGate.get("gateStatus"));
        assertEquals(5, qualityGate.get("minSampleSize"));
        assertEquals(false, qualityGate.get("strongRecommendationAllowed"));
    }

    @Test
    void generateReviewKeepsLowConfidenceWhileSampleIsInsufficientEvenAfterFiveApplications() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(List.of(
                relation(101L), relation(102L), relation(103L), relation(104L), relation(105L)));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(
                application("APPLIED"),
                application("APPLIED"),
                application("INTERVIEWING"),
                application("REJECTED"),
                application("APPLIED")));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(100L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("LOW", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("5-14"));
        assertTrue(review.getInsightSummary().contains("弱观察"));
        assertTrue(review.getNextAction().contains("15 条"));
        assertFalse(review.getUnsupportedConclusion().isBlank());
        Map<?, ?> qualityGate = (Map<?, ?>) review.getStrategy().get("qualityGate");
        assertEquals("WARN", qualityGate.get("gateStatus"));
        assertEquals(15, qualityGate.get("minSampleSize"));
        assertEquals(false, qualityGate.get("strongRecommendationAllowed"));
    }

    @Test
    void generateReviewUsesMediumConfidenceForEnoughApplicationsButTooFewCompletedInterviews() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(applicationRelations(15));
        when(jobApplicationMapper.selectList(any())).thenReturn(applications(15, List.of(1L, 2L)));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(interviewCompletedEvents(2));
        stubCompletedInterviewSessions(2);
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(101L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("MEDIUM", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("面试样本不足"));
        assertTrue(review.getInsightSummary().contains("趋势观察"));
        assertTrue(review.getUnsupportedConclusion().contains("不能判断面试能力变化"));
        assertTrue(review.getNextAction().contains("面试复盘样本"));
        Map<?, ?> qualityGate = (Map<?, ?>) review.getStrategy().get("qualityGate");
        assertEquals("WARN", qualityGate.get("gateStatus"));
        assertEquals(15, qualityGate.get("minSampleSize"));
        assertEquals(false, qualityGate.get("strongRecommendationAllowed"));
    }

    @Test
    void generateReviewUsesHighConfidenceWhenApplicationsAndCompletedInterviewsAreEnough() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(
                resumeVersionRelationsWithApplications(15, 1L, 2L, 3L, 4L));
        when(jobApplicationMapper.selectList(any())).thenReturn(applications(15, List.of(1L, 2L, 3L, 4L)));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(interviewCompletedEvents(3));
        stubCompletedInterviewSessions(3);
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(102L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("HIGH", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("影响因素"));
        assertTrue(review.getUnsupportedConclusion().contains("不能把结果归因到单一因素"));
        assertTrue(review.getNextAction().contains("下一轮实验"));
        Map<?, ?> qualityGate = (Map<?, ?>) review.getStrategy().get("qualityGate");
        assertEquals("PASS", qualityGate.get("gateStatus"));
        assertEquals(15, qualityGate.get("minSampleSize"));
        assertEquals(true, qualityGate.get("strongRecommendationAllowed"));
    }

    @Test
    void generateReviewDoesNotCompareResumeVersionsWhenVersionSampleIsInsufficient() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(
                resumeVersionRelationsWithApplications(15, 1L));
        when(jobApplicationMapper.selectList(any())).thenReturn(applications(15, List.of(1L)));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(interviewCompletedEvents(3));
        stubCompletedInterviewSessions(3);
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(103L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("HIGH", review.getConfidenceLevel());
        assertTrue(review.getUnsupportedConclusion().contains("每个证据或简历版本使用少于 3 次"));
        assertTrue(review.getSampleWarning().contains("简历版本"));
    }

    @Test
    void metricsCountsOnlyApplicationsThatStillExistAndAreOwned() {
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        when(relationMapper.selectList(any())).thenReturn(applicationRelations(3));
        when(jobApplicationMapper.selectList(any()))
                .thenReturn(List.of(application("APPLIED")));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of());

        var metrics = service.metrics(7L);

        assertEquals(1, metrics.getApplicationCount());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void metricsDeduplicatesEventSessionAndReportForTheSameInterview() {
        List<JobSearchExperimentRelation> relations = applicationRelations(1);
        relations.add(relationWithType(701L, "INTERVIEW_SESSION"));
        relations.add(relationWithType(901L, "INTERVIEW_REPORT"));
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        when(relationMapper.selectList(any())).thenReturn(relations);
        when(jobApplicationMapper.selectList(any()))
                .thenReturn(List.of(application("INTERVIEWING")));
        when(jobApplicationEventMapper.selectList(any()))
                .thenReturn(interviewCompletedEvents(1));
        when(jdbcTemplate.query(
                contains("FROM interview_report r"),
                any(RowMapper.class),
                any(Object[].class))).thenReturn((List) List.of(701L));
        when(jdbcTemplate.query(
                contains("FROM interview_session"),
                any(RowMapper.class),
                any(Object[].class))).thenReturn((List) List.of(701L));

        var metrics = service.metrics(7L);

        assertEquals(1, metrics.getInterviewCompletedCount());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void metricsBindsInterviewEventSessionToTheSameApplication() {
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        when(relationMapper.selectList(any())).thenReturn(applicationRelations(1));
        when(jobApplicationMapper.selectList(any()))
                .thenReturn(List.of(application("INTERVIEWING")));
        when(jobApplicationEventMapper.selectList(any()))
                .thenReturn(interviewCompletedEvents(1));
        when(jdbcTemplate.query(
                contains("FROM interview_session"),
                any(RowMapper.class),
                any(Object[].class))).thenReturn((List) List.of(701L));

        service.metrics(7L);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                contains("application_id = ?"),
                any(RowMapper.class),
                argsCaptor.capture());
        assertArrayEquals(
                new Object[]{10L, 701L, 101L},
                argsCaptor.getValue());
    }

    @Test
    void createReviewDoesNotAllowClientToUpgradeLowSampleConfidence() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(List.of(
                relation(101L),
                relation(102L),
                relation(103L)));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(
                application("APPLIED"),
                application("APPLIED"),
                application("APPLIED")));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(104L);
            return 1;
        });
        JobSearchExperimentReviewSaveDTO dto = new JobSearchExperimentReviewSaveDTO();
        dto.setFactSummary("投递 3 条。");
        dto.setInsightSummary("客户端声称策略已经成功。");
        dto.setUnsupportedConclusion("");
        dto.setSampleWarning("");
        dto.setNextAction("继续按当前方向投递。");
        dto.setConfidenceLevel("HIGH");
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("confidenceLevel", "HIGH");
        strategy.put("sampleInsufficient", false);
        strategy.put("sampleWarning", "");
        strategy.put("unsupportedConclusions", List.of());
        dto.setStrategy(strategy);

        JobSearchExperimentReviewVO review = service.createReview(7L, dto);

        assertEquals("LOW", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("投递少于 5"));
        assertTrue(review.getUnsupportedConclusion().contains("不能判断策略有效性"));
        assertEquals("LOW", review.getStrategy().get("confidenceLevel"));
        assertEquals(true, review.getStrategy().get("sampleInsufficient"));
        assertTrue(((String) review.getStrategy().get("sampleWarning")).contains("投递少于 5"));
        assertTrue(((List<?>) review.getStrategy().get("unsupportedConclusions")).contains("不能判断策略有效性或渠道质量。"));
    }

    @Test
    void createReviewReplacesClientStrongManualReviewFieldsWhenSampleIsLow() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(List.of(
                relation(101L),
                relation(102L),
                relation(103L)));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(
                application("APPLIED"),
                application("APPLIED"),
                application("APPLIED")));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(105L);
            return 1;
        });
        ArgumentCaptor<JobSearchExperiment> experimentCaptor = ArgumentCaptor.forClass(JobSearchExperiment.class);
        when(experimentMapper.updateById(experimentCaptor.capture())).thenReturn(1);
        JobSearchExperimentReviewSaveDTO dto = new JobSearchExperimentReviewSaveDTO();
        dto.setFactSummary("client fact summary");
        dto.setInsightSummary("CLIENT_STRONG_INSIGHT_STRATEGY_SUCCESS");
        dto.setUnsupportedConclusion("CLIENT_UNSUPPORTED_FIELD_STRONG_CLAIM");
        dto.setSampleWarning("");
        dto.setNextAction("CLIENT_STRONG_NEXT_ACTION_SCALE_DELIVERY");
        dto.setConfidenceLevel("HIGH");
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("title", "CLIENT_STRONG_TITLE");
        strategy.put("content", "CLIENT_STRONG_STRATEGY_CONTENT");
        strategy.put("actionUrl", "/client/unsafe-action");
        strategy.put("evidenceSources", List.of(Map.of("sourceType", "CLIENT")));
        strategy.put("confidenceLevel", "HIGH");
        strategy.put("sampleInsufficient", false);
        strategy.put("sampleWarning", "");
        strategy.put("unsupportedConclusions", List.of());
        strategy.put("weakObservations", List.of("CLIENT_STRONG_WEAK_OBSERVATION"));
        dto.setStrategy(strategy);

        JobSearchExperimentReviewVO review = service.createReview(7L, dto);

        assertEquals("LOW", review.getConfidenceLevel());
        assertFalse(review.getUnsupportedConclusion().contains("CLIENT_UNSUPPORTED_FIELD_STRONG_CLAIM"));
        assertFalse(review.getInsightSummary().contains("CLIENT_STRONG_INSIGHT"));
        assertFalse(review.getNextAction().contains("CLIENT_STRONG_NEXT_ACTION"));
        assertFalse(String.valueOf(review.getStrategy().get("title")).contains("CLIENT_STRONG"));
        assertFalse(String.valueOf(review.getStrategy().get("content")).contains("CLIENT_STRONG"));
        assertFalse(String.valueOf(review.getStrategy().get("actionUrl")).contains("/client/unsafe-action"));
        assertFalse(String.valueOf(review.getStrategy().get("weakObservations")).contains("CLIENT_STRONG"));
        assertFalse(String.valueOf(review.getStrategy().get("evidenceSources")).contains("CLIENT"));
        assertFalse(experimentCaptor.getValue().getNextStrategy().contains("CLIENT_STRONG_NEXT_ACTION"));
    }

    @Test
    void createIgnoresClientSuppliedDemoFlag() {
        JobSearchExperimentSaveDTO dto = new JobSearchExperimentSaveDTO();
        dto.setTitle("Real experiment");
        dto.setDemoFlag(true);
        ArgumentCaptor<JobSearchExperiment> captor = ArgumentCaptor.forClass(JobSearchExperiment.class);
        when(experimentMapper.insert(captor.capture())).thenAnswer(invocation -> {
            JobSearchExperiment experiment = invocation.getArgument(0);
            experiment.setId(77L);
            return 1;
        });
        when(experimentMapper.selectOne(any())).thenAnswer(invocation -> captor.getValue());
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        service.create(dto);

        assertEquals(0, captor.getValue().getDemoFlag());
    }

    @Test
    void createPersistsTargetAndResumeVersionRelationsBeforeImmediateDetailReadback() {
        JobSearchExperimentSaveDTO dto = new JobSearchExperimentSaveDTO();
        dto.setTitle("Targeted resume experiment");
        dto.setTargetJobId(100L);
        dto.setResumeVersionId(200L);
        JobSearchExperiment storedExperiment = experiment();
        storedExperiment.setId(77L);
        List<JobSearchExperimentRelation> storedRelations = new java.util.ArrayList<>();

        when(experimentMapper.insert(any(JobSearchExperiment.class))).thenAnswer(invocation -> {
            JobSearchExperiment inserted = invocation.getArgument(0);
            inserted.setId(77L);
            storedExperiment.setTitle(inserted.getTitle());
            storedExperiment.setGoal(inserted.getGoal());
            storedExperiment.setTargetDirection(inserted.getTargetDirection());
            storedExperiment.setStartDate(inserted.getStartDate());
            storedExperiment.setEndDate(inserted.getEndDate());
            storedExperiment.setStatus(inserted.getStatus());
            storedExperiment.setDemoFlag(inserted.getDemoFlag());
            storedExperiment.setSampleCount(inserted.getSampleCount());
            storedExperiment.setConfidenceLevel(inserted.getConfidenceLevel());
            return 1;
        });
        when(experimentMapper.selectOne(any())).thenReturn(storedExperiment);
        TargetJob targetJob = new TargetJob();
        targetJob.setId(100L);
        targetJob.setUserId(10L);
        targetJob.setJobTitle("Java backend");
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob);
        ResumeVersion version = new ResumeVersion();
        version.setId(200L);
        version.setUserId(10L);
        version.setResumeId(300L);
        version.setVersionName("Delivery V1");
        when(resumeVersionMapper.selectOne(any())).thenReturn(version);
        when(relationMapper.selectOne(any())).thenReturn(null);
        when(relationMapper.insert(any(JobSearchExperimentRelation.class))).thenAnswer(invocation -> {
            JobSearchExperimentRelation relation = invocation.getArgument(0);
            relation.setId(800L + storedRelations.size());
            storedRelations.add(relation);
            return 1;
        });
        when(relationMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(storedRelations));
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        JobSearchExperimentDetailVO result = service.create(dto);

        assertEquals(77L, result.getId());
        assertEquals(List.of("TARGET_JOB", "RESUME_VERSION"),
                result.getRelations().stream()
                        .map(JobSearchExperimentRelationVO::getRelationType)
                        .toList());
        assertEquals(List.of(100L, 200L),
                result.getRelations().stream()
                        .map(JobSearchExperimentRelationVO::getRelationId)
                        .toList());
    }

    @Test
    void createResolvesMultipleBaseResumesToOwnedVersionEvidence() {
        JobSearchExperimentSaveDTO dto = new JobSearchExperimentSaveDTO();
        dto.setTitle("Multi evidence experiment");
        dto.setTargetJobIds(List.of(100L, 101L));
        dto.setResumeIds(List.of(300L, 301L));
        JobSearchExperiment storedExperiment = experiment();
        storedExperiment.setId(77L);
        List<JobSearchExperimentRelation> storedRelations = new ArrayList<>();

        when(experimentMapper.insert(any(JobSearchExperiment.class))).thenAnswer(invocation -> {
            JobSearchExperiment inserted = invocation.getArgument(0);
            inserted.setId(77L);
            storedExperiment.setTitle(inserted.getTitle());
            storedExperiment.setStatus(inserted.getStatus());
            storedExperiment.setDemoFlag(inserted.getDemoFlag());
            return 1;
        });
        when(experimentMapper.selectOne(any())).thenReturn(storedExperiment);
        when(targetJobMapper.selectOne(any())).thenAnswer(invocation -> {
            TargetJob target = new TargetJob();
            target.setId(storedRelations.stream()
                    .filter(item -> "TARGET_JOB".equals(item.getRelationType()))
                    .count() == 0 ? 100L : 101L);
            target.setUserId(10L);
            target.setCompanyName("Company");
            target.setJobTitle("Backend");
            return target;
        });
        ResumeVersion firstVersion = new ResumeVersion();
        firstVersion.setId(200L);
        firstVersion.setUserId(10L);
        firstVersion.setResumeId(300L);
        firstVersion.setVersionName("Resume 300 current");
        ResumeVersion secondVersion = new ResumeVersion();
        secondVersion.setId(201L);
        secondVersion.setUserId(10L);
        secondVersion.setResumeId(301L);
        secondVersion.setVersionName("Resume 301 current");
        when(resumeVersionMapper.selectCurrentForUpdate(10L, 300L)).thenReturn(firstVersion);
        when(resumeVersionMapper.selectCurrentForUpdate(10L, 301L)).thenReturn(secondVersion);
        when(resumeVersionMapper.selectOne(any())).thenReturn(firstVersion, secondVersion);
        when(relationMapper.selectOne(any())).thenReturn(null);
        when(relationMapper.insert(any(JobSearchExperimentRelation.class))).thenAnswer(invocation -> {
            JobSearchExperimentRelation relation = invocation.getArgument(0);
            relation.setId(800L + storedRelations.size());
            storedRelations.add(relation);
            return 1;
        });
        when(relationMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(storedRelations));
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        JobSearchExperimentDetailVO result = service.create(dto);

        assertEquals(4, result.getRelations().size());
        assertEquals(List.of(100L, 101L),
                result.getRelations().stream()
                        .filter(item -> "TARGET_JOB".equals(item.getRelationType()))
                        .map(JobSearchExperimentRelationVO::getRelationId)
                        .toList());
        assertEquals(List.of(200L, 201L),
                result.getRelations().stream()
                        .filter(item -> "RESUME_VERSION".equals(item.getRelationType()))
                        .map(JobSearchExperimentRelationVO::getRelationId)
                        .toList());
        verify(resumeVersionMapper).selectCurrentForUpdate(10L, 300L);
        verify(resumeVersionMapper).selectCurrentForUpdate(10L, 301L);
    }

    @Test
    void createRejectsSelectedResumeWithoutOwnedVersionBeforeRelationEvidenceIsComplete() {
        JobSearchExperimentSaveDTO dto = new JobSearchExperimentSaveDTO();
        dto.setTitle("Missing resume version");
        dto.setResumeIds(List.of(300L));
        when(experimentMapper.insert(any(JobSearchExperiment.class))).thenAnswer(invocation -> {
            JobSearchExperiment inserted = invocation.getArgument(0);
            inserted.setId(77L);
            return 1;
        });
        when(resumeVersionMapper.selectCurrentForUpdate(10L, 300L)).thenReturn(null);
        when(resumeVersionMapper.selectLatestForUpdate(10L, 300L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.create(dto));

        verify(relationMapper, never()).insert(any(JobSearchExperimentRelation.class));
    }

    @Test
    void createRejectsForeignTargetBeforeAnyRelationIsPersisted() {
        JobSearchExperimentSaveDTO dto = new JobSearchExperimentSaveDTO();
        dto.setTitle("Invalid experiment");
        dto.setTargetJobId(100L);
        when(experimentMapper.insert(any(JobSearchExperiment.class))).thenAnswer(invocation -> {
            JobSearchExperiment inserted = invocation.getArgument(0);
            inserted.setId(77L);
            return 1;
        });
        when(targetJobMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.create(dto));

        verify(relationMapper, never()).insert(any(JobSearchExperimentRelation.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listReviewsRefreshesV9EvidenceProjectionWithoutChangingStrategyJson() throws Exception {
        JobSearchExperiment experiment = experiment();
        JobSearchExperimentReview stored = new JobSearchExperimentReview();
        stored.setId(901L);
        stored.setUserId(10L);
        stored.setExperimentId(7L);
        stored.setFactSummary("已保存事实");
        stored.setStrategyJson("{\"facts\":[\"旧接口事实\"]}");
        stored.setConfidenceLevel("LOW");
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(reviewMapper.selectList(any())).thenReturn(List.of(stored));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    if (sql.contains("career_evidence_usage u")) {
                        ResultSet resultSet = mock(ResultSet.class);
                        when(resultSet.getLong("id")).thenReturn(501L);
                        when(resultSet.getString("source_hash")).thenReturn("usage-source");
                        when(resultSet.getString("content_hash")).thenReturn("usage-content");
                        when(resultSet.getString("asset_version")).thenReturn("2");
                        when(resultSet.getTimestamp("used_at")).thenReturn(
                                Timestamp.valueOf("2026-07-22 10:00:00"));
                        when(resultSet.getObject("result_id", Long.class)).thenReturn(601L);
                        when(resultSet.getString("result_status")).thenReturn("CONFIRMED");
                        return List.of(mapper.mapRow(resultSet, 0));
                    }
                    if (sql.contains("job_experiment_attribution a")) {
                        return List.of(701L);
                    }
                    return List.of();
                });

        JobSearchExperimentReviewVO review = service.listReviews(7L).get(0);

        assertEquals(1L, review.getEvidenceUsageCount());
        assertEquals(1L, review.getOutcomeSampleCount());
        assertEquals(64, review.getUsageSourceHash().length());
        assertEquals(701L, review.getAttributionSnapshotId());
        assertEquals(List.of("旧接口事实"), review.getFacts());
        assertFalse(review.getStrategy().containsKey("evidenceUsageCount"));
        assertFalse(review.getStrategy().containsKey("outcomeSampleCount"));
        assertFalse(review.getStrategy().containsKey("usageSourceHash"));
        assertFalse(review.getStrategy().containsKey("attributionSnapshotId"));
    }

    @Test
    void listAgentContextForUserReturnsSafeNonDemoExperimentSummaries() {
        JobSearchExperiment experiment = experiment();
        experiment.setTitle("Redis 方向投递实验");
        experiment.setTargetDirection("Java 后端 / Redis");
        experiment.setSampleCount(3);
        experiment.setConfidenceLevel("LOW");
        experiment.setSampleWarning("样本不足：投递少于 5 条。");
        experiment.setNextStrategy("继续积累可比较投递。");
        when(relationMapper.selectList(any())).thenReturn(List.of(targetJobRelation(100L)));
        when(experimentMapper.selectList(any())).thenReturn(List.of(experiment));

        List<JobExperimentAgentContextVO> contexts = service.listAgentContextForUser(10L, 100L);

        assertEquals(1, contexts.size());
        JobExperimentAgentContextVO context = contexts.get(0);
        assertEquals(7L, context.getId());
        assertEquals("Redis 方向投递实验", context.getTitle());
        assertEquals("Java 后端 / Redis", context.getTargetDirection());
        assertEquals("RUNNING", context.getStatus());
        assertEquals(3, context.getSampleCount());
        assertEquals("LOW", context.getConfidenceLevel());
        assertTrue(context.getSampleWarning().contains("样本不足"));
        assertEquals("继续积累可比较投递。", context.getNextStrategy());
    }

    @Test
    void listAgentContextForUserSkipsExperimentsWithoutMatchingTargetRelation() {
        lenient().when(experimentMapper.selectList(any())).thenReturn(List.of(experiment()));
        when(relationMapper.selectList(any())).thenReturn(List.of());

        List<JobExperimentAgentContextVO> contexts = service.listAgentContextForUser(10L, 100L);

        assertTrue(contexts.isEmpty());
    }

    @Test
    void addRelationAcceptsOwnedJdAnalysisRelation() {
        JobSearchExperiment experiment = experiment();
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(55L);
        analysis.setUserId(10L);
        analysis.setTargetJobId(100L);
        analysis.setSummary("Redis 高并发 JD 分析");
        analysis.setJobTitle("Java 后端");
        analysis.setCompanyName("CodeCoachAI");
        JobSearchExperimentRelationSaveDTO dto = new JobSearchExperimentRelationSaveDTO();
        dto.setRelationType("JD_ANALYSIS");
        dto.setRelationId(55L);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis);
        when(relationMapper.selectOne(any())).thenReturn(null);
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.insert(any(JobSearchExperimentRelation.class))).thenAnswer(invocation -> {
            JobSearchExperimentRelation relation = invocation.getArgument(0);
            relation.setId(88L);
            return 1;
        });

        JobSearchExperimentRelationVO relation = service.addRelation(7L, dto);

        assertEquals("JD_ANALYSIS", relation.getRelationType());
        assertEquals(55L, relation.getRelationId());
        assertTrue(relation.getRelationSummary().contains("Redis 高并发"));
    }

    @Test
    void addRelationReturnsConcurrentDuplicateWinner() {
        JobSearchExperiment experiment = experiment();
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(55L);
        analysis.setUserId(10L);
        analysis.setSummary("JD");
        JobSearchExperimentRelation winner = relationWithType(55L, "JD_ANALYSIS");
        winner.setId(88L);
        winner.setExperimentId(7L);
        JobSearchExperimentRelationSaveDTO dto = new JobSearchExperimentRelationSaveDTO();
        dto.setRelationType("JD_ANALYSIS");
        dto.setRelationId(55L);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis);
        when(relationMapper.selectOne(any())).thenReturn(null, winner);
        when(relationMapper.insert(any(JobSearchExperimentRelation.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(relationMapper.selectList(any())).thenReturn(List.of(winner));

        JobSearchExperimentRelationVO result = service.addRelation(7L, dto);

        assertEquals(88L, result.getId());
        assertEquals(55L, result.getRelationId());
    }

    @Test
    void addRelationRejectsRemoteTypesThatCannotBeOwnedLocally() {
        JobSearchExperimentRelationSaveDTO dto = new JobSearchExperimentRelationSaveDTO();
        dto.setRelationType("INTERVIEW_REPORT");
        dto.setRelationId(66L);
        when(experimentMapper.selectOne(any())).thenReturn(experiment());

        assertThrows(BusinessException.class, () -> service.addRelation(7L, dto));
    }

    @Test
    void addRelationRejectsDemoExperimentMutationToKeepDemoDataIsolated() {
        JobSearchExperiment experiment = experiment();
        experiment.setDemoFlag(1);
        JobSearchExperimentRelationSaveDTO dto = new JobSearchExperimentRelationSaveDTO();
        dto.setRelationType("JOB_APPLICATION");
        dto.setRelationId(101L);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);

        assertThrows(BusinessException.class, () -> service.addRelation(7L, dto));
    }

    private static JobSearchExperiment experiment() {
        JobSearchExperiment experiment = new JobSearchExperiment();
        experiment.setId(7L);
        experiment.setUserId(10L);
        experiment.setTitle("Redis experiment");
        experiment.setStatus("RUNNING");
        experiment.setDemoFlag(0);
        return experiment;
    }

    private static JobSearchExperimentRelation relation(Long appId) {
        JobSearchExperimentRelation relation = new JobSearchExperimentRelation();
        relation.setId(appId);
        relation.setUserId(10L);
        relation.setExperimentId(7L);
        relation.setRelationType("JOB_APPLICATION");
        relation.setRelationId(appId);
        relation.setDemoFlag(0);
        return relation;
    }

    private static List<JobSearchExperimentRelation> applicationRelations(int count) {
        List<JobSearchExperimentRelation> relations = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            relations.add(relation(100L + i));
        }
        return relations;
    }

    private static List<JobSearchExperimentRelation> resumeVersionRelations(Long... resumeVersionIds) {
        return resumeVersionRelationsWithApplications(10, resumeVersionIds);
    }

    private static List<JobSearchExperimentRelation> resumeVersionRelationsWithApplications(
            int applicationCount, Long... resumeVersionIds) {
        List<JobSearchExperimentRelation> relations = applicationRelations(applicationCount);
        long relationId = 1_000L;
        for (Long resumeVersionId : resumeVersionIds) {
            JobSearchExperimentRelation relation = relation(relationId++);
            relation.setRelationType("RESUME_VERSION");
            relation.setRelationId(resumeVersionId);
            relations.add(relation);
        }
        return relations;
    }

    private static JobSearchExperimentRelation targetJobRelation(Long targetJobId) {
        JobSearchExperimentRelation relation = relation(targetJobId);
        relation.setRelationType("TARGET_JOB");
        relation.setRelationId(targetJobId);
        return relation;
    }

    private static JobSearchExperimentRelation relationWithType(
            Long relationId, String relationType) {
        JobSearchExperimentRelation relation = relation(relationId);
        relation.setRelationType(relationType);
        relation.setRelationId(relationId);
        return relation;
    }

    private static JobApplication application(String status) {
        JobApplication application = new JobApplication();
        application.setId(APPLICATION_ID_SEQUENCE.incrementAndGet());
        application.setUserId(10L);
        application.setStatus(status);
        return application;
    }

    private static List<JobApplication> applications(int count, List<Long> resumeVersionIds) {
        List<JobApplication> applications = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JobApplication application = application("APPLIED");
            if (!resumeVersionIds.isEmpty()) {
                application.setResumeVersionId(resumeVersionIds.get(i % resumeVersionIds.size()));
            }
            applications.add(application);
        }
        return applications;
    }

    private static List<JobApplicationEvent> interviewCompletedEvents(int count) {
        List<JobApplicationEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JobApplicationEvent event = new JobApplicationEvent();
            event.setUserId(10L);
            event.setApplicationId(101L + i);
            event.setEventType("INTERVIEW_COMPLETED");
            event.setReviewJson("{\"interviewId\":" + (701L + i) + "}");
            events.add(event);
        }
        return events;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubCompletedInterviewSessions(int count) {
        List<Long> ids = new ArrayList<>();
        for (long index = 0; index < count; index++) {
            ids.add(701L + index);
        }
        when(jdbcTemplate.query(
                contains("FROM interview_session"),
                any(RowMapper.class),
                any(Object[].class))).thenReturn((List) ids);
    }

    @SuppressWarnings("unchecked")
    private static List<String> weakObservations(JobSearchExperimentReviewVO review) {
        Object observations = review.getStrategy().get("weakObservations");
        return observations instanceof List<?> list ? (List<String>) list : List.of();
    }
}
