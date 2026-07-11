package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateDTO;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.StudyPlan;
import com.codecoachai.interview.domain.entity.StudyPlanSkillRelation;
import com.codecoachai.interview.domain.entity.StudyTask;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.StudyPlanAgentEvidenceVO;
import com.codecoachai.interview.domain.vo.StudyPlanDetailVO;
import com.codecoachai.interview.domain.vo.StudyPlanGenerateVO;
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
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
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
        initTableInfo(StudyTask.class);
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

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
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

    @Test
    void detailFiltersTaskReadsByCurrentUser() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        when(studyPlanMapper.selectOne(any())).thenReturn(activePlan());
        when(studyTaskMapper.selectList(any())).thenReturn(List.of(activeTask()));

        StudyPlanDetailVO detail = service.detail(PLAN_ID);

        assertEquals(1, detail.getTasks().size());
        assertEquals(1, detail.getTotalTaskCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<StudyTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(studyTaskMapper, org.mockito.Mockito.times(2)).selectList(wrapperCaptor.capture());
        wrapperCaptor.getAllValues().forEach(wrapper -> {
            String sqlSegment = wrapper.getSqlSegment();
            assertTrue(sqlSegment.contains("plan_id"));
            assertTrue(sqlSegment.contains("user_id"));
            assertTrue(sqlSegment.contains("deleted"));
        });
    }

    @Test
    void generateExistingPlanCountsOnlyCurrentUserOwnedTasksAndRelations() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        when(reportMapper.selectOne(any())).thenReturn(generatedReport());
        when(studyPlanMapper.selectOne(any())).thenReturn(activePlan());
        when(studyTaskMapper.selectList(any())).thenReturn(List.of(activeTask()));
        when(relationMapper.selectCount(any())).thenReturn(1L);

        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(3001L);

        StudyPlanGenerateVO result = service.generate(dto);

        assertEquals(PLAN_ID, result.getPlanId());
        assertEquals(1, result.getTaskCount());
        assertEquals(1, result.getSkillGapCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<StudyTask>> taskWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(studyTaskMapper).selectList(taskWrapperCaptor.capture());
        String taskSqlSegment = taskWrapperCaptor.getValue().getSqlSegment();
        assertTrue(taskSqlSegment.contains("plan_id"));
        assertTrue(taskSqlSegment.contains("user_id"));
        assertTrue(taskSqlSegment.contains("deleted"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<StudyPlanSkillRelation>> relationWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(relationMapper).selectCount(relationWrapperCaptor.capture());
        String relationSqlSegment = relationWrapperCaptor.getValue().getSqlSegment();
        assertTrue(relationSqlSegment.contains("study_plan_id"));
        assertTrue(relationSqlSegment.contains("user_id"));
        assertTrue(relationSqlSegment.contains("deleted"));
    }

    @Test
    void generateRejectsFallbackOrSampleInsufficientReport() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        InterviewReport report = generatedReport();
        report.setRubricScores("[{\"sampleInsufficient\":true}]");
        when(reportMapper.selectOne(any())).thenReturn(report);

        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(report.getId());

        assertThrows(BusinessException.class, () -> service.generate(dto));
    }

    @Test
    void generateRejectsReportWithUntrustedEvidenceSource() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        InterviewReport report = generatedReport();
        report.setAdviceEvidence("""
                [{"title":"unsafe","sampleInsufficient":false,
                  "evidenceSources":[{"sourceType":"CLIENT","sourceId":1,"sourceSummary":"client payload"}]}]
                """);
        when(reportMapper.selectOne(any())).thenReturn(report);

        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(report.getId());

        assertThrows(BusinessException.class, () -> service.generate(dto));
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

    private StudyTask activeTask() {
        StudyTask task = new StudyTask();
        task.setId(9001L);
        task.setPlanId(PLAN_ID);
        task.setUserId(USER_ID);
        task.setStageNo(1);
        task.setTaskOrder(1);
        task.setTaskStatus("TODO");
        task.setTaskTitle("Owner scoped task");
        return task;
    }

    private InterviewReport generatedReport() {
        InterviewReport report = new InterviewReport();
        report.setId(3001L);
        report.setUserId(USER_ID);
        report.setSessionId(4001L);
        report.setStatus(ReportStatusEnum.GENERATED.name());
        report.setTotalScore(80);
        report.setSummary("Trusted interview report");
        report.setReportContent("Structured trusted report content");
        report.setGeneratedAt(UPDATED_AT);
        return report;
    }
}
