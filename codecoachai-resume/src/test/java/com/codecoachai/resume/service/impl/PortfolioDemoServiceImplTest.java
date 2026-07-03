package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentReviewMapper;
import com.codecoachai.resume.mapper.PortfolioDemoDatasetMapper;
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
    void resetDeletesOnlyDemoMarkedExperimentDataAndKeepsDatasetAsReset() {
        PortfolioDemoDataset dataset = new PortfolioDemoDataset();
        dataset.setId(5L);
        dataset.setUserId(10L);
        dataset.setDatasetKey("portfolio-3b-v1");
        dataset.setDatasetName("CodeCoachAI 作品集演示");
        dataset.setVersion("v1");
        dataset.setStatus("LOADED");
        dataset.setDemoFlag(1);
        when(datasetMapper.selectOne(any())).thenReturn(dataset);

        PortfolioDemoStatusVO status = service.reset();

        assertEquals("RESET", status.getStatus());
        assertTrue(status.getDemoData());
        assertTrue(status.getReadOnly());
        assertDemoFlagFilter(experimentMapper, relationMapper, reviewMapper);
        verify(datasetMapper).updateById(dataset);
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
}
