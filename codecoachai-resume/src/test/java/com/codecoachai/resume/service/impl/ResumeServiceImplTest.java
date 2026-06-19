package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeOptimizeRecord;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.ResumeOptimizeStatus;
import com.codecoachai.resume.domain.vo.ResumeOptimizeRecordAgentEvidenceVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeOptimizeRecordMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeMqDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long RESUME_ID = 100L;
    private static final long TARGET_JOB_ID = 501L;
    private static final long OPTIMIZE_RECORD_ID = 7001L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 18, 9, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 6, 18, 9, 10);

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper projectMapper;
    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private ResumeOptimizeRecordMapper optimizeRecordMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private FileFeignClient fileFeignClient;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ResumeMqDispatcher resumeMqDispatcher;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;

    private ResumeServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(Resume.class);
        initTableInfo(ResumeProject.class);
        initTableInfo(ResumeAnalysisRecord.class);
        initTableInfo(ResumeOptimizeRecord.class);
        initTableInfo(TargetJob.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @BeforeEach
    void setUp() {
        service = new ResumeServiceImpl(
                resumeMapper,
                projectMapper,
                analysisRecordMapper,
                optimizeRecordMapper,
                targetJobMapper,
                fileFeignClient,
                aiFeignClient,
                new ObjectMapper(),
                transactionTemplate,
                Optional.of(resumeMqDispatcher),
                agentBusinessActionNotifier);
    }

    @Test
    void getOptimizeRecordEvidenceReturnsOwnedSuccessfulTargetJobScopedRecord() {
        when(optimizeRecordMapper.selectOne(any())).thenReturn(successRecord());

        ResumeOptimizeRecordAgentEvidenceVO evidence =
                service.getOptimizeRecordEvidence(USER_ID, OPTIMIZE_RECORD_ID);

        assertEquals(OPTIMIZE_RECORD_ID, evidence.getId());
        assertEquals(USER_ID, evidence.getUserId());
        assertEquals(RESUME_ID, evidence.getResumeId());
        assertEquals(TARGET_JOB_ID, evidence.getTargetJobId());
        assertEquals(ResumeOptimizeStatus.SUCCESS.getCode(), evidence.getStatus());
        assertEquals(UPDATED_AT, evidence.getOptimizedAt());
        assertEquals(CREATED_AT, evidence.getCreatedAt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeOptimizeRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verifySelectWrapper(wrapperCaptor);
    }

    @Test
    void getOptimizeRecordEvidenceRejectsMissingOrUnsuccessfulRecord() {
        when(optimizeRecordMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.getOptimizeRecordEvidence(USER_ID, OPTIMIZE_RECORD_ID));
    }

    private void verifySelectWrapper(ArgumentCaptor<Wrapper<ResumeOptimizeRecord>> wrapperCaptor) {
        org.mockito.Mockito.verify(optimizeRecordMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("optimize_status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    private ResumeOptimizeRecord successRecord() {
        ResumeOptimizeRecord record = new ResumeOptimizeRecord();
        record.setId(OPTIMIZE_RECORD_ID);
        record.setUserId(USER_ID);
        record.setResumeId(RESUME_ID);
        record.setTargetJobId(TARGET_JOB_ID);
        record.setOptimizeStatus(ResumeOptimizeStatus.SUCCESS.getCode());
        record.setCreatedAt(CREATED_AT);
        record.setUpdatedAt(UPDATED_AT);
        return record;
    }
}
