package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.JobSearchExperimentRelationSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentReviewSaveDTO;
import com.codecoachai.resume.domain.dto.JobSearchExperimentSaveDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import com.codecoachai.resume.domain.entity.JobSearchExperimentReview;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JobSearchExperimentServiceImplTest {

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
        assertTrue(review.getUnsupportedConclusion().contains("不比较简历版本优劣"));
        assertTrue(review.getNextAction().contains("只展示事实"));
        assertTrue(review.getFactSummary().contains("投递数：3"));
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
        assertTrue(review.getSampleWarning().contains("5-9"));
        assertTrue(review.getInsightSummary().contains("弱建议"));
        assertTrue(review.getNextAction().contains("低置信度"));
        assertFalse(review.getUnsupportedConclusion().isBlank());
    }

    @Test
    void generateReviewUsesMediumConfidenceForEnoughApplicationsButTooFewCompletedInterviews() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(applicationRelations(10));
        when(jobApplicationMapper.selectList(any())).thenReturn(applications(10, List.of(1L, 2L)));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(interviewCompletedEvents(2));
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(101L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("MEDIUM", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("面试样本不足"));
        assertTrue(review.getInsightSummary().contains("趋势观察"));
        assertTrue(review.getUnsupportedConclusion().contains("不判断面试能力变化"));
        assertTrue(review.getNextAction().contains("面试复盘样本"));
    }

    @Test
    void generateReviewUsesHighConfidenceWhenApplicationsAndCompletedInterviewsAreEnough() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(resumeVersionRelations(1L, 2L, 3L, 4L));
        when(jobApplicationMapper.selectList(any())).thenReturn(applications(12, List.of(1L, 2L, 3L, 4L)));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(interviewCompletedEvents(3));
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(102L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("HIGH", review.getConfidenceLevel());
        assertTrue(review.getSampleWarning().contains("影响因素"));
        assertTrue(review.getUnsupportedConclusion().contains("不能完全归因"));
        assertTrue(review.getNextAction().contains("下一轮实验"));
    }

    @Test
    void generateReviewDoesNotCompareResumeVersionsWhenVersionSampleIsInsufficient() {
        JobSearchExperiment experiment = experiment();
        when(experimentMapper.selectOne(any())).thenReturn(experiment);
        when(relationMapper.selectList(any())).thenReturn(resumeVersionRelations(1L));
        when(jobApplicationMapper.selectList(any())).thenReturn(applications(10, List.of(1L)));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(interviewCompletedEvents(3));
        when(reviewMapper.insert(any(JobSearchExperimentReview.class))).thenAnswer(invocation -> {
            JobSearchExperimentReview review = invocation.getArgument(0);
            review.setId(103L);
            return 1;
        });

        JobSearchExperimentReviewVO review = service.generateReview(7L);

        assertEquals("HIGH", review.getConfidenceLevel());
        assertTrue(review.getUnsupportedConclusion().contains("不比较简历版本优劣"));
        assertTrue(review.getSampleWarning().contains("简历版本"));
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
        List<JobSearchExperimentRelation> relations = applicationRelations(10);
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

    private static JobApplication application(String status) {
        JobApplication application = new JobApplication();
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
            events.add(event);
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private static List<String> weakObservations(JobSearchExperimentReviewVO review) {
        Object observations = review.getStrategy().get("weakObservations");
        return observations instanceof List<?> list ? (List<String>) list : List.of();
    }
}
