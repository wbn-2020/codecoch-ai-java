package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.interview.domain.dto.StudyPlanGenerateFromGapDTO;
import com.codecoachai.interview.domain.dto.StudyPlanQueryDTO;
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
import com.codecoachai.interview.domain.vo.StudyPlanListVO;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.GenerateLearningPlanDTO;
import com.codecoachai.interview.feign.dto.GenerateTargetedStudyPlanDTO;
import com.codecoachai.interview.feign.vo.GenerateLearningPlanVO;
import com.codecoachai.interview.feign.vo.InnerSkillGapItemVO;
import com.codecoachai.interview.feign.vo.InnerSkillProfileVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mapper.StudyPlanMapper;
import com.codecoachai.interview.mapper.StudyPlanSkillRelationMapper;
import com.codecoachai.interview.mapper.StudyTaskMapper;
import com.codecoachai.interview.mq.StudyPlanMqDispatcher;
import com.codecoachai.task.service.AsyncTaskService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
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
    @Mock
    private AsyncTaskService asyncTaskService;

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
                new ObjectMapper().findAndRegisterModules(),
                transactionTemplate,
                Optional.of(studyPlanMqDispatcher),
                asyncTaskService);
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

        assertEquals(9001L, detail.getSourceId());
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

    @Test
    void generateFromReportUsesCanonicalInterviewReportSourceId() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        InterviewReport report = generatedReport();
        InterviewSession session = new InterviewSession();
        session.setId(report.getSessionId());
        session.setUserId(USER_ID);
        session.setTargetJobId(TARGET_JOB_ID);
        stubTransactions();
        when(studyPlanMqDispatcher.isAvailable()).thenReturn(true);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(studyPlanMapper.selectOne(any())).thenReturn(null);
        when(studyPlanMapper.insert(any(StudyPlan.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, StudyPlan.class).setId(PLAN_ID);
            return 1;
        });
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(studyPlanMqDispatcher.dispatchGenerateWithReceipt(any(), any()))
                .thenReturn(MqDispatchReceipt.builder()
                        .messageId("study-plan-message-1")
                        .bizType(StudyPlanMqDispatcher.BIZ_TYPE_GENERATE)
                        .bizId(String.valueOf(PLAN_ID))
                        .build());
        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(report.getId());
        dto.setExpectedDurationDays(21);
        dto.setDailyMinutes(90);

        service.generate(dto);

        ArgumentCaptor<StudyPlan> planCaptor = ArgumentCaptor.forClass(StudyPlan.class);
        verify(studyPlanMapper).insert(planCaptor.capture());
        assertEquals("INTERVIEW_REPORT", planCaptor.getValue().getSourceType());
        assertEquals(report.getId(), planCaptor.getValue().getSourceId());
        assertEquals(report.getId(), planCaptor.getValue().getReportId());
        assertEquals(report.getSessionId(), planCaptor.getValue().getSessionId());
        assertEquals(90, planCaptor.getValue().getDailyMinutes());
        verify(asyncTaskService).registerPending(
                org.mockito.ArgumentMatchers.startsWith("study-plan.generate:" + PLAN_ID + ":"),
                org.mockito.ArgumentMatchers.eq(StudyPlanMqDispatcher.BIZ_TYPE_GENERATE),
                org.mockito.ArgumentMatchers.eq(String.valueOf(PLAN_ID)),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.anyString(),
                any(),
                org.mockito.ArgumentMatchers.eq(3));
    }

    @Test
    void listActivePlansUsesStableUpdatedAtAndIdOrdering() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        Page<StudyPlan> page = Page.of(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(studyPlanMapper.selectPage(any(), any())).thenReturn(page);
        StudyPlanQueryDTO query = new StudyPlanQueryDTO();
        query.setPlanStatus("ACTIVE");

        com.codecoachai.common.core.domain.PageResult<StudyPlanListVO> result = service.list(query);

        assertTrue(result.getRecords().isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<StudyPlan>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(studyPlanMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("plan_status"));
        assertTrue(sqlSegment.contains("updated_at DESC"));
        assertTrue(sqlSegment.contains("id DESC"));
    }

    @ParameterizedTest
    @ValueSource(ints = {21, 90})
    void executeReportPlanPersistsRequestedDurationDays(int requestedDays) throws Exception {
        StudyPlan plan = generatingReportPlan(requestedDays);
        GenerateLearningPlanVO aiPlan = validAiPlan(requestedDays, 30);
        stubTransactions();
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateLearningPlan(any())).thenReturn(Result.success(aiPlan));

        StudyPlanGenerateVO result = service.executeGeneration(PLAN_ID, USER_ID);

        assertEquals("ACTIVE", result.getPlanStatus());
        ArgumentCaptor<StudyPlan> planCaptor = ArgumentCaptor.forClass(StudyPlan.class);
        verify(studyPlanMapper).update(planCaptor.capture(), any());
        assertEquals(requestedDays, planCaptor.getValue().getDurationDays());
        ArgumentCaptor<StudyTask> taskCaptor = ArgumentCaptor.forClass(StudyTask.class);
        verify(studyTaskMapper).insert(taskCaptor.capture());
        assertEquals(30, taskCaptor.getValue().getEstimatedMinutes());
    }

    @Test
    void executeReportPlanFailsWhenAiDurationDiffersFromRequest() throws Exception {
        StudyPlan plan = generatingReportPlan(21, 90);
        stubTransactions();
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateLearningPlan(any())).thenReturn(Result.success(validAiPlan(30, 30)));

        StudyPlanGenerateVO result = service.executeGeneration(PLAN_ID, USER_ID);

        assertEquals("FAILED", result.getPlanStatus());
        assertTrue(result.getFailureReason().contains("期望 21 天，实际 30 天"));
        verify(studyTaskMapper, never()).insert(any(StudyTask.class));
    }

    @Test
    void executeReportPlanFailsWhenTaskExceedsDailyBudget() throws Exception {
        StudyPlan plan = generatingReportPlan(21, 90);
        stubTransactions();
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateLearningPlan(any())).thenReturn(Result.success(validAiPlan(21, 91)));

        StudyPlanGenerateVO result = service.executeGeneration(PLAN_ID, USER_ID);

        assertEquals("FAILED", result.getPlanStatus());
        assertTrue(result.getFailureReason().contains("91 分钟超过每日预算 90 分钟"));
        verify(studyTaskMapper, never()).insert(any(StudyTask.class));
    }

    @Test
    void executeTargetedPlanFailsWhenTaskExceedsDailyBudget() throws Exception {
        StudyPlan plan = generatingTargetedPlan(21, 60);
        stubTransactions();
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateTargetedStudyPlan(any())).thenReturn(Result.success(validAiPlan(21, 61)));

        StudyPlanGenerateVO result = service.executeGeneration(PLAN_ID, USER_ID);

        assertEquals("FAILED", result.getPlanStatus());
        assertTrue(result.getFailureReason().contains("61 分钟超过每日预算 60 分钟"));
        verify(studyTaskMapper, never()).insert(any(StudyTask.class));
    }

    @Test
    void executeTargetedPlanFailsWhenSameDayTaskTotalExceedsDailyBudget() throws Exception {
        StudyPlan plan = generatingTargetedPlan(21, 60);
        GenerateLearningPlanVO aiPlan = validAiPlan(21, 35);
        GenerateLearningPlanVO.ItemVO second = new GenerateLearningPlanVO.ItemVO();
        second.setDayOffset(1);
        second.setTaskTitle("Practice concurrency");
        second.setEstimatedMinutes(30);
        aiPlan.getStages().get(0).getItems().get(0).setDayOffset(1);
        aiPlan.getStages().get(0).setItems(List.of(aiPlan.getStages().get(0).getItems().get(0), second));
        stubTransactions();
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateTargetedStudyPlan(any())).thenReturn(Result.success(aiPlan));

        StudyPlanGenerateVO result = service.executeGeneration(PLAN_ID, USER_ID);

        assertEquals("FAILED", result.getPlanStatus());
        assertTrue(result.getFailureReason().contains("第 1 天任务总时长 65 分钟超过每日预算 60 分钟"));
        verify(studyTaskMapper, never()).insert(any(StudyTask.class));
    }

    @Test
    void executeTargetedPlanAllowsFullBudgetOnDifferentDays() throws Exception {
        StudyPlan plan = generatingTargetedPlan(21, 60);
        GenerateLearningPlanVO aiPlan = validAiPlan(21, 60);
        GenerateLearningPlanVO.ItemVO first = aiPlan.getStages().get(0).getItems().get(0);
        first.setDayOffset(1);
        GenerateLearningPlanVO.ItemVO second = new GenerateLearningPlanVO.ItemVO();
        second.setDayOffset(2);
        second.setTaskTitle("Practice concurrency");
        second.setEstimatedMinutes(60);
        aiPlan.getStages().get(0).setItems(List.of(first, second));
        stubTransactions();
        when(studyPlanMapper.selectOne(any())).thenReturn(plan);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateTargetedStudyPlan(any())).thenReturn(Result.success(aiPlan));

        StudyPlanGenerateVO result = service.executeGeneration(PLAN_ID, USER_ID);

        assertEquals("ACTIVE", result.getPlanStatus());
        verify(studyTaskMapper, org.mockito.Mockito.times(2)).insert(any(StudyTask.class));
    }

    @Test
    void generateFromGapInheritsTrustedInterviewReportSource() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        InnerSkillProfileVO profile = trustedInterviewProfile();
        StudyPlanGenerateFromGapDTO dto = new StudyPlanGenerateFromGapDTO();
        dto.setProfileId(profile.getProfileId());
        dto.setDays(21);
        dto.setDailyMinutes(90);
        dto.setStartDate(LocalDate.of(2026, 6, 19));
        stubTransactions();
        when(studyPlanMqDispatcher.isAvailable()).thenReturn(true);
        when(resumeFeignClient.getSkillProfile(profile.getProfileId()))
                .thenReturn(Result.success(profile));
        when(reportMapper.selectOne(any())).thenReturn(generatedReport());
        when(studyPlanMapper.selectList(any())).thenReturn(List.of());
        when(studyPlanMqDispatcher.dispatchGenerateWithReceipt(any(), any()))
                .thenReturn(MqDispatchReceipt.builder()
                        .messageId("study-plan-message-1")
                        .bizType(StudyPlanMqDispatcher.BIZ_TYPE_GENERATE)
                        .bizId(String.valueOf(PLAN_ID))
                        .build());

        service.generateFromGap(dto);

        ArgumentCaptor<StudyPlan> planCaptor = ArgumentCaptor.forClass(StudyPlan.class);
        verify(studyPlanMapper).insert(planCaptor.capture());
        StudyPlan plan = planCaptor.getValue();
        assertEquals("INTERVIEW_REPORT", plan.getSourceType());
        assertEquals(3001L, plan.getSourceId());
        assertEquals(profile.getProfileId(), plan.getSkillProfileId());
        assertEquals(21, plan.getDurationDays());
        assertEquals(90, plan.getDailyMinutes());
    }

    @Test
    void generateRejectsDurationAboveSixtyDaysBeforeDispatch() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        when(reportMapper.selectOne(any())).thenReturn(generatedReport());
        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(3001L);
        dto.setExpectedDurationDays(61);

        assertThrows(BusinessException.class, () -> service.generate(dto));
        verify(studyPlanMqDispatcher, never()).dispatchGenerateWithReceipt(any(), any());
    }

    @Test
    void fallsBackToLocalExecutionAndCompletesTheRegisteredReceipt() throws Exception {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).build());
        InterviewReport report = generatedReport();
        InterviewSession session = new InterviewSession();
        session.setId(report.getSessionId());
        session.setUserId(USER_ID);
        session.setTargetJobId(TARGET_JOB_ID);
        stubTransactions();
        when(studyPlanMqDispatcher.isAvailable()).thenReturn(true);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(studyPlanMapper.selectOne(any())).thenReturn(null, generatingReportPlan(21, 90));
        when(studyPlanMapper.insert(any(StudyPlan.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, StudyPlan.class).setId(PLAN_ID);
            return 1;
        });
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(studyPlanMqDispatcher.dispatchGenerateWithReceipt(any(), any())).thenReturn(null);
        when(studyPlanMapper.update(any(), any())).thenReturn(1);
        when(studyTaskMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(aiFeignClient.generateLearningPlan(any())).thenReturn(Result.success(validAiPlan(21, 30)));
        StudyPlanGenerateDTO dto = new StudyPlanGenerateDTO();
        dto.setReportId(report.getId());
        dto.setExpectedDurationDays(21);
        dto.setDailyMinutes(90);

        service.generate(dto);

        verify(asyncTaskService).completePending(
                org.mockito.ArgumentMatchers.startsWith("study-plan.generate:" + PLAN_ID + ":"),
                org.mockito.ArgumentMatchers.eq(true),
                any(),
                org.mockito.ArgumentMatchers.isNull());
    }

    private StudyPlan generatingReportPlan(int requestedDays) throws Exception {
        return generatingReportPlan(requestedDays, 90);
    }

    private StudyPlan generatingReportPlan(int requestedDays, int dailyMinutes) throws Exception {
        StudyPlan plan = activePlan();
        plan.setSourceType("REPORT");
        plan.setPlanStatus("GENERATING");
        plan.setDurationDays(requestedDays);
        plan.setDailyMinutes(dailyMinutes);
        GenerateLearningPlanDTO request = new GenerateLearningPlanDTO();
        request.setExpectedDurationDays(requestedDays);
        request.setDailyMinutes(dailyMinutes);
        plan.setRequestJson(new ObjectMapper().writeValueAsString(request));
        return plan;
    }

    private StudyPlan generatingTargetedPlan(int requestedDays, int dailyMinutes) throws Exception {
        StudyPlan plan = activePlan();
        plan.setReportId(null);
        plan.setPlanStatus("GENERATING");
        plan.setDurationDays(requestedDays);
        plan.setDailyMinutes(dailyMinutes);
        GenerateTargetedStudyPlanDTO request = new GenerateTargetedStudyPlanDTO();
        request.setAvailableDays(requestedDays);
        request.setDailyMinutes(dailyMinutes);
        request.setSkillGapsJson("[]");
        plan.setRequestJson(new ObjectMapper().writeValueAsString(request));
        return plan;
    }

    private GenerateLearningPlanVO validAiPlan(int durationDays, int estimatedMinutes) {
        GenerateLearningPlanVO.ItemVO item = new GenerateLearningPlanVO.ItemVO();
        item.setTaskTitle("Review concurrency");
        item.setEstimatedMinutes(estimatedMinutes);
        GenerateLearningPlanVO.StageVO stage = new GenerateLearningPlanVO.StageVO();
        stage.setStageNo(1);
        stage.setItems(List.of(item));
        GenerateLearningPlanVO plan = new GenerateLearningPlanVO();
        plan.setDurationDays(durationDays);
        plan.setStages(List.of(stage));
        return plan;
    }

    private InnerSkillProfileVO trustedInterviewProfile() {
        InnerSkillGapItemVO gap = new InnerSkillGapItemVO();
        gap.setId(7001L);
        gap.setProfileId(6001L);
        gap.setUserId(USER_ID);
        gap.setSkillName("Redis");
        gap.setGapLevel(2);
        gap.setPriority(1);
        InnerSkillProfileVO profile = new InnerSkillProfileVO();
        profile.setProfileId(6001L);
        profile.setUserId(USER_ID);
        profile.setTargetJobId(TARGET_JOB_ID);
        profile.setSourceType("INTERVIEW_REPORT");
        profile.setSourceBizId(3001L);
        profile.setStatus("SUCCESS");
        profile.setTargetJobTitle("Java 后端工程师");
        profile.setGapItems(List.of(gap));
        return profile;
    }

    @SuppressWarnings("unchecked")
    private void stubTransactions() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
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
