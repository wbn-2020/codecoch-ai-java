package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.context.LoginUser;
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
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioDemoServiceImplTest {

    @Mock
    private PortfolioDemoDatasetMapper datasetMapper;
    @Mock
    private JobSearchExperimentMapper experimentMapper;
    @Mock
    private JobSearchExperimentRelationMapper relationMapper;
    @Mock
    private JobSearchExperimentReviewMapper reviewMapper;

    private PortfolioDemoServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(JobSearchExperiment.class);
        initTableInfo(JobSearchExperimentRelation.class);
        initTableInfo(JobSearchExperimentReview.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(10L)
                .username("portfolio-demo-user")
                .build());
        service = new PortfolioDemoServiceImpl(datasetMapper, experimentMapper, relationMapper, reviewMapper);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void statusBeforeLoadIsNotMarkedAsLoadedDemoData() {
        when(datasetMapper.selectOne(any())).thenReturn(null);

        PortfolioDemoStatusVO status = service.status();

        assertEquals("EMPTY", status.getStatus());
        assertFalse(status.getLoaded());
        assertFalse(status.getDemoData());
        assertTrue(status.getReadOnly());
    }

    @Test
    void resetDeletesOnlyDemoMarkedExperimentDataAndKeepsDatasetAsReset() {
        PortfolioDemoDataset dataset = demoDataset();
        when(datasetMapper.selectOne(any())).thenReturn(dataset);

        PortfolioDemoStatusVO status = service.reset();

        assertEquals("RESET", status.getStatus());
        assertTrue(status.getDemoData());
        assertTrue(status.getReadOnly());
        assertDemoFlagFilter(experimentMapper, relationMapper, reviewMapper);
        verify(datasetMapper).updateById(dataset);
    }

    @Test
    void loadReusesExistingDatasetAndDemoExperimentWithoutDuplicatingSeedData() {
        PortfolioDemoDataset dataset = demoDataset();
        JobSearchExperiment experiment = new JobSearchExperiment();
        experiment.setId(9L);
        experiment.setUserId(10L);
        experiment.setDemoFlag(1);
        when(datasetMapper.selectOne(any())).thenReturn(dataset);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);

        PortfolioDemoStatusVO status = service.load();

        assertEquals("LOADED", status.getStatus());
        assertTrue(status.getLoaded());
        verify(datasetMapper).updateById(dataset);
        verify(experimentMapper, never()).insert(any(JobSearchExperiment.class));
        verify(reviewMapper, never()).insert(any(JobSearchExperimentReview.class));
    }

    @Test
    void storylineReturnsCompletePhaseOneRoutesWithDemoEvidenceAndReadableChineseCopy() {
        when(datasetMapper.selectOne(any())).thenReturn(demoDataset());

        JobSearchExperiment experiment = new JobSearchExperiment();
        experiment.setId(9L);
        experiment.setUserId(10L);
        experiment.setDemoFlag(1);
        when(experimentMapper.selectOne(any())).thenReturn(experiment);

        var storyline = service.storyline();

        assertEquals(List.of(
                "target-job",
                "jd-match",
                "project-evidence",
                "interview-training",
                "interview-report",
                "ability-map",
                "job-experiment-review",
                "agent-today"
        ), storyline.getSteps().stream().map(PortfolioDemoStorylineVO.Step::getKey).toList());
        assertEquals(List.of(
                "agent-runs",
                "prompt-template",
                "prompt-regression",
                "ai-call-logs",
                "async-tasks",
                "metrics-dictionary",
                "ai-ops-dashboard"
        ), storyline.getOpsSteps().stream().map(PortfolioDemoStorylineVO.Step::getKey).toList());

        storyline.getSteps().forEach(PortfolioDemoServiceImplTest::assertStepHasReadableDemoRoute);
        storyline.getOpsSteps().forEach(PortfolioDemoServiceImplTest::assertStepHasReadableDemoRoute);
        assertEquals(List.of("job-experiment-review"), storyline.getSteps().stream()
                .filter(PortfolioDemoStorylineVO.Step::getDemoData)
                .map(PortfolioDemoStorylineVO.Step::getKey)
                .toList());
        assertTrue(storyline.getOpsSteps().stream().noneMatch(PortfolioDemoStorylineVO.Step::getDemoData));
        assertEquals("/job-experiments/9/review?demoFlag=true", storyline.getSteps().get(6).getRoute());
        assertEquals("/admin/ai/prompt-regression?demoFlag=true", storyline.getOpsSteps().get(2).getRoute());
    }

    private static PortfolioDemoDataset demoDataset() {
        PortfolioDemoDataset dataset = new PortfolioDemoDataset();
        dataset.setId(5L);
        dataset.setUserId(10L);
        dataset.setDatasetKey("portfolio-3b-v1");
        dataset.setDatasetName("CodeCoachAI 作品集演示");
        dataset.setVersion("v1");
        dataset.setStatus("LOADED");
        dataset.setDemoFlag(1);
        return dataset;
    }

    private static void assertDemoFlagFilter(JobSearchExperimentMapper experimentMapper,
                                             JobSearchExperimentRelationMapper relationMapper,
                                             JobSearchExperimentReviewMapper reviewMapper) {
        ArgumentCaptor<LambdaQueryWrapper<JobSearchExperiment>> experimentCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<JobSearchExperimentRelation>> relationCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<JobSearchExperimentReview>> reviewCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(experimentMapper).delete(experimentCaptor.capture());
        verify(relationMapper).delete(relationCaptor.capture());
        verify(reviewMapper).delete(reviewCaptor.capture());
        assertUserAndDemoFlagParams(experimentCaptor.getValue());
        assertUserAndDemoFlagParams(relationCaptor.getValue());
        assertUserAndDemoFlagParams(reviewCaptor.getValue());
    }

    private static void assertUserAndDemoFlagParams(LambdaQueryWrapper<?> wrapper) {
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("user_id"), sqlSegment);
        assertTrue(sqlSegment.contains("demo_flag"), sqlSegment);
    }

    private static void assertStepHasReadableDemoRoute(PortfolioDemoStorylineVO.Step step) {
        assertFalse(step.getTitle().isBlank(), step.getKey());
        assertFalse(step.getEvidenceSummary().isBlank(), step.getKey());
        assertFalse(containsMojibake(step.getTitle()), step.getTitle());
        assertFalse(containsMojibake(step.getEvidenceSummary()), step.getEvidenceSummary());
        assertTrue(step.getRoute().startsWith("/"), step.getKey());
        assertTrue(step.getRoute().contains("demoFlag=true"), step.getKey() + " route=" + step.getRoute());
    }

    private static boolean containsMojibake(String value) {
        return value.contains("�") || value.contains("鐩") || value.contains("婕") || value.contains("鍚") || value.contains("绀"); // mojibake-check-ignore-line: intentional detector
    }
}
