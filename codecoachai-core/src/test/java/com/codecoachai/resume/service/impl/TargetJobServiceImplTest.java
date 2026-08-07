package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobDescriptionParseDTO;
import com.codecoachai.resume.domain.dto.TargetJobQueryDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TargetJobServiceImplTest {

    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper analysisMapper;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private DistributedLockHelper distributedLockHelper;

    private TargetJobServiceImpl targetJobService;

    @BeforeEach
    void setUp() {
        initTableInfo(TargetJob.class);
        initTableInfo(JobDescriptionAnalysis.class);
        targetJobService = new TargetJobServiceImpl(
                targetJobMapper,
                analysisMapper,
                aiFeignClient,
                new ObjectMapper(),
                new TransactionTemplate(new NoopTransactionManager()),
                distributedLockHelper,
                Optional.empty());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        LoginUserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void listTargetJobsBatchLoadsLatestAnalysis() {
        when(targetJobMapper.selectList(any())).thenReturn(List.of(targetJob(11L), targetJob(12L)));
        JobDescriptionAnalysis stale = analysis(101L, 11L, "stale");
        JobDescriptionAnalysis latest = analysis(102L, 11L, "latest");
        JobDescriptionAnalysis second = analysis(201L, 12L, "second");
        when(analysisMapper.selectList(any())).thenReturn(List.of(stale, latest, second));

        var result = targetJobService.listTargetJobs(new TargetJobQueryDTO());

        assertEquals(2, result.size());
        assertEquals("latest", result.get(0).getAnalysisSummary());
        assertEquals("second", result.get(1).getAnalysisSummary());
        verify(analysisMapper).selectList(any());
        verify(analysisMapper, never()).selectOne(any());
    }

    @Test
    void createTargetJobRunsInsideUserCurrentLock() {
        when(distributedLockHelper.tryLockAndCall(
                eq("lock:target-job:current:1001"),
                eq(3L),
                eq(10L),
                any(),
                any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(3);
            return supplier.get();
        });
        when(targetJobMapper.selectCount(any())).thenReturn(0L);

        var dto = new com.codecoachai.resume.domain.dto.TargetJobSaveDTO();
        dto.setJobTitle("Java Engineer");
        dto.setCompanyName("CodeCoachAI");
        dto.setJdText("Build production Java services");

        var result = targetJobService.createTargetJob(dto);

        assertEquals(CommonConstants.YES, result.getCurrentFlag());
        verify(distributedLockHelper).tryLockAndCall(
                eq("lock:target-job:current:1001"),
                eq(3L),
                eq(10L),
                any(),
                any());
    }

    @Test
    void parseJobDescriptionDoesNotStartAiWhenParsingClaimLosesRace() {
        TargetJob job = targetJob(11L);
        job.setJdText("Build production Java services");
        job.setParseStatus(JobDescriptionParseStatus.NOT_PARSED.getCode());
        when(targetJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(targetJobMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> targetJobService.parseJobDescriptionForUser(11L, 1001L, new JobDescriptionParseDTO()));

        verify(analysisMapper, never()).insert(any(JobDescriptionAnalysis.class));
        verify(aiFeignClient, never()).parseJobDescription(any());
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    private static TargetJob targetJob(Long id) {
        TargetJob job = new TargetJob();
        job.setId(id);
        job.setUserId(1001L);
        job.setJobTitle("Java Engineer " + id);
        job.setCompanyName("CodeCoachAI");
        job.setStatus(CommonConstants.YES);
        job.setDeleted(CommonConstants.NO);
        return job;
    }

    private static JobDescriptionAnalysis analysis(Long id, Long targetJobId, String summary) {
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(id);
        analysis.setTargetJobId(targetJobId);
        analysis.setUserId(1001L);
        analysis.setSummary(summary);
        analysis.setDeleted(CommonConstants.NO);
        return analysis;
    }

    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
