package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult.PlanTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentCoachActionDTO;
import com.codecoachai.ai.agent.domain.dto.AgentRunFailureDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskSkipDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentRun;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.domain.enums.AgentRunStatusEnum;
import com.codecoachai.ai.agent.domain.enums.AgentTaskStatusEnum;
import com.codecoachai.ai.agent.domain.vo.AgentRunUserDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentCoachActionVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.ai.agent.feign.InterviewReportEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.ResumeOptimizeRecordEvidenceFeignClient;
import com.codecoachai.ai.agent.mapper.AgentReviewMapper;
import com.codecoachai.ai.agent.mapper.AgentRunMapper;
import com.codecoachai.ai.agent.mapper.AgentTaskMapper;
import com.codecoachai.ai.agent.feign.QuestionPracticeEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.ResumeJobApplicationEvidenceFeignClient;
import com.codecoachai.ai.agent.feign.vo.InterviewReportEvidenceVO;
import com.codecoachai.ai.agent.feign.vo.JobApplicationEventEvidenceVO;
import com.codecoachai.ai.agent.feign.vo.PracticeRecordEvidenceVO;
import com.codecoachai.ai.agent.feign.vo.ResumeOptimizeRecordEvidenceVO;
import com.codecoachai.ai.agent.mq.AgentMqDispatcher;
import com.codecoachai.ai.agent.service.AgentContextBuilder;
import com.codecoachai.ai.agent.service.AgentContextUsageReferenceService;
import com.codecoachai.ai.agent.service.AgentConfirmedPlanEffectReconciler;
import com.codecoachai.ai.agent.service.AgentMetricsService;
import com.codecoachai.ai.agent.service.AgentOutputParser;
import com.codecoachai.ai.agent.service.AgentOutputValidator;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.agent.service.AgentWeekPlanService;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.agent.domain.dto.AgentMetricEventDTO;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class JobCoachAgentServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentTaskMapper agentTaskMapper;
    @Mock
    private AgentReviewMapper agentReviewMapper;
    @Mock
    private AgentContextBuilder agentContextBuilder;
    @Mock
    private CandidateTaskBuilder candidateTaskBuilder;
    @Mock
    private AgentPromptBuilder agentPromptBuilder;
    @Mock
    private AgentOutputParser agentOutputParser;
    @Mock
    private AgentOutputValidator agentOutputValidator;
    @Mock
    private AiCallLogService aiCallLogService;
    @Mock
    private AgentMetricsService agentMetricsService;
    @Mock
    private AgentContextUsageReferenceService usageReferenceService;
    @Mock
    private AgentConfirmedPlanEffectReconciler confirmedPlanEffectReconciler;
    @Mock
    private AgentWeekPlanService agentWeekPlanService;
    @Mock
    private QuestionPracticeEvidenceFeignClient questionPracticeEvidenceFeignClient;
    @Mock
    private ResumeJobApplicationEvidenceFeignClient resumeJobApplicationEvidenceFeignClient;
    @Mock
    private InterviewReportEvidenceFeignClient interviewReportEvidenceFeignClient;
    @Mock
    private ResumeOptimizeRecordEvidenceFeignClient resumeOptimizeRecordEvidenceFeignClient;
    @Mock
    private AgentMqDispatcher agentMqDispatcher;
    @Mock
    private TransactionTemplate transactionTemplate;
    private final List<AgentMetricEventDTO> capturedCoachMetrics = new ArrayList<>();

    private JobCoachAgentServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(AgentTask.class);
        initTableInfo(AgentRun.class);
        initTableInfo(AgentReview.class);
    }

    @BeforeEach
    void setUp() {
        service = new JobCoachAgentServiceImpl(
                agentRunMapper,
                agentTaskMapper,
                agentReviewMapper,
                agentContextBuilder,
                candidateTaskBuilder,
                agentPromptBuilder,
                agentOutputParser,
                agentOutputValidator,
                agentMetricsService,
                usageReferenceService,
                confirmedPlanEffectReconciler,
                agentWeekPlanService,
                aiCallLogService,
                questionPracticeEvidenceFeignClient,
                resumeJobApplicationEvidenceFeignClient,
                interviewReportEvidenceFeignClient,
                resumeOptimizeRecordEvidenceFeignClient,
                new ObjectMapper().findAndRegisterModules(),
                agentMqDispatcher,
                transactionTemplate);
        capturedCoachMetrics.clear();
        Mockito.lenient().when(agentMetricsService.acceptEvent(eq(USER_ID), any())).thenAnswer(invocation -> {
            capturedCoachMetrics.add(invocation.getArgument(1));
            return null;
        });
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void startTaskRejectsOtherUsersTaskBeforeMutating() {
        when(agentTaskMapper.selectById(99L)).thenReturn(task(99L, 20L, AgentTaskStatusEnum.TODO.name()));

        assertThrows(BusinessException.class, () -> service.startTask(USER_ID, 99L));

        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeTaskScopesUpdateByUserDeletedAndAllowedStatus() {
        when(agentTaskMapper.selectById(99L))
                .thenReturn(task(99L, USER_ID, AgentTaskStatusEnum.TODO.name()))
                .thenReturn(task(99L, USER_ID, AgentTaskStatusEnum.DONE.name()));
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());

        AgentTaskVO vo = service.completeTask(USER_ID, 99L, null);

        assertEquals(AgentTaskStatusEnum.DONE.name(), vo.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentTaskMapper).update(org.mockito.ArgumentMatchers.<AgentTask>isNull(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("deleted"));
        assertTrue(sqlSegment.contains("status"));
    }

    @Test
    void completeTaskRejectsManualCompletionForEvidenceBoundTasks() {
        AgentTask task = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        task.setTaskType("QUESTION_PRACTICE");
        task.setRelatedBizType("TARGET_JOB");
        task.setRelatedBizId(501L);
        when(agentTaskMapper.selectById(99L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> service.completeTask(USER_ID, 99L, null));

        verify(agentTaskMapper, never()).update(any(), any());
        verify(agentReviewMapper, never()).insert(any(AgentReview.class));
    }

    @Test
    void completeTaskRejectsManualCompletionForResumeOptimizeTask() {
        AgentTask task = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        task.setTaskType("RESUME_OPTIMIZE");
        task.setRelatedBizType("TARGET_JOB");
        task.setRelatedBizId(501L);
        when(agentTaskMapper.selectById(99L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> service.completeTask(USER_ID, 99L, null));

        verify(agentTaskMapper, never()).update(any(), any());
        verify(agentReviewMapper, never()).insert(any(AgentReview.class));
    }

    @Test
    void completeBusinessActionCompletesMatchingQuestionPracticeTaskOnce() {
        AgentTask matched = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        matched.setTaskType("QUESTION_PRACTICE");
        matched.setRelatedBizType("TARGET_JOB");
        matched.setRelatedBizId(501L);
        AgentTask done = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        done.setTaskType("QUESTION_PRACTICE");
        done.setRelatedBizType("TARGET_JOB");
        done.setRelatedBizId(501L);

        when(agentTaskMapper.selectList(any())).thenReturn(List.of(matched));
        when(agentTaskMapper.selectById(99L)).thenReturn(matched).thenReturn(done);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());

        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("QUESTION_PRACTICE");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("PRACTICE_RECORD");
        dto.setEvidenceBizId(7001L);
        dto.setNote("Practice record #7001 submitted");
        when(questionPracticeEvidenceFeignClient.getPracticeRecordEvidence(USER_ID, 7001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        practiceEvidence(7001L, USER_ID, "TARGET_JOB", 501L)));

        AgentTaskVO vo = service.completeBusinessAction(dto);

        assertEquals(99L, vo.getId());
        assertEquals(AgentTaskStatusEnum.DONE.name(), vo.getStatus());
        assertEquals("RULE", vo.getReviewSource());
        assertTrue(vo.getReviewNote().contains("7001"));
        verify(agentTaskMapper).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsUnsupportedTaskTypeBeforeLookup() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("UNKNOWN_ACTION");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionCompletesActiveTaskBeforeReturningDoneTask() {
        AgentTask oldDone = task(100L, USER_ID, AgentTaskStatusEnum.DONE.name());
        oldDone.setTaskType("QUESTION_PRACTICE");
        oldDone.setRelatedBizType("TARGET_JOB");
        oldDone.setRelatedBizId(501L);
        oldDone.setDueDate(LocalDate.now());
        oldDone.setSortOrder(1);
        AgentTask active = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        active.setTaskType("QUESTION_PRACTICE");
        active.setRelatedBizType("TARGET_JOB");
        active.setRelatedBizId(501L);
        active.setDueDate(LocalDate.now().minusDays(1));
        active.setSortOrder(2);
        AgentTask activeDone = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        activeDone.setTaskType("QUESTION_PRACTICE");
        activeDone.setRelatedBizType("TARGET_JOB");
        activeDone.setRelatedBizId(501L);

        when(agentTaskMapper.selectList(any())).thenReturn(List.of(oldDone, active));
        when(agentTaskMapper.selectById(99L)).thenReturn(active).thenReturn(activeDone);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());
        when(questionPracticeEvidenceFeignClient.getPracticeRecordEvidence(USER_ID, 7001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        practiceEvidence(7001L, USER_ID, "TARGET_JOB", 501L)));

        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("QUESTION_PRACTICE");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("PRACTICE_RECORD");
        dto.setEvidenceBizId(7001L);

        AgentTaskVO vo = service.completeBusinessAction(dto);

        assertEquals(99L, vo.getId());
        assertEquals(AgentTaskStatusEnum.DONE.name(), vo.getStatus());
        verify(agentTaskMapper).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsMissingRelatedBusinessScope() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("QUESTION_PRACTICE");

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsQuestionPracticeWithoutPracticeRecordEvidence() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("QUESTION_PRACTICE");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsPracticeRecordFromDifferentTargetJob() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("QUESTION_PRACTICE");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("PRACTICE_RECORD");
        dto.setEvidenceBizId(7001L);
        when(questionPracticeEvidenceFeignClient.getPracticeRecordEvidence(USER_ID, 7001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        practiceEvidence(7001L, USER_ID, "TARGET_JOB", 999L)));

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsApplicationFollowUpWithoutEventEvidence() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("APPLICATION_FOLLOW_UP");
        dto.setRelatedBizType("JOB_APPLICATION");
        dto.setRelatedBizId(88L);

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsApplicationEventFromDifferentApplication() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("APPLICATION_FOLLOW_UP");
        dto.setRelatedBizType("JOB_APPLICATION");
        dto.setRelatedBizId(88L);
        dto.setEvidenceBizType("JOB_APPLICATION_EVENT");
        dto.setEvidenceBizId(701L);
        when(resumeJobApplicationEvidenceFeignClient.getApplicationEventEvidence(USER_ID, 701L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        applicationEventEvidence(701L, USER_ID, 99L)));

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsApplicationNoteEventEvidence() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("APPLICATION_FOLLOW_UP");
        dto.setRelatedBizType("JOB_APPLICATION");
        dto.setRelatedBizId(88L);
        dto.setEvidenceBizType("JOB_APPLICATION_EVENT");
        dto.setEvidenceBizId(701L);
        when(resumeJobApplicationEvidenceFeignClient.getApplicationEventEvidence(USER_ID, 701L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        applicationEventEvidence(701L, USER_ID, 88L, "NOTE")));

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionCompletesApplicationFollowUpWithEventEvidence() {
        AgentTask matched = task(188L, USER_ID, AgentTaskStatusEnum.TODO.name());
        matched.setTaskType("APPLICATION_FOLLOW_UP");
        matched.setRelatedBizType("JOB_APPLICATION");
        matched.setRelatedBizId(88L);
        AgentTask done = task(188L, USER_ID, AgentTaskStatusEnum.DONE.name());
        done.setTaskType("APPLICATION_FOLLOW_UP");
        done.setRelatedBizType("JOB_APPLICATION");
        done.setRelatedBizId(88L);

        when(resumeJobApplicationEvidenceFeignClient.getApplicationEventEvidence(USER_ID, 701L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        applicationEventEvidence(701L, USER_ID, 88L, "INTERVIEW")));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(matched));
        when(agentTaskMapper.selectById(188L)).thenReturn(matched).thenReturn(done);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());

        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("APPLICATION_FOLLOW_UP");
        dto.setRelatedBizType("JOB_APPLICATION");
        dto.setRelatedBizId(88L);
        dto.setEvidenceBizType("JOB_APPLICATION_EVENT");
        dto.setEvidenceBizId(701L);

        AgentTaskVO vo = service.completeBusinessAction(dto);

        assertEquals(188L, vo.getId());
        assertEquals(AgentTaskStatusEnum.DONE.name(), vo.getStatus());
        assertTrue(vo.getReviewNote().contains("701"));
        verify(agentTaskMapper).update(any(), any());
    }

    @Test
    void completeBusinessActionDoesNotCompleteTaskFromDifferentEvidenceDate() {
        AgentTask yesterday = task(188L, USER_ID, AgentTaskStatusEnum.TODO.name());
        yesterday.setTaskType("APPLICATION_FOLLOW_UP");
        yesterday.setRelatedBizType("JOB_APPLICATION");
        yesterday.setRelatedBizId(88L);
        yesterday.setDueDate(LocalDate.now().minusDays(1));

        when(resumeJobApplicationEvidenceFeignClient.getApplicationEventEvidence(USER_ID, 701L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        applicationEventEvidence(701L, USER_ID, 88L, "INTERVIEW", LocalDateTime.now())));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(yesterday));

        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("APPLICATION_FOLLOW_UP");
        dto.setRelatedBizType("JOB_APPLICATION");
        dto.setRelatedBizId(88L);
        dto.setEvidenceBizType("JOB_APPLICATION_EVENT");
        dto.setEvidenceBizId(701L);

        AgentTaskVO vo = service.completeBusinessAction(dto);

        assertNull(vo);
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsInterviewWithoutReportEvidence() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("INTERVIEW");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsInterviewReportFromDifferentTargetJob() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("INTERVIEW");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("INTERVIEW_REPORT");
        dto.setEvidenceBizId(9001L);
        when(interviewReportEvidenceFeignClient.getReportEvidence(USER_ID, 9001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        interviewReportEvidence(9001L, USER_ID, 1L, 999L, "GENERATED")));

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsInterviewReportThatIsNotGenerated() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("INTERVIEW");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("INTERVIEW_REPORT");
        dto.setEvidenceBizId(9001L);
        when(interviewReportEvidenceFeignClient.getReportEvidence(USER_ID, 9001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        interviewReportEvidence(9001L, USER_ID, 1L, 501L, "FAILED")));

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void completeBusinessActionCompletesInterviewWithGeneratedReportEvidence() {
        AgentTask matched = task(288L, USER_ID, AgentTaskStatusEnum.TODO.name());
        matched.setTaskType("INTERVIEW");
        matched.setRelatedBizType("TARGET_JOB");
        matched.setRelatedBizId(501L);
        AgentTask done = task(288L, USER_ID, AgentTaskStatusEnum.DONE.name());
        done.setTaskType("INTERVIEW");
        done.setRelatedBizType("TARGET_JOB");
        done.setRelatedBizId(501L);

        when(interviewReportEvidenceFeignClient.getReportEvidence(USER_ID, 9001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        interviewReportEvidence(9001L, USER_ID, 1L, 501L, "GENERATED")));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(matched));
        when(agentTaskMapper.selectById(288L)).thenReturn(matched).thenReturn(done);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());

        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("INTERVIEW");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("INTERVIEW_REPORT");
        dto.setEvidenceBizId(9001L);

        AgentTaskVO vo = service.completeBusinessAction(dto);

        assertEquals(288L, vo.getId());
        assertEquals(AgentTaskStatusEnum.DONE.name(), vo.getStatus());
        assertTrue(vo.getReviewNote().contains("9001"));
        verify(agentTaskMapper).update(any(), any());
    }

    @Test
    void completeBusinessActionCompletesResumeOptimizeWithSuccessfulRecordEvidence() {
        AgentTask matched = task(388L, USER_ID, AgentTaskStatusEnum.TODO.name());
        matched.setTaskType("RESUME_OPTIMIZE");
        matched.setRelatedBizType("TARGET_JOB");
        matched.setRelatedBizId(501L);
        AgentTask done = task(388L, USER_ID, AgentTaskStatusEnum.DONE.name());
        done.setTaskType("RESUME_OPTIMIZE");
        done.setRelatedBizType("TARGET_JOB");
        done.setRelatedBizId(501L);

        when(resumeOptimizeRecordEvidenceFeignClient.getOptimizeRecordEvidence(USER_ID, 7001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        resumeOptimizeEvidence(7001L, USER_ID, 100L, 501L, "SUCCESS")));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(matched));
        when(agentTaskMapper.selectById(388L)).thenReturn(matched).thenReturn(done);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());

        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("RESUME_OPTIMIZE");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("RESUME_OPTIMIZE_RECORD");
        dto.setEvidenceBizId(7001L);

        AgentTaskVO vo = service.completeBusinessAction(dto);

        assertEquals(388L, vo.getId());
        assertEquals(AgentTaskStatusEnum.DONE.name(), vo.getStatus());
        assertTrue(vo.getReviewNote().contains("7001"));
        verify(agentTaskMapper).update(any(), any());
    }

    @Test
    void completeBusinessActionRejectsResumeOptimizeRecordFromDifferentTargetJob() {
        AgentBusinessActionCompleteDTO dto = new AgentBusinessActionCompleteDTO();
        dto.setUserId(USER_ID);
        dto.setTaskType("RESUME_OPTIMIZE");
        dto.setRelatedBizType("TARGET_JOB");
        dto.setRelatedBizId(501L);
        dto.setEvidenceBizType("RESUME_OPTIMIZE_RECORD");
        dto.setEvidenceBizId(7001L);
        when(resumeOptimizeRecordEvidenceFeignClient.getOptimizeRecordEvidence(USER_ID, 7001L))
                .thenReturn(com.codecoachai.common.core.domain.Result.success(
                        resumeOptimizeEvidence(7001L, USER_ID, 100L, 999L, "SUCCESS")));

        assertThrows(BusinessException.class, () -> service.completeBusinessAction(dto));

        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).update(any(), any());
    }

    @Test
    void executeDailyPlanDoesNotReviveCanceledRun() {
        AgentRun canceled = run(77L, USER_ID);
        canceled.setStatus(AgentRunStatusEnum.CANCELED.name());
        when(agentRunMapper.selectById(77L)).thenReturn(canceled);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanVO vo = service.executeDailyPlan(USER_ID, 77L, new DailyPlanGenerateDTO());

        assertEquals(AgentRunStatusEnum.CANCELED.name(), vo.getStatus());
        verify(agentRunMapper, never()).updateById(any(AgentRun.class));
        verify(agentContextBuilder, never()).build(any(), any(), any());
        verify(candidateTaskBuilder, never()).build(any(), any(Integer.class));
    }

    @Test
    void failDailyPlanRunMarksRunningRunFailedFromAsyncTerminalFailure() {
        AgentRun running = run(77L, USER_ID);
        running.setStatus(AgentRunStatusEnum.RUNNING.name());
        running.setStartedAt(LocalDateTime.now().minusMinutes(2));
        running.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(running);
        when(agentRunMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        AgentRunFailureDTO dto = new AgentRunFailureDTO();
        dto.setUserId(USER_ID);
        dto.setExecutionToken("run-token-1");
        dto.setErrorCode(AgentErrorCode.ASYNC_TASK_FAILED);
        dto.setErrorMessage("agent daily plan execute failed: invalid target job");

        DailyPlanVO vo = service.failDailyPlanRun(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.FAILED.name(), vo.getStatus());
        assertEquals(AgentErrorCode.ASYNC_TASK_FAILED, vo.getErrorCode());
        ArgumentCaptor<Wrapper<AgentRun>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentRunMapper).update(org.mockito.ArgumentMatchers.<AgentRun>isNull(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("RUNNING") || sqlSegment.contains("status"));
    }

    @Test
    void failDailyPlanRunDoesNotOverwriteTerminalRun() {
        AgentRun success = run(77L, USER_ID);
        success.setStatus(AgentRunStatusEnum.SUCCESS.name());
        when(agentRunMapper.selectById(77L)).thenReturn(success);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        AgentRunFailureDTO dto = new AgentRunFailureDTO();
        dto.setUserId(USER_ID);
        dto.setErrorCode(AgentErrorCode.ASYNC_TASK_FAILED);
        dto.setErrorMessage("terminal failure after success should be ignored");

        DailyPlanVO vo = service.failDailyPlanRun(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.SUCCESS.name(), vo.getStatus());
        verify(agentRunMapper, never()).update(any(), any());
        verify(agentRunMapper, never()).updateById(any(AgentRun.class));
    }

    @Test
    void forceRegenerateSoftDeletesOpenTasksForCanceledRuns() {
        LocalDate planDate = LocalDate.now();
        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setDate(planDate);
        dto.setTargetJobId(501L);
        dto.setForceRegenerate(true);

        AgentRun oldRun = run(77L, USER_ID);
        oldRun.setTargetJobId(501L);
        oldRun.setPlanDate(planDate);
        oldRun.setStatus(AgentRunStatusEnum.SUCCESS.name());
        AgentRun newRun = run(88L, USER_ID);
        newRun.setTargetJobId(501L);
        newRun.setPlanDate(planDate);
        newRun.setStatus(AgentRunStatusEnum.RUNNING.name());

        when(agentRunMapper.selectList(any())).thenReturn(List.of(oldRun));
        when(agentRunMapper.insert(any(AgentRun.class))).thenAnswer(invocation -> {
            AgentRun inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        });
        when(agentMqDispatcher.dispatchDailyPlanWithReceipt(any(), any(), any()))
                .thenReturn(MqDispatchReceipt.builder()
                        .messageId("msg-1")
                        .traceId("trace-1")
                        .bizType("agent.daily-plan.generate")
                        .bizId("88")
                        .userId(USER_ID)
                        .build());
        when(agentRunMapper.selectById(88L)).thenReturn(newRun);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanVO vo = service.generateDailyPlan(USER_ID, dto);

        assertEquals(88L, vo.getRunId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentTaskMapper).update(org.mockito.ArgumentMatchers.<AgentTask>isNull(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("agent_run_id"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    @Test
    void generateDailyPlanDefersMqDispatchUntilAfterCommitWhenTransactionSynchronizationActive() {
        LocalDate planDate = LocalDate.now();
        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setDate(planDate);
        dto.setTargetJobId(501L);

        AgentRun run = run(88L, USER_ID);
        run.setTargetJobId(501L);
        run.setPlanDate(planDate);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());

        when(agentRunMapper.insert(any(AgentRun.class))).thenAnswer(invocation -> {
            AgentRun inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        });
        when(agentRunMapper.selectById(88L)).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();

        DailyPlanVO vo = service.generateDailyPlan(USER_ID, dto);

        assertEquals(88L, vo.getRunId());
        verify(agentMqDispatcher, never()).dispatchDailyPlanWithReceipt(any(), any(), any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(agentMqDispatcher).dispatchDailyPlanWithReceipt(eq(88L), eq(USER_ID), any(DailyPlanGenerateDTO.class));
        verify(agentContextBuilder, never()).build(any(), any(), any());
    }

    @Test
    void latestDailyPlanExposesFrontendAliasesAndPlanHandoffs() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        run.setPlanDate(LocalDate.of(2026, 6, 26));
        run.setTargetJobId(501L);
        run.setInputSnapshotJson("""
                {"requestId":"req-123","context":{"targetJobId":501}}
                """);
        run.setOutputJson("""
                {"summary":"daily plan","focusSkills":[],"tasks":[]}
                """);
        AgentTask doneTask = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        doneTask.setTargetJobId(501L);
        doneTask.setEstimatedMinutes(45);
        doneTask.setActionUrl("/practice/99");
        doneTask.setTaskType("QUESTION_PRACTICE");
        AgentReview review = new AgentReview();
        review.setId(200L);
        review.setSummary("completed");
        review.setNextActionsJson("[]");
        review.setReviewJson("""
                {"taskId":99,"generatedSource":"RULE","activationHandoffs":[{"code":"ACT-001-FIRST-TASK-COMPLETED","stage":"first_task_completed","firstOccurrence":true,"taskId":99}]}
                """);

        when(agentRunMapper.selectOne(any())).thenReturn(run);
        when(agentRunMapper.selectList(any())).thenReturn(List.of());
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(doneTask));
        when(agentReviewMapper.selectList(any())).thenReturn(List.of(review));

        DailyPlanVO vo = service.latestDailyPlan(USER_ID, 501L, LocalDate.of(2026, 6, 26));

        assertEquals(LocalDate.of(2026, 6, 26), vo.getDate());
        assertEquals(LocalDate.of(2026, 6, 26), vo.getPlanDate());
        assertEquals("req-123", vo.getRequestId());
        assertEquals(2, vo.getActivationHandoffs().size());
        assertEquals("ACT-001-TARGET-DIRECTION-ESTABLISHED", vo.getActivationHandoffs().get(0).getCode());
        assertEquals("ACT-001-FIRST-PLAN-GENERATED", vo.getActivationHandoffs().get(1).getCode());
        assertEquals(1, vo.getTasks().size());
        AgentTaskVO task = vo.getTasks().get(0);
        assertEquals(Integer.valueOf(45), task.getEstimatedMinutes());
        assertEquals(Integer.valueOf(45), task.getEstimatedEffortMinutes());
        assertEquals("/practice/99", task.getActionUrl());
        assertEquals("PRACTICE", task.getActionType());
        assertEquals(1, task.getActivationHandoffs().size());
        assertEquals("ACT-001-FIRST-TASK-COMPLETED", task.getActivationHandoffs().get(0).getCode());
    }

    @Test
    void generateDailyPlanNormalizesRequestIdFromIdempotencyKeyForAsyncDispatch() {
        LocalDate planDate = LocalDate.now();
        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setDate(planDate);
        dto.setTargetJobId(501L);
        dto.setIdempotencyKey("idem-001");

        AgentRun run = run(88L, USER_ID);
        run.setTargetJobId(501L);
        run.setPlanDate(planDate);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());

        when(agentRunMapper.insert(any(AgentRun.class))).thenAnswer(invocation -> {
            AgentRun inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        });
        when(agentRunMapper.selectById(88L)).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());
        when(agentMqDispatcher.dispatchDailyPlanWithReceipt(any(), any(), any()))
                .thenReturn(MqDispatchReceipt.builder()
                        .messageId("msg-1")
                        .traceId("trace-1")
                        .bizType("agent.daily-plan.generate")
                        .bizId("88")
                        .userId(USER_ID)
                        .build());

        service.generateDailyPlan(USER_ID, dto);

        ArgumentCaptor<DailyPlanGenerateDTO> dtoCaptor = ArgumentCaptor.forClass(DailyPlanGenerateDTO.class);
        verify(agentMqDispatcher).dispatchDailyPlanWithReceipt(eq(88L), eq(USER_ID), dtoCaptor.capture());
        assertEquals("idem-001", dtoCaptor.getValue().getRequestId());
        assertEquals("idem-001", dtoCaptor.getValue().getIdempotencyKey());
        assertNotNull(dtoCaptor.getValue().getExecutionToken());
    }

    @Test
    void todayTasksFiltersLogicallyDeletedTasks() {
        LocalDate dueDate = LocalDate.now();
        AgentRun run = run(77L, USER_ID);
        run.setTargetJobId(501L);
        run.setPlanDate(dueDate);
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        when(agentRunMapper.selectOne(any())).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        service.todayTasks(USER_ID, 501L, dueDate, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentTaskMapper).selectList(wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("deleted"));
    }

    @Test
    void todayTasksWithoutTargetReadsLatestLocalRunWithoutBuildingRemoteContext() {
        LocalDate dueDate = LocalDate.now();
        AgentRun run = run(77L, USER_ID);
        run.setTargetJobId(501L);
        run.setPlanDate(dueDate);
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        when(agentRunMapper.selectOne(any())).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        service.todayTasks(USER_ID, null, dueDate, null);

        verify(agentContextBuilder, never()).build(any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentRun>> runQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentRunMapper).selectOne(runQueryCaptor.capture());
        String sqlSegment = runQueryCaptor.getValue().getSqlSegment();
        assertFalse(sqlSegment.contains("target_job_id"));
        assertTrue(sqlSegment.contains("plan_date"));
        assertTrue(wrapperParams(runQueryCaptor.getValue()).containsValue(dueDate));
    }

    @Test
    void pageTasksFiltersLogicallyDeletedTasks() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AgentTask> emptyPage =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 10);
        emptyPage.setRecords(List.of());
        when(agentTaskMapper.selectPage(any(), any())).thenReturn(emptyPage);

        service.pageTasks(USER_ID, new AgentTaskQueryDTO());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentTaskMapper).selectPage(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("deleted"));
    }

    @Test
    void pageTasksUsesDateRangeFiltersWhenProvided() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AgentTask> emptyPage =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 10);
        emptyPage.setRecords(List.of());
        when(agentTaskMapper.selectPage(any(), any())).thenReturn(emptyPage);
        AgentTaskQueryDTO query = new AgentTaskQueryDTO();
        query.setStartDate(LocalDate.of(2026, 6, 1));
        query.setEndDate(LocalDate.of(2026, 6, 18));

        service.pageTasks(USER_ID, query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentTaskMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("due_date"));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(LocalDate.of(2026, 6, 1)));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(LocalDate.of(2026, 6, 18)));
    }

    @Test
    void adminPageRunsUsesTriggerTypeAndPlanDateRangeFilters() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AgentRun> emptyPage =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 10);
        emptyPage.setRecords(List.of());
        when(agentRunMapper.selectPage(any(), any())).thenReturn(emptyPage);
        AdminAgentRunQueryDTO query = new AdminAgentRunQueryDTO();
        query.setTriggerType("MANUAL");
        query.setStartDate(LocalDate.of(2026, 6, 1));
        query.setEndDate(LocalDate.of(2026, 6, 18));

        service.adminPageRuns(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentRun>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentRunMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("trigger_type"));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue("MANUAL"));
        assertTrue(sqlSegment.contains("plan_date"));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(LocalDate.of(2026, 6, 1)));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(LocalDate.of(2026, 6, 18)));
    }

    @Test
    void adminPageTasksUsesDateRangeFiltersWhenProvided() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AgentTask> emptyPage =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(1, 10);
        emptyPage.setRecords(List.of());
        when(agentTaskMapper.selectPage(any(), any())).thenReturn(emptyPage);
        AdminAgentTaskQueryDTO query = new AdminAgentTaskQueryDTO();
        query.setStartDate(LocalDate.of(2026, 6, 1));
        query.setEndDate(LocalDate.of(2026, 6, 18));

        service.adminPageTasks(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentTaskMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("due_date"));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(LocalDate.of(2026, 6, 1)));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(LocalDate.of(2026, 6, 18)));
    }

    @Test
    void executeDailyPlanStoresCandidateBusinessBindingWhenAiTaskIsTampered() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        run.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(run);
        when(agentRunMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(USER_ID);
        context.setTargetJobId(501L);
        context.setPlanDate(LocalDate.now());
        when(agentContextBuilder.build(any(), any(), any())).thenReturn(context);
        CandidateTask candidate = candidate("c1", "TARGET_JOB", 501L, "/questions/practice?mode=category");
        when(candidateTaskBuilder.build(any(), any(Integer.class))).thenReturn(List.of(candidate));
        when(agentPromptBuilder.buildDailyPlanPrompt(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(PromptRenderResult.builder().renderedPrompt("prompt").build());
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("{}");
        when(aiCallLogService.callAndLog(any())).thenReturn(routeResult);

        DailyPlanResult result = new DailyPlanResult();
        result.setSummary("今日计划");
        PlanTask tampered = new PlanTask();
        tampered.setCandidateId("c1");
        tampered.setType("QUESTION_PRACTICE");
        tampered.setTitle("完成一组题目练习");
        tampered.setReason("补齐目标岗位短板");
        tampered.setPriority("HIGH");
        tampered.setEstimatedMinutes(30);
        tampered.setRelatedBizType("ADMIN_USER");
        tampered.setRelatedBizId(999L);
        tampered.setActionUrl("/admin/users");
        result.setTasks(List.of(tampered));
        when(agentOutputParser.parseDailyPlan(any())).thenReturn(result);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setExecutionToken("run-token-1");
        service.executeDailyPlan(USER_ID, 77L, dto);

        ArgumentCaptor<AgentTask> taskCaptor = ArgumentCaptor.forClass(AgentTask.class);
        verify(agentTaskMapper).insert(taskCaptor.capture());
        AgentTask saved = taskCaptor.getValue();
        assertEquals("TARGET_JOB", saved.getRelatedBizType());
        assertEquals(501L, saved.getRelatedBizId());
        assertEquals("/questions/practice?mode=category", saved.getActionUrl());
    }

    @Test
    void executeDailyPlanMasksPiiBeforePersistingAgentRunRawDiagnostics() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        run.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(run);
        when(agentRunMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(USER_ID);
        context.setTargetJobId(501L);
        context.setPlanDate(LocalDate.now());
        JobCoachAgentContext.TargetJobSnapshot targetJob = new JobCoachAgentContext.TargetJobSnapshot();
        targetJob.setId(501L);
        targetJob.setJobTitle("Java Engineer");
        targetJob.setCompanyName("Demo Corp");
        targetJob.setJdSource("{\"realName\":\"Zhang San\",\"phone\":\"13812345678\",\"email\":\"zhangsan@example.com\"}");
        context.setTargetJob(targetJob);
        JobCoachAgentContext.ApplicationSnapshot application = new JobCoachAgentContext.ApplicationSnapshot();
        application.setId(601L);
        application.setTargetJobId(501L);
        application.setNote("candidate phone 13812345678 email zhangsan@example.com");
        context.setApplications(List.of(application));
        context.setAgentHistorySummary("last raw contact 13812345678 zhangsan@example.com");
        when(agentContextBuilder.build(any(), any(), any())).thenReturn(context);
        CandidateTask candidate = candidate("c1", "TARGET_JOB", 501L, "/questions/practice?mode=category");
        when(candidateTaskBuilder.build(any(), any(Integer.class))).thenReturn(List.of(candidate));
        when(agentPromptBuilder.buildDailyPlanPrompt(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(PromptRenderResult.builder().renderedPrompt("prompt").build());
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("""
                {"summary":"contact 13812345678 or zhangsan@example.com","tasks":[]}
                """);
        when(aiCallLogService.callAndLog(any())).thenReturn(routeResult);

        DailyPlanResult result = new DailyPlanResult();
        result.setSummary("daily plan for 13812345678 zhangsan@example.com");
        result.setTasks(List.of());
        when(agentOutputParser.parseDailyPlan(any())).thenReturn(result);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setExecutionToken("run-token-1");
        service.executeDailyPlan(USER_ID, 77L, dto);

        assertFalse(run.getInputSnapshotJson().contains("Zhang San"));
        assertFalse(run.getInputSnapshotJson().contains("13812345678"));
        assertFalse(run.getInputSnapshotJson().contains("zhangsan@example.com"));
        assertTrue(run.getInputSnapshotJson().contains("Z********"));
        assertTrue(run.getInputSnapshotJson().contains("138****5678"));
        assertTrue(run.getInputSnapshotJson().contains("***@example.com"));
        assertFalse(run.getRawOutputText().contains("13812345678"));
        assertFalse(run.getRawOutputText().contains("zhangsan@example.com"));
        assertTrue(run.getRawOutputText().contains("138****5678"));
        assertTrue(run.getRawOutputText().contains("***@example.com"));
        assertFalse(run.getOutputJson().contains("13812345678"));
        assertFalse(run.getOutputJson().contains("zhangsan@example.com"));
        assertTrue(run.getOutputJson().contains("138****5678"));
        assertTrue(run.getOutputJson().contains("***@example.com"));
    }

    @Test
    void executeDailyPlanDegradesValidatedOutputFailureIntoOneCandidateBoundTask() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        run.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(run);
        when(agentRunMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(USER_ID);
        context.setTargetJobId(501L);
        context.setPlanDate(LocalDate.now());
        when(agentContextBuilder.build(any(), any(), any())).thenReturn(context);
        CandidateTask candidate = candidate("c1", "TARGET_JOB", 501L, "/questions/practice?mode=category");
        when(candidateTaskBuilder.build(any(), any(Integer.class))).thenReturn(List.of(candidate));
        when(agentPromptBuilder.buildDailyPlanPrompt(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(PromptRenderResult.builder().renderedPrompt("prompt").build());
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("{\"summary\":\"模型返回的空计划\",\"tasks\":[]}");
        routeResult.setResultSource("LLM");
        routeResult.setTraceId("trace-degraded-1");
        routeResult.setAiCallLogId(901L);
        when(aiCallLogService.callAndLog(any())).thenReturn(routeResult);

        DailyPlanResult invalidResult = new DailyPlanResult();
        invalidResult.setSummary("模型返回的空计划");
        invalidResult.setTasks(List.of());
        when(agentOutputParser.parseDailyPlan(any())).thenReturn(invalidResult);
        AgentOutputValidator actualValidator = new AgentOutputValidatorImpl();
        Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<CandidateTask> validationCandidates = invocation.getArgument(1);
            actualValidator.validateDailyPlan(
                    invocation.getArgument(0, DailyPlanResult.class),
                    validationCandidates,
                    invocation.getArgument(2, Integer.class),
                    invocation.getArgument(3, Integer.class));
            return null;
        }).when(agentOutputValidator).validateDailyPlan(any(), any(), any(Integer.class), any(Integer.class));

        List<AgentTask> persistedTasks = new ArrayList<>();
        when(agentTaskMapper.insert(any(AgentTask.class))).thenAnswer(invocation -> {
            AgentTask saved = invocation.getArgument(0);
            saved.setId(88L);
            persistedTasks.add(saved);
            return 1;
        });
        when(agentTaskMapper.selectList(any())).thenAnswer(invocation -> persistedTasks);

        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setExecutionToken("run-token-1");
        DailyPlanVO vo = service.executeDailyPlan(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.SUCCESS.name(), vo.getStatus());
        assertEquals("DEGRADED", run.getResultSource());
        assertTrue(vo.getFallback());
        assertNull(vo.getErrorCode());
        assertTrue(vo.getSummary().contains("模型输出未通过计划结构校验"));
        assertEquals(1, vo.getTasks().size());
        assertEquals(1, persistedTasks.size());
        AgentTask saved = persistedTasks.get(0);
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(77L, saved.getAgentRunId());
        assertEquals(501L, saved.getTargetJobId());
        assertEquals("c1", saved.getCandidateId());
        assertEquals("TARGET_JOB", saved.getRelatedBizType());
        assertEquals(501L, saved.getRelatedBizId());
        assertEquals("/questions/practice?mode=category", saved.getActionUrl());
        assertEquals("完成一项今日可执行任务", saved.getTitle());
        assertTrue(run.getOutputJson().contains("模型输出未通过计划结构校验"));
        verify(agentOutputValidator, Mockito.times(2))
                .validateDailyPlan(any(), any(), any(Integer.class), any(Integer.class));

        DailyPlanVO repeated = service.executeDailyPlan(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.SUCCESS.name(), repeated.getStatus());
        verify(aiCallLogService, Mockito.times(1)).callAndLog(any());
        verify(agentTaskMapper, Mockito.times(1)).insert(any(AgentTask.class));
    }

    @Test
    void executeDailyPlanPersistsAiResultSource() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        run.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(run);
        when(agentRunMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(USER_ID);
        context.setTargetJobId(501L);
        context.setPlanDate(LocalDate.now());
        when(agentContextBuilder.build(any(), any(), any())).thenReturn(context);
        CandidateTask candidate = candidate("c1", "TARGET_JOB", 501L, "/questions/practice?mode=category");
        when(candidateTaskBuilder.build(any(), any(Integer.class))).thenReturn(List.of(candidate));
        when(agentPromptBuilder.buildDailyPlanPrompt(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(PromptRenderResult.builder().renderedPrompt("prompt").build());
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("{}");
        routeResult.setResultSource("FALLBACK");
        when(aiCallLogService.callAndLog(any())).thenReturn(routeResult);

        DailyPlanResult result = new DailyPlanResult();
        result.setSummary("daily plan");
        result.setTasks(List.of());
        when(agentOutputParser.parseDailyPlan(any())).thenReturn(result);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setExecutionToken("run-token-1");
        service.executeDailyPlan(USER_ID, 77L, dto);

        assertEquals("FALLBACK", run.getResultSource());
        ArgumentCaptor<Wrapper<AgentRun>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentRunMapper, atLeastOnce()).update(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getAllValues().stream()
                .anyMatch(wrapper -> wrapperParams(wrapper).containsValue("FALLBACK")));
    }

    @Test
    void executeDailyPlanDoesNotClearOrInsertTasksWhenSuccessTransitionLosesRace() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        run.setExecutionToken("run-token-1");
        AgentRun latest = run(77L, USER_ID);
        latest.setStatus(AgentRunStatusEnum.SUCCESS.name());
        latest.setTargetJobId(501L);
        latest.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(run).thenReturn(run).thenReturn(latest);
        when(agentRunMapper.update(any(), any())).thenReturn(1).thenReturn(1).thenReturn(1).thenReturn(0);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        JobCoachAgentContext context = new JobCoachAgentContext();
        context.setUserId(USER_ID);
        context.setTargetJobId(501L);
        context.setPlanDate(LocalDate.now());
        when(agentContextBuilder.build(any(), any(), any())).thenReturn(context);
        CandidateTask candidate = candidate("c1", "TARGET_JOB", 501L, "/questions/practice?mode=category");
        when(candidateTaskBuilder.build(any(), any(Integer.class))).thenReturn(List.of(candidate));
        when(agentPromptBuilder.buildDailyPlanPrompt(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(PromptRenderResult.builder().renderedPrompt("prompt").build());
        RouteResult routeResult = new RouteResult();
        routeResult.setContent("{}");
        when(aiCallLogService.callAndLog(any())).thenReturn(routeResult);

        DailyPlanResult result = new DailyPlanResult();
        result.setSummary("daily plan");
        PlanTask planTask = new PlanTask();
        planTask.setCandidateId("c1");
        planTask.setType("QUESTION_PRACTICE");
        planTask.setTitle("practice one set");
        planTask.setReason("close gap");
        planTask.setPriority("HIGH");
        planTask.setEstimatedMinutes(30);
        result.setTasks(List.of(planTask));
        when(agentOutputParser.parseDailyPlan(any())).thenReturn(result);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setExecutionToken("run-token-1");
        DailyPlanVO vo = service.executeDailyPlan(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.SUCCESS.name(), vo.getStatus());
        verify(agentTaskMapper, never()).update(any(), any());
        verify(agentTaskMapper, never()).insert(any(AgentTask.class));
    }

    @Test
    void executeDailyPlanDoesNotReachAiWhenExecutionTokenClaimLosesRace() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        run.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(run).thenReturn(run);
        when(agentRunMapper.update(any(), any())).thenReturn(0);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setExecutionToken("run-token-1");

        DailyPlanVO vo = service.executeDailyPlan(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.RUNNING.name(), vo.getStatus());
        verify(agentContextBuilder, never()).build(any(), any(), any());
        verify(aiCallLogService, never()).callAndLog(any());
        verify(agentTaskMapper, never()).insert(any(AgentTask.class));
    }

    @Test
    void failDailyPlanRunDoesNotOverwriteRunningRunWhenExecutionTokenMismatched() {
        AgentRun running = run(77L, USER_ID);
        running.setStatus(AgentRunStatusEnum.RUNNING.name());
        running.setExecutionToken("run-token-1");
        when(agentRunMapper.selectById(77L)).thenReturn(running).thenReturn(running);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        AgentRunFailureDTO dto = new AgentRunFailureDTO();
        dto.setUserId(USER_ID);
        dto.setExecutionToken("run-token-2");
        dto.setErrorCode(AgentErrorCode.ASYNC_TASK_FAILED);
        dto.setErrorMessage("stale executor should not overwrite active run");

        DailyPlanVO vo = service.failDailyPlanRun(USER_ID, 77L, dto);

        assertEquals(AgentRunStatusEnum.RUNNING.name(), vo.getStatus());
        verify(agentRunMapper, never()).update(any(), any());
        verify(agentRunMapper, never()).updateById(any(AgentRun.class));
    }

    @Test
    void completeTaskCreatesRuleReviewAndReturnsSafeSummary() {
        AgentTask before = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        before.setRelatedBizType("QUESTION_PRACTICE");
        before.setRelatedBizId(700L);
        before.setRelatedSkillName("MySQL Index");
        before.setTargetJobId(88L);
        AgentTask after = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        after.setRelatedBizType(before.getRelatedBizType());
        after.setRelatedBizId(before.getRelatedBizId());
        after.setRelatedSkillName(before.getRelatedSkillName());
        after.setTargetJobId(before.getTargetJobId());
        when(agentTaskMapper.selectById(99L)).thenReturn(before).thenReturn(after);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());
        AgentTaskCompleteDTO dto = new AgentTaskCompleteDTO();
        dto.setNote("finished index drill");

        AgentTaskVO vo = service.completeTask(USER_ID, 99L, dto);

        ArgumentCaptor<AgentReview> reviewCaptor = ArgumentCaptor.forClass(AgentReview.class);
        verify(agentReviewMapper).insert(reviewCaptor.capture());
        AgentReview review = reviewCaptor.getValue();
        assertEquals(USER_ID, review.getUserId());
        assertEquals(after.getTargetJobId(), review.getTargetJobId());
        assertEquals(after.getDueDate(), review.getReviewDate());
        assertEquals(after.getAgentRunId(), review.getAgentRunId());
        assertEquals("TASK", review.getReviewType());
        assertEquals(99L, review.getSourceTaskId());
        assertEquals("TASK:10:99", review.getIdempotencyKey());
        assertEquals("JOB:88", review.getTargetScopeKey());
        assertNotNull(review.getSummary());
        assertTrue(review.getReviewJson().contains("\"taskId\":99"));
        assertTrue(review.getReviewJson().contains("\"status\":\"DONE\""));
        assertTrue(review.getReviewJson().contains("\"generatedSource\":\"RULE\""));
        assertTrue(review.getReviewJson().contains("finished index drill"));
        assertFalse(review.getReviewJson().contains("inputSnapshotJson"));
        assertFalse(review.getReviewJson().contains("rawOutputText"));
        assertEquals("RULE", vo.getReviewSource());
        assertEquals("规则复盘", vo.getReviewSourceLabel());
        assertEquals("finished index drill", vo.getReviewNote());
        assertEquals(review.getSummary(), vo.getReviewSummary());
        assertFalse(vo.getReviewNextActions().isEmpty());
        assertEquals(1, vo.getActivationHandoffs().size());
        assertEquals("ACT-001-FIRST-TASK-COMPLETED", vo.getActivationHandoffs().get(0).getCode());
        assertTrue(review.getReviewJson().contains("\"activationHandoffs\""));
    }

    @Test
    void completeTaskAiReviewSuccessMarksSourceAsLlmAndStoresCallLog() {
        AgentTask before = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        AgentTask after = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        when(agentTaskMapper.selectById(99L)).thenReturn(before).thenReturn(after);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());
        RouteResult aiResult = new RouteResult();
        aiResult.setContent("{\"summary\":\"AI reviewed this task against the target job.\",\"nextActions\":[\"Practice one follow-up question.\"]}");
        aiResult.setResultSource("LLM");
        aiResult.setAiCallLogId(888L);
        when(aiCallLogService.callAndLog(any())).thenReturn(aiResult);

        AgentTaskVO vo = service.completeTask(USER_ID, 99L, null);

        ArgumentCaptor<AgentReview> reviewCaptor = ArgumentCaptor.forClass(AgentReview.class);
        verify(agentReviewMapper).insert(reviewCaptor.capture());
        AgentReview review = reviewCaptor.getValue();
        assertEquals(888L, review.getAiCallLogId());
        assertEquals("AI reviewed this task against the target job.", review.getSummary());
        assertTrue(review.getNextActionsJson().contains("Practice one follow-up question."));
        assertTrue(review.getReviewJson().contains("\"generatedSource\":\"LLM\""));
        assertTrue(review.getReviewJson().contains("\"promptVersion\":\"agent-task-review-v1\""));
        assertEquals("LLM", vo.getReviewSource());
        assertEquals("AI复盘", vo.getReviewSourceLabel());
        assertEquals("AI reviewed this task against the target job.", vo.getReviewSummary());
    }

    @Test
    void completeTaskAiReviewFailureKeepsRuleSummaryAndMarksFallback() {
        AgentTask before = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        AgentTask after = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        when(agentTaskMapper.selectById(99L)).thenReturn(before).thenReturn(after);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of());
        when(aiCallLogService.callAndLog(any())).thenThrow(new RuntimeException("provider down"));

        AgentTaskVO vo = service.completeTask(USER_ID, 99L, null);

        ArgumentCaptor<AgentReview> reviewCaptor = ArgumentCaptor.forClass(AgentReview.class);
        verify(agentReviewMapper).insert(reviewCaptor.capture());
        AgentReview review = reviewCaptor.getValue();
        assertEquals(null, review.getAiCallLogId());
        assertTrue(review.getSummary().startsWith("Completed task:"));
        assertTrue(review.getReviewJson().contains("\"generatedSource\":\"FALLBACK\""));
        assertTrue(review.getReviewJson().contains("\"aiFailureReason\":\"RuntimeException\""));
        assertEquals("FALLBACK", vo.getReviewSource());
        assertEquals("规则兜底", vo.getReviewSourceLabel());
        assertEquals(review.getSummary(), vo.getReviewSummary());
    }

    @Test
    void performCoachActionExplainsRecommendationWithStructuredSafeOutput() {
        AgentTask task = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        task.setReason("Build confidence on MySQL indexes before interview follow-up.");
        task.setRelatedSkillName("MySQL Index");
        task.setActionUrl("/questions?skill=mysql");
        when(agentTaskMapper.selectById(99L)).thenReturn(task);
        RouteResult aiResult = new RouteResult();
        aiResult.setContent("""
                {"summary":"This task protects the highest-risk interview gap.","reasons":["It targets MySQL Index.","It is tied to today's plan.","It has a concrete practice entry.","extra reason ignored"],"evidenceRefs":["task.reason","task.relatedSkillName"],"nextAction":"Open the practice set and finish one round."}
                """);
        aiResult.setResultSource("LLM");
        aiResult.setAiCallLogId(888L);
        aiResult.setElapsedMs(321L);
        aiResult.setEstimatedCost(0.012D);
        when(aiCallLogService.callAndLog(any())).thenReturn(aiResult);
        AgentCoachActionDTO dto = new AgentCoachActionDTO();
        dto.setTaskId(99L);
        dto.setActionType("EXPLAIN_RECOMMENDATION");
        dto.setRequestId("req-r3-explain");
        dto.setIdempotencyKey("idem-r3-explain");

        AgentCoachActionVO vo = service.performCoachAction(USER_ID, dto);

        assertEquals("EXPLAIN_RECOMMENDATION", vo.getActionType());
        assertEquals(99L, vo.getTaskId());
        assertEquals("This task protects the highest-risk interview gap.", vo.getSummary());
        assertEquals(3, vo.getReasons().size());
        assertEquals(List.of("task.reason", "task.relatedSkillName"), vo.getEvidenceRefs());
        assertEquals("Open the practice set and finish one round.", vo.getNextAction());
        assertEquals("req-r3-explain", vo.getRequestId());
        assertEquals("idem-r3-explain", vo.getIdempotencyKey());
        assertEquals("LLM", vo.getResultSource());
        assertEquals(888L, vo.getAiCallLogId());
        assertEquals(321L, vo.getLatencyMs());
        assertEquals(0.012D, vo.getEstimatedCost());
        ArgumentCaptor<com.codecoachai.ai.router.AiModelRouter.AiCallContext> ctxCaptor =
                ArgumentCaptor.forClass(com.codecoachai.ai.router.AiModelRouter.AiCallContext.class);
        verify(aiCallLogService).callAndLog(ctxCaptor.capture());
        assertFalse(ctxCaptor.getValue().getPrompt().contains("inputSnapshotJson"));
        assertFalse(ctxCaptor.getValue().getPrompt().contains("rawOutputText"));
        verify(agentMetricsService, atLeastOnce()).acceptEvent(eq(USER_ID), any());
        assertFalse(capturedCoachMetrics.isEmpty());
        AgentMetricEventDTO startedMetric = capturedCoachMetrics.stream()
                .filter(event -> "ai_coach_action_started".equals(event.getEventCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-r3-explain|99",
                startedMetric.getIdempotencyKey());
        assertEquals("idem-r3-explain", vo.getIdempotencyKey());
    }

    @Test
    void performCoachActionUsesDistinctMetricKeysForDifferentStages() {
        AgentTask task = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        task.setReason("Build confidence on MySQL indexes before interview follow-up.");
        task.setRelatedSkillName("MySQL Index");
        task.setActionUrl("/questions?skill=mysql");
        when(agentTaskMapper.selectById(99L)).thenReturn(task);
        RouteResult aiResult = new RouteResult();
        aiResult.setContent("{\"summary\":\"ok\",\"reasons\":[\"a\"],\"evidenceRefs\":[\"task.reason\"],\"nextAction\":\"do it\"}");
        aiResult.setResultSource("LLM");
        aiResult.setAiCallLogId(888L);
        when(aiCallLogService.callAndLog(any())).thenReturn(aiResult);
        AgentCoachActionDTO dto = new AgentCoachActionDTO();
        dto.setTaskId(99L);
        dto.setActionType("EXPLAIN_RECOMMENDATION");
        dto.setRequestId("req-r3-explain-stage");
        dto.setIdempotencyKey("idem-r3-explain-stage");

        service.performCoachAction(USER_ID, dto);

        List<String> keys = capturedCoachMetrics.stream()
                .map(AgentMetricEventDTO::getIdempotencyKey)
                .toList();
        assertTrue(keys.contains("ai-coach|ai_coach_action_started|EXPLAIN_RECOMMENDATION|req-r3-explain-stage|99"));
        assertTrue(keys.contains("ai-coach|ai_coach_action_succeeded|EXPLAIN_RECOMMENDATION|req-r3-explain-stage|99"));
        assertEquals(2, keys.size());
        assertFalse(keys.get(0).equals(keys.get(1)));
    }

    @Test
    void performCoachActionRejectsReviewForUnfinishedTask() {
        when(agentTaskMapper.selectById(99L)).thenReturn(task(99L, USER_ID, AgentTaskStatusEnum.TODO.name()));
        AgentCoachActionDTO dto = new AgentCoachActionDTO();
        dto.setTaskId(99L);
        dto.setActionType("REVIEW_COMPLETED_TASK");
        dto.setRequestId("req-r3-review");

        assertThrows(BusinessException.class, () -> service.performCoachAction(USER_ID, dto));

        verify(aiCallLogService, never()).callAndLog(any());
    }

    @Test
    void performCoachActionReviewsCompletedTaskFromExistingReview() {
        AgentTask task = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        when(agentTaskMapper.selectById(99L)).thenReturn(task);
        AgentReview review = new AgentReview();
        review.setId(200L);
        review.setUserId(USER_ID);
        review.setReviewType("TASK");
        review.setSourceTaskId(99L);
        review.setIdempotencyKey("TASK:10:99");
        review.setSummary("You finished a useful MySQL review.");
        review.setNextActionsJson("[\"Practice one follow-up question.\",\"Write down one mistake.\"]");
        review.setAiCallLogId(777L);
        review.setReviewJson("{\"generatedSource\":\"LLM\",\"promptVersion\":\"agent-task-review-v1\"}");
        when(agentReviewMapper.selectList(any())).thenReturn(List.of(review));
        AgentCoachActionDTO dto = new AgentCoachActionDTO();
        dto.setTaskId(99L);
        dto.setActionType("REVIEW_COMPLETED_TASK");
        dto.setRequestId("req-r3-review");

        AgentCoachActionVO vo = service.performCoachAction(USER_ID, dto);

        assertEquals("REVIEW_COMPLETED_TASK", vo.getActionType());
        assertEquals("You finished a useful MySQL review.", vo.getSummary());
        assertEquals(List.of("Practice one follow-up question.", "Write down one mistake."), vo.getReasons());
        assertEquals("Practice one follow-up question.", vo.getNextAction());
        assertEquals("LLM", vo.getResultSource());
        assertEquals(777L, vo.getAiCallLogId());
        verify(aiCallLogService, never()).callAndLog(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentReview>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(agentReviewMapper).selectList(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("review_type"));
        assertTrue(sqlSegment.contains("source_task_id"));
        assertTrue(sqlSegment.contains("deleted"));
        assertFalse(sqlSegment.contains("review_json"));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue("TASK"));
        assertTrue(wrapperParams(wrapperCaptor.getValue()).containsValue(99L));
    }

    @Test
    void skipTaskUpdatesExistingRuleReviewForSameTaskInsteadOfDuplicating() {
        AgentTask before = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        AgentTask after = task(99L, USER_ID, AgentTaskStatusEnum.SKIPPED.name());
        after.setSkipReason("blocked by missing JD");
        AgentReview existing = new AgentReview();
        existing.setId(200L);
        existing.setUserId(USER_ID);
        existing.setReviewDate(after.getDueDate());
        existing.setAgentRunId(after.getAgentRunId());
        existing.setReviewType("TASK");
        existing.setSourceTaskId(99L);
        existing.setIdempotencyKey("LEGACY:200");
        existing.setReviewJson("{\"taskId\":99,\"generatedSource\":\"RULE\"}");
        when(agentTaskMapper.selectById(99L)).thenReturn(before).thenReturn(after);
        when(agentTaskMapper.update(any(), any())).thenReturn(1);
        when(agentReviewMapper.selectList(any())).thenReturn(List.of(existing));
        AgentTaskSkipDTO dto = new AgentTaskSkipDTO();
        dto.setSkipReason("blocked by missing JD");

        AgentTaskVO vo = service.skipTask(USER_ID, 99L, dto);

        ArgumentCaptor<AgentReview> reviewCaptor = ArgumentCaptor.forClass(AgentReview.class);
        verify(agentReviewMapper).updateById(reviewCaptor.capture());
        verify(agentReviewMapper, never()).insert(any(AgentReview.class));
        AgentReview updated = reviewCaptor.getValue();
        assertEquals(200L, updated.getId());
        assertEquals("TASK", updated.getReviewType());
        assertEquals(99L, updated.getSourceTaskId());
        assertEquals("TASK:10:99", updated.getIdempotencyKey());
        assertTrue(updated.getReviewJson().contains("\"taskId\":99"));
        assertTrue(updated.getReviewJson().contains("\"status\":\"SKIPPED\""));
        assertEquals(200L, vo.getReviewId());
        assertEquals("RULE", vo.getReviewSource());
        assertEquals("blocked by missing JD", vo.getReviewNote());
    }

    @Test
    void getRunDetailRejectsOtherUsersRun() {
        when(agentRunMapper.selectById(77L)).thenReturn(run(77L, 20L));

        assertThrows(BusinessException.class, () -> service.getRunDetail(USER_ID, 77L));

        verify(agentTaskMapper, never()).selectList(any());
    }

    @Test
    void getRunDetailReturnsUserSafeDetailWithoutRawPayloadAccessors() {
        AgentRun run = run(77L, USER_ID);
        run.setTraceId("trace-77");
        run.setAiCallLogId(9001L);
        run.setPromptVersionId(3001L);
        when(agentRunMapper.selectById(77L)).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task(99L, USER_ID, AgentTaskStatusEnum.DONE.name())));

        AgentRunUserDetailVO vo = service.getRunDetail(USER_ID, 77L);

        assertEquals(77L, vo.getId());
        assertEquals(AgentRunStatusEnum.SUCCESS.name(), vo.getStatus());
        assertEquals("trace-77", vo.getTraceId());
        assertEquals(9001L, vo.getAiCallLogId());
        assertEquals(3001L, vo.getPromptVersionId());
        assertEquals(1, vo.getTasks().size());
        List<String> accessors = Arrays.stream(AgentRunUserDetailVO.class.getMethods())
                .map(Method::getName)
                .toList();
        assertFalse(accessors.contains("getInputSnapshotJson"));
        assertFalse(accessors.contains("getOutputJson"));
        assertFalse(accessors.contains("getRawOutputText"));
    }

    @Test
    void todoTaskDoesNotExposeStaleReviewAfterRestore() {
        AgentReview staleReview = new AgentReview();
        staleReview.setId(200L);
        staleReview.setSummary("old skipped review");
        staleReview.setNextActionsJson("[\"old action\"]");
        staleReview.setReviewJson("{\"taskId\":99,\"status\":\"SKIPPED\",\"generatedSource\":\"RULE\"}");
        when(agentRunMapper.selectById(77L)).thenReturn(run(77L, USER_ID));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task(99L, USER_ID, AgentTaskStatusEnum.TODO.name())));

        AgentRunUserDetailVO vo = service.getRunDetail(USER_ID, 77L);

        AgentTaskVO task = vo.getTasks().get(0);
        assertEquals(AgentTaskStatusEnum.TODO.name(), task.getStatus());
        assertEquals(null, task.getReviewId());
        assertEquals(null, task.getReviewSummary());
        assertTrue(task.getReviewNextActions().isEmpty());
    }

    @Test
    void getRunDetailAddsFailureDiagnosisForMissingTargetJob() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.FAILED.name());
        run.setErrorCode(AgentErrorCode.TARGET_JOB_REQUIRED);
        run.setErrorMessage(AgentErrorCode.TARGET_JOB_REQUIRED);
        when(agentRunMapper.selectById(77L)).thenReturn(run);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        AgentRunUserDetailVO vo = service.getRunDetail(USER_ID, 77L);

        assertEquals("FIX_TARGET_JOB", vo.getFailureAction());
        assertEquals("去创建目标岗位", vo.getFailureActionLabel());
        assertEquals("请先补充目标岗位，再重新生成今日计划。", vo.getFailureSuggestion());
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    private static Map<String, Object> wrapperParams(Wrapper<?> wrapper) {
        assertTrue(wrapper instanceof AbstractWrapper<?, ?, ?>);
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    private PracticeRecordEvidenceVO practiceEvidence(Long id, Long userId, String sourceType, Long sourceId) {
        PracticeRecordEvidenceVO evidence = new PracticeRecordEvidenceVO();
        evidence.setId(id);
        evidence.setUserId(userId);
        evidence.setSourceType(sourceType);
        evidence.setSourceId(sourceId);
        return evidence;
    }

    private JobApplicationEventEvidenceVO applicationEventEvidence(Long id, Long userId, Long applicationId) {
        return applicationEventEvidence(id, userId, applicationId, null);
    }

    private JobApplicationEventEvidenceVO applicationEventEvidence(Long id, Long userId, Long applicationId,
                                                                   String eventType) {
        return applicationEventEvidence(id, userId, applicationId, eventType, null);
    }

    private JobApplicationEventEvidenceVO applicationEventEvidence(Long id, Long userId, Long applicationId,
                                                                   String eventType, LocalDateTime eventTime) {
        JobApplicationEventEvidenceVO evidence = new JobApplicationEventEvidenceVO();
        evidence.setId(id);
        evidence.setUserId(userId);
        evidence.setApplicationId(applicationId);
        evidence.setEventType(eventType);
        evidence.setEventTime(eventTime);
        return evidence;
    }

    private InterviewReportEvidenceVO interviewReportEvidence(Long id, Long userId, Long sessionId,
                                                              Long targetJobId, String status) {
        InterviewReportEvidenceVO evidence = new InterviewReportEvidenceVO();
        evidence.setId(id);
        evidence.setUserId(userId);
        evidence.setSessionId(sessionId);
        evidence.setTargetJobId(targetJobId);
        evidence.setStatus(status);
        return evidence;
    }

    private ResumeOptimizeRecordEvidenceVO resumeOptimizeEvidence(Long id, Long userId, Long resumeId,
                                                                  Long targetJobId, String status) {
        ResumeOptimizeRecordEvidenceVO evidence = new ResumeOptimizeRecordEvidenceVO();
        evidence.setId(id);
        evidence.setUserId(userId);
        evidence.setResumeId(resumeId);
        evidence.setTargetJobId(targetJobId);
        evidence.setStatus(status);
        evidence.setOptimizedAt(LocalDateTime.now());
        return evidence;
    }

    private AgentRun run(Long id, Long userId) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setUserId(userId);
        run.setAgentType("JOB_COACH");
        run.setPlanDate(LocalDate.now());
        run.setStatus(AgentRunStatusEnum.SUCCESS.name());
        run.setExecutionToken("run-token-default");
        run.setInputSnapshotJson("{\"resume\":\"private\"}");
        run.setOutputJson("{\"summary\":\"今日计划\",\"tasks\":[]}");
        run.setRawOutputText("private raw output");
        return run;
    }

    private AgentTask task(Long id, Long userId, String status) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setUserId(userId);
        task.setAgentRunId(77L);
        task.setTitle("复盘 MySQL 索引题");
        task.setTaskType("QUESTION_REVIEW");
        task.setStatus(status);
        task.setDueDate(LocalDate.now());
        task.setSortOrder(1);
        return task;
    }

    private CandidateTask candidate(String candidateId, String relatedBizType, Long relatedBizId, String actionUrl) {
        CandidateTask candidate = new CandidateTask();
        candidate.setCandidateId(candidateId);
        candidate.setType("QUESTION_PRACTICE");
        candidate.setTitle("完成一组题目练习");
        candidate.setDescription("Practice target job questions");
        candidate.setReason("补齐目标岗位短板");
        candidate.setPriority("HIGH");
        candidate.setEstimatedMinutes(30);
        candidate.setRelatedSkillCode("mysql");
        candidate.setRelatedSkillName("MySQL");
        candidate.setRelatedBizType(relatedBizType);
        candidate.setRelatedBizId(relatedBizId);
        candidate.setActionUrl(actionUrl);
        return candidate;
    }
}
