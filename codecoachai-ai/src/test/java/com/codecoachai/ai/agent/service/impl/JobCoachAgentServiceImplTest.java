package com.codecoachai.ai.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.ai.agent.domain.context.CandidateTask;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult.PlanTask;
import com.codecoachai.ai.agent.domain.context.JobCoachAgentContext;
import com.codecoachai.ai.agent.domain.dto.AgentBusinessActionCompleteDTO;
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
import com.codecoachai.ai.agent.service.AgentOutputParser;
import com.codecoachai.ai.agent.service.AgentOutputValidator;
import com.codecoachai.ai.agent.service.AgentPromptBuilder;
import com.codecoachai.ai.agent.service.CandidateTaskBuilder;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
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
                aiCallLogService,
                questionPracticeEvidenceFeignClient,
                resumeJobApplicationEvidenceFeignClient,
                interviewReportEvidenceFeignClient,
                resumeOptimizeRecordEvidenceFeignClient,
                new ObjectMapper().findAndRegisterModules(),
                agentMqDispatcher,
                transactionTemplate);
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
        when(agentRunMapper.selectById(77L)).thenReturn(running);
        when(agentRunMapper.update(any(), any())).thenReturn(1);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of());

        AgentRunFailureDTO dto = new AgentRunFailureDTO();
        dto.setUserId(USER_ID);
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
        assertTrue(sqlSegment.contains("2026-06-01"));
        assertTrue(sqlSegment.contains("2026-06-18"));
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
        assertTrue(sqlSegment.contains("MANUAL"));
        assertTrue(sqlSegment.contains("plan_date"));
        assertTrue(sqlSegment.contains("2026-06-01"));
        assertTrue(sqlSegment.contains("2026-06-18"));
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
        assertTrue(sqlSegment.contains("2026-06-01"));
        assertTrue(sqlSegment.contains("2026-06-18"));
    }

    @Test
    void executeDailyPlanStoresCandidateBusinessBindingWhenAiTaskIsTampered() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
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

        service.executeDailyPlan(USER_ID, 77L, new DailyPlanGenerateDTO());

        ArgumentCaptor<AgentTask> taskCaptor = ArgumentCaptor.forClass(AgentTask.class);
        verify(agentTaskMapper).insert(taskCaptor.capture());
        AgentTask saved = taskCaptor.getValue();
        assertEquals("TARGET_JOB", saved.getRelatedBizType());
        assertEquals(501L, saved.getRelatedBizId());
        assertEquals("/questions/practice?mode=category", saved.getActionUrl());
    }

    @Test
    void executeDailyPlanDoesNotClearOrInsertTasksWhenSuccessTransitionLosesRace() {
        AgentRun run = run(77L, USER_ID);
        run.setStatus(AgentRunStatusEnum.RUNNING.name());
        run.setTargetJobId(501L);
        AgentRun latest = run(77L, USER_ID);
        latest.setStatus(AgentRunStatusEnum.SUCCESS.name());
        latest.setTargetJobId(501L);
        when(agentRunMapper.selectById(77L)).thenReturn(run).thenReturn(run).thenReturn(latest);
        when(agentRunMapper.update(any(), any())).thenReturn(1).thenReturn(0);
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

        DailyPlanVO vo = service.executeDailyPlan(USER_ID, 77L, new DailyPlanGenerateDTO());

        assertEquals(AgentRunStatusEnum.SUCCESS.name(), vo.getStatus());
        verify(agentTaskMapper, never()).update(any(), any());
        verify(agentTaskMapper, never()).insert(any(AgentTask.class));
    }

    @Test
    void completeTaskCreatesRuleReviewAndReturnsSafeSummary() {
        AgentTask before = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        before.setRelatedBizType("QUESTION_PRACTICE");
        before.setRelatedBizId(700L);
        before.setRelatedSkillName("MySQL Index");
        AgentTask after = task(99L, USER_ID, AgentTaskStatusEnum.DONE.name());
        after.setRelatedBizType(before.getRelatedBizType());
        after.setRelatedBizId(before.getRelatedBizId());
        after.setRelatedSkillName(before.getRelatedSkillName());
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
    void skipTaskUpdatesExistingRuleReviewForSameTaskInsteadOfDuplicating() {
        AgentTask before = task(99L, USER_ID, AgentTaskStatusEnum.TODO.name());
        AgentTask after = task(99L, USER_ID, AgentTaskStatusEnum.SKIPPED.name());
        after.setSkipReason("blocked by missing JD");
        AgentReview existing = new AgentReview();
        existing.setId(200L);
        existing.setUserId(USER_ID);
        existing.setReviewDate(after.getDueDate());
        existing.setAgentRunId(after.getAgentRunId());
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
        when(agentRunMapper.selectById(77L)).thenReturn(run(77L, USER_ID));
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(task(99L, USER_ID, AgentTaskStatusEnum.DONE.name())));

        AgentRunUserDetailVO vo = service.getRunDetail(USER_ID, 77L);

        assertEquals(77L, vo.getId());
        assertEquals(AgentRunStatusEnum.SUCCESS.name(), vo.getStatus());
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
