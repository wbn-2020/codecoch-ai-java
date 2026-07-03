package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.JobSearchExperimentRelationSaveDTO;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

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
        assertTrue(review.getSampleWarning().contains("投递少于 5 条"));
        assertTrue(review.getUnsupportedConclusion().contains("暂时不要判断"));
        assertTrue(review.getNextAction().contains("至少积累 5 条可比较投递"));
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
}
