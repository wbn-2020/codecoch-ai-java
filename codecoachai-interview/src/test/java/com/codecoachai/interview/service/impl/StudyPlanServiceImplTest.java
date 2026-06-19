package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.StudyPlan;
import com.codecoachai.interview.domain.entity.StudyPlanSkillRelation;
import com.codecoachai.interview.domain.vo.StudyPlanAgentEvidenceVO;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.StudyPlanMapper;
import com.codecoachai.interview.mapper.StudyPlanSkillRelationMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.interview.mq.StudyPlanMqDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
class StudyPlanServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long PLAN_ID = 8001L;
    private static final long TARGET_JOB_ID = 501L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 18, 10, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 6, 18, 10, 20);

    @Mock
    private StudyPlanMapper studyPlanMapper;
    @Mock
    private StudyTaskMapper studyTaskMapper;
    @Mock
    private StudyPlanSkillRelationMapper relationMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private ResumeFeignClient resumeFeignClient;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private StudyPlanMqDispatcher studyPlanMqDispatcher;

    private StudyPlanServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(StudyPlan.class);
        initTableInfo(StudyPlanSkillRelation.class);
        initTableInfo(InterviewReport.class);
        initTableInfo(InterviewSession.class);
        initTableInfo(InterviewMessage.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @BeforeEach
    void setUp() {
        service = new StudyPlanServiceImpl(
                studyPlanMapper,
                studyTaskMapper,
                relationMapper,
                reportMapper,
                sessionMapper,
                messageMapper,
                resumeFeignClient,
                aiFeignClient,
                new ObjectMapper(),
                transactionTemplate,
                Optional.of(studyPlanMqDispatcher));
    }

    @Test
    void getPlanEvidenceReturnsOwnedActivePlanAndFiltersQuery() {
        when(studyPlanMapper.selectOne(any())).thenReturn(activePlan());

        StudyPlanAgentEvidenceVO evidence = service.getPlanEvidence(USER_ID, PLAN_ID);

        assertEquals(PLAN_ID, evidence.getId());
        assertEquals(USER_ID, evidence.getUserId());
        assertEquals(TARGET_JOB_ID, evidence.getTargetJobId());
        assertEquals("RESUME_JOB_MATCH", evidence.getSourceType());
        assertEquals("ACTIVE", evidence.getPlanStatus());
        assertEquals(UPDATED_AT, evidence.getGeneratedAt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<StudyPlan>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(studyPlanMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("plan_status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    @Test
    void getPlanEvidenceRejectsMissingOrInactivePlan() {
        when(studyPlanMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getPlanEvidence(USER_ID, PLAN_ID));
    }

    private StudyPlan activePlan() {
        StudyPlan plan = new StudyPlan();
        plan.setId(PLAN_ID);
        plan.setUserId(USER_ID);
        plan.setSourceType("RESUME_JOB_MATCH");
        plan.setSourceId(9001L);
        plan.setTargetJobId(TARGET_JOB_ID);
        plan.setSkillProfileId(6001L);
        plan.setMatchReportId(9001L);
        plan.setReportId(3001L);
        plan.setPlanStatus("ACTIVE");
        plan.setStartDate(LocalDate.of(2026, 6, 18));
        plan.setCreatedAt(CREATED_AT);
        plan.setUpdatedAt(UPDATED_AT);
        return plan;
    }
}
