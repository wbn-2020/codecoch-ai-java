package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.JobApplicationEventSaveDTO;
import com.codecoachai.resume.domain.dto.JobApplicationSaveDTO;
import com.codecoachai.resume.domain.dto.ResumeVersionCopyDTO;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.domain.vo.ApplicationCareerInsightSummaryVO;
import com.codecoachai.resume.domain.vo.CareerInsightItemVO;
import com.codecoachai.resume.domain.vo.ApplicationReminderCandidateVO;
import com.codecoachai.resume.domain.vo.JobApplicationAgentContextVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationStatsVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.domain.vo.ResumeVersionEffectItemVO;
import com.codecoachai.resume.experimentv2.ExperimentV2ApplicationAutoAssignmentService;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionAdoptionMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class V4ResumeCareerServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper resumeProjectMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private ResumeJobMatchReportMapper resumeJobMatchReportMapper;
    @Mock
    private ResumeSuggestionAdoptionMapper resumeSuggestionAdoptionMapper;
    @Mock
    private JobApplicationEventMapper jobApplicationEventMapper;
    @Mock
    private CareerCalendarEventMapper careerCalendarEventMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;
    @Mock
    private NotificationBusinessResolver notificationBusinessResolver;
    @Mock
    private ResumeSearchSyncOutboxService resumeSearchSyncOutboxService;
    @Mock
    private ExperimentV2ApplicationAutoAssignmentService experimentAutoAssignmentService;

    private V4ResumeCareerServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(ResumeVersion.class);
        initTableInfo(ResumeProject.class);
        initTableInfo(ResumeJobMatchReport.class);
        initTableInfo(JobApplication.class);
        initTableInfo(JobApplicationEvent.class);
        initTableInfo(CareerCalendarEvent.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("phase7-user")
                .build());
        service = new V4ResumeCareerServiceImpl(
                resumeMapper,
                resumeProjectMapper,
                resumeVersionMapper,
                jobApplicationMapper,
                resumeJobMatchReportMapper,
                resumeSuggestionAdoptionMapper,
                jobApplicationEventMapper,
                careerCalendarEventMapper,
                targetJobMapper,
                agentBusinessActionNotifier,
                notificationBusinessResolver,
                resumeSearchSyncOutboxService,
                experimentAutoAssignmentService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getVersionRejectsOtherUsersVersion() {
        ResumeVersion version = resumeVersion(99L, 20L, 1L);
        when(resumeVersionMapper.selectById(99L)).thenReturn(version);

        assertThrows(BusinessException.class, () -> service.getVersion(99L));
    }

    @Test
    void copyVersionRejectsVersionFromDifferentResume() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, USER_ID));
        when(resumeVersionMapper.selectById(2L)).thenReturn(resumeVersion(2L, USER_ID, 99L));

        assertThrows(BusinessException.class, () -> service.copyVersion(1L, 2L, new ResumeVersionCopyDTO()));
        verify(resumeVersionMapper, never()).insert(any(ResumeVersion.class));
    }

    @Test
    void createApplicationRejectsResumeVersionOwnedByOtherUser() {
        JobApplicationSaveDTO dto = new JobApplicationSaveDTO();
        dto.setResumeVersionId(77L);
        when(resumeVersionMapper.selectById(77L)).thenReturn(resumeVersion(77L, 20L, 1L));

        assertThrows(BusinessException.class, () -> service.createApplication(dto));
        verify(jobApplicationMapper, never()).insert(any(JobApplication.class));
    }

    @Test
    void createApplicationStoresOwnerAndDefaults() {
        JobApplicationSaveDTO dto = new JobApplicationSaveDTO();
        dto.setCompanyName("测试科技");

        service.createApplication(dto);

        ArgumentCaptor<JobApplication> appCaptor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationMapper).insert(appCaptor.capture());
        JobApplication app = appCaptor.getValue();
        assertEquals(USER_ID, app.getUserId());
        assertEquals("测试科技", app.getCompanyName());
        assertEquals("Untitled Job", app.getJobTitle());
        assertEquals("SAVED", app.getStatus());
    }

    @Test
    void createApplicationAutoAssignsOnlyAfterCommit() {
        when(jobApplicationMapper.insert(any(JobApplication.class))).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            application.setId(41L);
            return 1;
        });
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createApplication(new JobApplicationSaveDTO());

            verify(experimentAutoAssignmentService, never()).autoAssign(any(JobApplication.class));
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(experimentAutoAssignmentService).autoAssign(any(JobApplication.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createApplicationNormalizesStatus() {
        JobApplicationSaveDTO dto = new JobApplicationSaveDTO();
        dto.setStatus("applied");

        service.createApplication(dto);

        ArgumentCaptor<JobApplication> appCaptor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationMapper).insert(appCaptor.capture());
        assertEquals("APPLIED", appCaptor.getValue().getStatus());
    }

    @Test
    void createApplicationFromMatchReportStoresReportTargetAndVersion() {
        JobApplicationSaveDTO dto = new JobApplicationSaveDTO();
        dto.setMatchReportId(99L);
        dto.setCompanyName("测试科技");
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(matchReport(99L));
        when(resumeVersionMapper.selectById(77L)).thenReturn(resumeVersion(77L, USER_ID, 1L));
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob(88L, USER_ID));

        service.createApplication(dto);

        ArgumentCaptor<JobApplication> appCaptor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationMapper).insert(appCaptor.capture());
        JobApplication app = appCaptor.getValue();
        assertEquals(USER_ID, app.getUserId());
        assertEquals(99L, app.getMatchReportId());
        assertEquals(88L, app.getTargetJobId());
        assertEquals(77L, app.getResumeVersionId());
        assertEquals("测试科技", app.getCompanyName());
        assertEquals("SAVED", app.getStatus());
    }

    @Test
    void createApplicationFromMatchReportReturnsExistingApplication() {
        JobApplicationSaveDTO dto = new JobApplicationSaveDTO();
        dto.setMatchReportId(99L);
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(matchReport(99L));
        when(resumeVersionMapper.selectById(77L)).thenReturn(resumeVersion(77L, USER_ID, 1L));
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob(88L, USER_ID));
        JobApplication existing = application(188L, USER_ID);
        existing.setMatchReportId(99L);
        when(jobApplicationMapper.selectOne(any())).thenReturn(existing);

        JobApplicationVO result = service.createApplication(dto);

        assertEquals(188L, result.getId());
        assertEquals(99L, result.getMatchReportId());
        verify(jobApplicationMapper, never()).insert(any(JobApplication.class));
    }

    @Test
    void listApplicationsReturnsResumeVersionAndLatestEventSummary() {
        JobApplication app = application(188L, USER_ID);
        app.setResumeVersionId(77L);
        ResumeVersion version = resumeVersion(77L, USER_ID, 1L);
        version.setVersionNo(3);
        version.setVersionName("后端投递版");
        version.setCurrentFlag(1);
        JobApplicationEvent older = event(701L, 188L, "FOLLOW_UP", LocalDateTime.of(2026, 6, 15, 10, 0));
        older.setSummary("已发邮件");
        JobApplicationEvent latest = event(702L, 188L, "INTERVIEW", LocalDateTime.of(2026, 6, 16, 10, 0));
        latest.setSummary("约定技术面");
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(app));
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(version));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of(latest, older));

        List<JobApplicationVO> result = service.listApplications(null);

        assertEquals(1, result.size());
        JobApplicationVO vo = result.get(0);
        assertEquals(1L, vo.getResumeId());
        assertEquals(3, vo.getResumeVersionNo());
        assertEquals("后端投递版", vo.getResumeVersionName());
        assertEquals(1, vo.getResumeVersionCurrentFlag());
        assertEquals(702L, vo.getLatestEventId());
        assertEquals("INTERVIEW", vo.getLatestEventType());
        assertEquals(LocalDateTime.of(2026, 6, 16, 10, 0), vo.getLatestEventTime());
        assertEquals("约定技术面", vo.getLatestEventSummary());
    }

    @Test
    void updateApplicationRejectsMatchReportAlreadyLinkedToAnotherApplication() {
        JobApplication current = application(55L, USER_ID);
        when(jobApplicationMapper.selectById(55L)).thenReturn(current);
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(matchReport(99L));
        when(resumeVersionMapper.selectById(77L)).thenReturn(resumeVersion(77L, USER_ID, 1L));
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob(88L, USER_ID));
        JobApplication linked = application(188L, USER_ID);
        linked.setMatchReportId(99L);
        when(jobApplicationMapper.selectOne(any())).thenReturn(linked);
        JobApplicationSaveDTO dto = new JobApplicationSaveDTO();
        dto.setMatchReportId(99L);

        assertThrows(BusinessException.class, () -> service.updateApplication(55L, dto));

        verify(jobApplicationMapper, never()).updateById(any(JobApplication.class));
    }

    @Test
    void createApplicationEventRejectsOtherUsersApplication() {
        when(jobApplicationMapper.selectById(88L)).thenReturn(application(88L, 20L));

        assertThrows(BusinessException.class, () -> service.createApplicationEvent(88L, new JobApplicationEventSaveDTO()));
        verify(jobApplicationEventMapper, never()).insert(any(JobApplicationEvent.class));
    }

    @Test
    void createApplicationEventUsesOwnedApplicationAndDefaults() {
        when(jobApplicationMapper.selectById(88L)).thenReturn(application(88L, USER_ID));

        service.createApplicationEvent(88L, null);

        ArgumentCaptor<JobApplicationEvent> eventCaptor = ArgumentCaptor.forClass(JobApplicationEvent.class);
        verify(jobApplicationEventMapper).insert(eventCaptor.capture());
        JobApplicationEvent event = eventCaptor.getValue();
        assertEquals(USER_ID, event.getUserId());
        assertEquals(88L, event.getApplicationId());
        assertEquals("NOTE", event.getEventType());
        assertNotNull(event.getEventTime());
    }

    @Test
    void createApplicationEventCompletesAgentFollowUpTaskWithEventEvidence() {
        when(jobApplicationMapper.selectById(88L)).thenReturn(application(88L, USER_ID));
        when(jobApplicationEventMapper.insert(any(JobApplicationEvent.class))).thenAnswer(invocation -> {
            JobApplicationEvent event = invocation.getArgument(0);
            event.setId(701L);
            return 1;
        });
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW");

        service.createApplicationEvent(88L, dto);

        verify(agentBusinessActionNotifier).completeApplicationFollowUp(USER_ID, 88L, 701L);
    }

    @Test
    void createApplicationEventReturnsExistingInterviewCompletedForSameReportId() {
        when(jobApplicationMapper.selectById(88L)).thenReturn(application(88L, USER_ID));
        JobApplicationEvent existing = event(900L, 88L, "INTERVIEW_COMPLETED",
                LocalDateTime.of(2026, 6, 20, 10, 0));
        existing.setReviewJson("{\"reportId\":55,\"interviewId\":44}");
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of(existing));
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW_COMPLETED");
        dto.setReview(Map.of("reportId", 55L));

        JobApplicationEventVO result = service.createApplicationEvent(88L, dto);

        assertEquals(900L, result.getId());
        verify(jobApplicationEventMapper, never()).insert(any(JobApplicationEvent.class));
        verify(jobApplicationMapper, never()).updateById(any(JobApplication.class));
    }

    @Test
    void createApplicationEventDetectsInterviewCompletedEvidenceFromTopLevelReviewJson() {
        when(jobApplicationMapper.selectById(88L)).thenReturn(application(88L, USER_ID));
        JobApplicationEvent existing = event(901L, 88L, "INTERVIEW_COMPLETED",
                LocalDateTime.of(2026, 6, 20, 10, 0));
        existing.setReviewJson("{\"interviewId\":44}");
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of(existing));
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW_COMPLETED");
        dto.setReviewJson("{\"interviewId\":44}");

        JobApplicationEventVO result = service.createApplicationEvent(88L, dto);

        assertEquals(901L, result.getId());
        verify(jobApplicationEventMapper, never()).insert(any(JobApplicationEvent.class));
    }

    @Test
    void createApplicationEventDoesNotCompleteAgentFollowUpForNoteEvent() {
        when(jobApplicationMapper.selectById(88L)).thenReturn(application(88L, USER_ID));
        when(jobApplicationEventMapper.insert(any(JobApplicationEvent.class))).thenAnswer(invocation -> {
            JobApplicationEvent event = invocation.getArgument(0);
            event.setId(701L);
            return 1;
        });

        service.createApplicationEvent(88L, null);

        verify(jobApplicationEventMapper).insert(any(JobApplicationEvent.class));
        verify(agentBusinessActionNotifier, never()).completeApplicationFollowUp(any(), any(), any());
    }

    @Test
    void createApplicationEventSyncsForwardStatus() {
        JobApplication app = application(88L, USER_ID);
        app.setStatus("APPLIED");
        when(jobApplicationMapper.selectById(88L)).thenReturn(app);
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW");

        service.createApplicationEvent(88L, dto);

        ArgumentCaptor<JobApplication> appCaptor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationMapper).updateById(appCaptor.capture());
        assertEquals("INTERVIEWING", appCaptor.getValue().getStatus());
    }

    @Test
    void createApplicationEventDoesNotRegressTerminalStatus() {
        JobApplication app = application(88L, USER_ID);
        app.setStatus("REJECTED");
        when(jobApplicationMapper.selectById(88L)).thenReturn(app);
        JobApplicationEventSaveDTO dto = new JobApplicationEventSaveDTO();
        dto.setEventType("INTERVIEW");

        service.createApplicationEvent(88L, dto);

        verify(jobApplicationEventMapper).insert(any(JobApplicationEvent.class));
        verify(jobApplicationMapper, never()).updateById(any(JobApplication.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAgentApplicationContextForUserReturnsActiveApplicationsForTarget() {
        Long targetJobId = 66L;
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDateTime nextFollowUpAt = LocalDateTime.of(2026, 6, 16, 10, 0);
        JobApplication app = application(188L, USER_ID);
        app.setTargetJobId(targetJobId);
        app.setResumeVersionId(77L);
        app.setMatchReportId(99L);
        app.setCompanyName("CodeCoach");
        app.setJobTitle("Java Backend Engineer");
        app.setSource("BOSS");
        app.setStatus("APPLIED");
        app.setAppliedAt(LocalDateTime.of(2026, 6, 15, 9, 30));
        app.setNextFollowUpAt(nextFollowUpAt);
        app.setNote("Send portfolio");
        app.setCreatedAt(LocalDateTime.of(2026, 6, 14, 8, 0));
        app.setUpdatedAt(LocalDateTime.of(2026, 6, 15, 20, 0));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(app));

        List<JobApplicationAgentContextVO> result =
                service.listAgentApplicationContextForUser(USER_ID, targetJobId, now);

        assertEquals(1, result.size());
        JobApplicationAgentContextVO vo = result.get(0);
        assertEquals(188L, vo.getId());
        assertEquals(targetJobId, vo.getTargetJobId());
        assertEquals(77L, vo.getResumeVersionId());
        assertEquals(99L, vo.getMatchReportId());
        assertEquals("CodeCoach", vo.getCompanyName());
        assertEquals("Java Backend Engineer", vo.getJobTitle());
        assertEquals("BOSS", vo.getSource());
        assertEquals("APPLIED", vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 30), vo.getAppliedAt());
        assertEquals(nextFollowUpAt, vo.getNextFollowUpAt());
        assertFalse(vo.getFollowUpOverdue());
        assertTrue(vo.getFollowUpDueToday());
        assertEquals(0L, vo.getDaysUntilFollowUp());
        assertEquals("Send portfolio", vo.getNote());
        assertEquals(LocalDateTime.of(2026, 6, 14, 8, 0), vo.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 6, 15, 20, 0), vo.getUpdatedAt());

        ArgumentCaptor<LambdaQueryWrapper<JobApplication>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(jobApplicationMapper).selectList(queryCaptor.capture());
        assertAgentContextQuery(queryCaptor.getValue(), true);
    }

    @Test
    void listAgentApplicationContextForUserReturnsResumeVersionAndLatestEventSummary() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        JobApplication app = application(188L, USER_ID);
        app.setResumeVersionId(77L);
        JobApplicationEvent latest = event(702L, 188L, "INTERVIEW", LocalDateTime.of(2026, 6, 16, 10, 0));
        latest.setSummary("约定技术面");
        ResumeVersion version = resumeVersion(77L, USER_ID, 1L);
        version.setVersionName("后端投递版");
        version.setVersionNo(3);
        version.setCurrentFlag(1);
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(app));
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(version));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of(latest));

        List<JobApplicationAgentContextVO> result =
                service.listAgentApplicationContextForUser(USER_ID, null, now);

        assertEquals(1, result.size());
        JobApplicationAgentContextVO vo = result.get(0);
        assertEquals(1L, vo.getResumeId());
        assertEquals(3, vo.getResumeVersionNo());
        assertEquals("后端投递版", vo.getResumeVersionName());
        assertEquals(1, vo.getResumeVersionCurrentFlag());
        assertEquals(702L, vo.getLatestEventId());
        assertEquals("INTERVIEW", vo.getLatestEventType());
        assertEquals("约定技术面", vo.getLatestEventSummary());
    }

    @Test
    void listAgentApplicationContextForUserComputesOverdueFollowUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        JobApplication app = application(189L, USER_ID);
        app.setNextFollowUpAt(LocalDateTime.of(2026, 6, 15, 18, 0));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(app));

        List<JobApplicationAgentContextVO> result =
                service.listAgentApplicationContextForUser(USER_ID, null, now);

        assertEquals(1, result.size());
        JobApplicationAgentContextVO vo = result.get(0);
        assertTrue(vo.getFollowUpOverdue());
        assertFalse(vo.getFollowUpDueToday());
        assertEquals(-1L, vo.getDaysUntilFollowUp());
    }

    @Test
    void getApplicationStatsAggregatesOperationalCounts() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        JobApplication savedNoFollowUp = application(201L, USER_ID);
        savedNoFollowUp.setStatus("SAVED");
        savedNoFollowUp.setNextFollowUpAt(null);
        savedNoFollowUp.setUpdatedAt(now.minusDays(15));
        JobApplication appliedOverdue = application(202L, USER_ID);
        appliedOverdue.setStatus("APPLIED");
        appliedOverdue.setNextFollowUpAt(now.minusHours(1));
        appliedOverdue.setUpdatedAt(now.minusDays(2));
        JobApplication interviewingDueToday = application(203L, USER_ID);
        interviewingDueToday.setStatus("INTERVIEWING");
        interviewingDueToday.setNextFollowUpAt(now.plusHours(2));
        interviewingDueToday.setUpdatedAt(now.minusDays(14));
        JobApplication offerTomorrow = application(204L, USER_ID);
        offerTomorrow.setStatus("OFFER");
        offerTomorrow.setNextFollowUpAt(now.plusDays(1));
        offerTomorrow.setUpdatedAt(now.minusDays(20));
        JobApplication rejected = application(205L, USER_ID);
        rejected.setStatus("REJECTED");
        rejected.setNextFollowUpAt(now.minusDays(1));
        rejected.setUpdatedAt(now.minusDays(30));
        JobApplication closed = application(206L, USER_ID);
        closed.setStatus("CLOSED");
        JobApplication preparingFuture = application(207L, USER_ID);
        preparingFuture.setStatus("PREPARING");
        preparingFuture.setNextFollowUpAt(now.plusDays(3));
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(
                savedNoFollowUp, appliedOverdue, interviewingDueToday, offerTomorrow,
                rejected, closed, preparingFuture));

        JobApplicationStatsVO stats = service.getApplicationStats(now);

        assertEquals(7L, stats.getTotal());
        assertEquals(5L, stats.getActiveCount());
        assertEquals(1L, stats.getOverdueFollowUpCount());
        assertEquals(1L, stats.getDueTodayFollowUpCount());
        assertEquals(1L, stats.getNoFollowUpCount());
        assertEquals(2L, stats.getStaleActiveCount());
        assertEquals(1L, stats.getInterviewCount());
        assertEquals(1L, stats.getOfferCount());
        assertEquals(1L, stats.getRejectedCount());
        assertEquals(1L, stats.getClosedCount());
        assertEquals(1L, stats.getStatusCounts().get("SAVED"));
        assertEquals(1L, stats.getStatusCounts().get("PREPARING"));
        assertEquals(1L, stats.getStatusCounts().get("APPLIED"));
        assertEquals(1L, stats.getStatusCounts().get("INTERVIEWING"));
        assertEquals(1L, stats.getStatusCounts().get("OFFER"));
        assertEquals(1L, stats.getStatusCounts().get("REJECTED"));
        assertEquals(1L, stats.getStatusCounts().get("CLOSED"));
        assertEquals(now, stats.getGeneratedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getApplicationStatsQueriesCurrentUserNonDeletedApplications() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of());

        service.getApplicationStats(now);

        ArgumentCaptor<LambdaQueryWrapper<JobApplication>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(jobApplicationMapper).selectList(queryCaptor.capture());
        String sql = (queryCaptor.getValue().getSqlSegment() + " " + queryCaptor.getValue().getTargetSql())
                .replaceAll("\\s+", " ")
                .toLowerCase();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.contains("deleted"), sql);
        assertTrue(queryCaptor.getValue().getParamNameValuePairs().containsValue(USER_ID));
        assertTrue(queryCaptor.getValue().getParamNameValuePairs().containsValue(0));
    }

    @Test
    void getApplicationCareerInsightSummaryAggregatesStatusEventsAndQualityWarnings() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0);
        JobApplication appliedWithFollowUpEvent = application(401L, USER_ID);
        appliedWithFollowUpEvent.setStatus("APPLIED");
        appliedWithFollowUpEvent.setResumeVersionId(77L);
        appliedWithFollowUpEvent.setAppliedAt(now.minusDays(5));
        appliedWithFollowUpEvent.setUpdatedAt(now.minusDays(5));
        JobApplication interviewByStatus = application(402L, USER_ID);
        interviewByStatus.setStatus("INTERVIEWING");
        interviewByStatus.setResumeVersionId(78L);
        interviewByStatus.setAppliedAt(now.minusDays(4));
        interviewByStatus.setNextFollowUpAt(now.minusHours(2));
        interviewByStatus.setUpdatedAt(now.minusDays(9));
        JobApplication offerByEvent = application(403L, USER_ID);
        offerByEvent.setStatus("APPLIED");
        offerByEvent.setResumeVersionId(77L);
        offerByEvent.setAppliedAt(now.minusDays(3));
        offerByEvent.setUpdatedAt(now.minusDays(2));
        JobApplication rejectedByEventWithoutVersion = application(404L, USER_ID);
        rejectedByEventWithoutVersion.setStatus("APPLIED");
        rejectedByEventWithoutVersion.setAppliedAt(now.minusDays(2));
        rejectedByEventWithoutVersion.setUpdatedAt(now.minusDays(1));
        JobApplication noEventStaleWithoutVersion = application(405L, USER_ID);
        noEventStaleWithoutVersion.setStatus("APPLIED");
        noEventStaleWithoutVersion.setAppliedAt(now.minusDays(20));
        noEventStaleWithoutVersion.setUpdatedAt(now.minusDays(10));
        when(jobApplicationMapper.selectInsightRange(eq(USER_ID), any(), eq(now))).thenReturn(List.of(
                appliedWithFollowUpEvent, interviewByStatus, offerByEvent,
                rejectedByEventWithoutVersion, noEventStaleWithoutVersion));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of(
                event(801L, 401L, "FOLLOW_UP", now.minusDays(4)),
                event(802L, 403L, "OFFER", now.minusDays(2)),
                event(803L, 404L, "REJECTED", now.minusDays(1))));
        ResumeVersion currentVersion = resumeVersion(77L, USER_ID, 1L);
        currentVersion.setVersionNo(2);
        currentVersion.setVersionName("current backend");
        currentVersion.setCurrentFlag(1);
        ResumeVersion olderVersion = resumeVersion(78L, USER_ID, 1L);
        olderVersion.setVersionNo(1);
        olderVersion.setVersionName("old backend");
        olderVersion.setCurrentFlag(0);
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(currentVersion, olderVersion));

        ApplicationCareerInsightSummaryVO summary =
                service.getApplicationCareerInsightSummaryForUser(USER_ID, 30, now);

        assertEquals(30, summary.getRangeDays());
        assertEquals(now, summary.getGeneratedAt());
        assertEquals(5L, summary.getApplicationCount());
        assertEquals(2L, summary.getFollowedUpApplicationCount());
        assertEquals(1L, summary.getInterviewApplicationCount());
        assertEquals(1L, summary.getOfferApplicationCount());
        assertEquals(1L, summary.getRejectedOrClosedApplicationCount());
        assertEquals(2L, summary.getQuality().getWithFollowUpCount());
        assertEquals(3L, summary.getQuality().getWithResumeVersionCount());
        assertEquals(2L, summary.getQuality().getWithFollowUpCount());
        assertEquals(1L, summary.getQuality().getOverdueFollowUpCount());
        assertEquals(2L, summary.getQuality().getStaleApplicationCount());
        assertEquals(2L, summary.getQuality().getNoEventApplicationCount());
        assertEquals(0.6D, summary.getQuality().getResumeVersionCoverageRate());
        assertEquals(0.4D, summary.getQuality().getFollowUpCoverageRate());
        assertTrue(summary.getQuality().getWarnings().stream()
                .map(CareerInsightItemVO::getType)
                .anyMatch("OVERDUE_FOLLOW_UP"::equals));
        assertTrue(summary.getQuality().getWarnings().stream()
                .map(CareerInsightItemVO::getType)
                .anyMatch("STALE_APPLICATION"::equals));
    }

    @Test
    void getApplicationCareerInsightSummaryUsesNaturalDayBucketsForRange() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0);
        JobApplication boundaryDayApplication = application(431L, USER_ID);
        boundaryDayApplication.setAppliedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        boundaryDayApplication.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        JobApplication previousDayApplication = application(432L, USER_ID);
        previousDayApplication.setAppliedAt(LocalDateTime.of(2026, 5, 31, 23, 59));
        previousDayApplication.setUpdatedAt(LocalDateTime.of(2026, 5, 31, 23, 59));
        when(jobApplicationMapper.selectInsightRange(eq(USER_ID), any(), eq(now)))
                .thenReturn(List.of(boundaryDayApplication));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of());

        ApplicationCareerInsightSummaryVO summary =
                service.getApplicationCareerInsightSummaryForUser(USER_ID, 8, now);

        assertEquals(30, summary.getRangeDays());
        assertEquals(1L, summary.getApplicationCount());
        assertEquals(431L, summary.getApplications().get(0).getApplicationId());
    }

    @Test
    void getApplicationCareerInsightSummaryWarnsWhenSampleIsTooSmall() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0);
        JobApplication onlyApplication = application(411L, USER_ID);
        onlyApplication.setResumeVersionId(77L);
        onlyApplication.setAppliedAt(now.minusDays(1));
        when(jobApplicationMapper.selectInsightRange(eq(USER_ID), any(), eq(now)))
                .thenReturn(List.of(onlyApplication));
        ResumeVersion version = resumeVersion(77L, USER_ID, 1L);
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(version));

        ApplicationCareerInsightSummaryVO summary =
                service.getApplicationCareerInsightSummaryForUser(USER_ID, 30, now);

        assertEquals(1L, summary.getApplicationCount());
        assertTrue(summary.getQuality().getWarnings().stream()
                .map(CareerInsightItemVO::getType)
                .anyMatch("LOW_SAMPLE"::equals));
        assertEquals("LOW", summary.getResumeVersionEffect().getVersions().get(0).getSampleLevel());
        assertEquals("样本不足", summary.getResumeVersionEffect().getVersions().get(0).getInsightLabel());
    }

    @Test
    void getApplicationCareerInsightSummaryAggregatesResumeVersionEffects() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0);
        JobApplication firstCurrent = application(421L, USER_ID);
        firstCurrent.setResumeVersionId(77L);
        firstCurrent.setStatus("INTERVIEWING");
        JobApplication secondCurrent = application(422L, USER_ID);
        secondCurrent.setResumeVersionId(77L);
        JobApplication thirdCurrent = application(423L, USER_ID);
        thirdCurrent.setResumeVersionId(77L);
        JobApplication oldWithOffer = application(424L, USER_ID);
        oldWithOffer.setResumeVersionId(78L);
        JobApplication withoutVersion = application(425L, USER_ID);
        when(jobApplicationMapper.selectInsightRange(eq(USER_ID), any(), eq(now))).thenReturn(List.of(
                firstCurrent, secondCurrent, thirdCurrent, oldWithOffer, withoutVersion));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of(
                event(811L, 421L, "INTERVIEW_COMPLETED", now.minusDays(5)),
                event(812L, 424L, "OFFER", now.minusDays(4))));
        ResumeVersion currentVersion = resumeVersion(77L, USER_ID, 1L);
        currentVersion.setVersionNo(3);
        currentVersion.setVersionName("current backend");
        currentVersion.setCurrentFlag(1);
        ResumeVersion olderVersion = resumeVersion(78L, USER_ID, 1L);
        olderVersion.setVersionNo(2);
        olderVersion.setVersionName("old backend");
        olderVersion.setCurrentFlag(0);
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(currentVersion, olderVersion));

        ApplicationCareerInsightSummaryVO summary =
                service.getApplicationCareerInsightSummaryForUser(USER_ID, 30, now);

        assertEquals(4L, summary.getResumeVersionEffect().getVersionUsedCount());
        assertEquals(3L, summary.getResumeVersionEffect().getCurrentVersionApplicationCount());
        assertEquals(1L, summary.getResumeVersionEffect().getApplicationsWithoutVersionCount());
        ResumeVersionEffectItemVO current = summary.getResumeVersionEffect().getVersions().get(0);
        assertEquals(77L, current.getResumeVersionId());
        assertEquals(3L, current.getApplicationCount());
        assertEquals(1L, current.getInterviewCount());
        assertEquals(0L, current.getOfferCount());
        assertEquals("ENOUGH", current.getSampleLevel());
        assertEquals("使用最多", current.getInsightLabel());
        ResumeVersionEffectItemVO older = summary.getResumeVersionEffect().getVersions().get(1);
        assertEquals(78L, older.getResumeVersionId());
        assertEquals(1L, older.getApplicationCount());
        assertEquals(1L, older.getOfferCount());
    }

    @Test
    void getApplicationCareerInsightSummaryIncludesUnusedCurrentResumeVersion() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0);
        JobApplication olderVersionApplication = application(426L, USER_ID);
        olderVersionApplication.setResumeVersionId(78L);
        olderVersionApplication.setAppliedAt(now.minusDays(2));
        when(jobApplicationMapper.selectInsightRange(eq(USER_ID), any(), eq(now)))
                .thenReturn(List.of(olderVersionApplication));
        when(jobApplicationEventMapper.selectList(any())).thenReturn(List.of());
        ResumeVersion currentVersion = resumeVersion(77L, USER_ID, 1L);
        currentVersion.setVersionNo(3);
        currentVersion.setVersionName("current backend");
        currentVersion.setCurrentFlag(1);
        ResumeVersion olderVersion = resumeVersion(78L, USER_ID, 1L);
        olderVersion.setVersionNo(2);
        olderVersion.setVersionName("old backend");
        olderVersion.setCurrentFlag(0);
        when(resumeVersionMapper.selectList(any()))
                .thenReturn(List.of(olderVersion))
                .thenReturn(List.of(currentVersion));

        ApplicationCareerInsightSummaryVO summary =
                service.getApplicationCareerInsightSummaryForUser(USER_ID, 30, now);

        ResumeVersionEffectItemVO current = summary.getResumeVersionEffect().getVersions().stream()
                .filter(item -> item.getResumeVersionId().equals(77L))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, current.getApplicationCount());
        assertEquals(1, current.getCurrentFlag());
        assertEquals(0L, summary.getResumeVersionEffect().getCurrentVersionApplicationCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listApplicationReminderCandidatesReturnsActiveOverdueAndDueTodayOnly() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        JobApplication overdueEarly = application(301L, USER_ID);
        overdueEarly.setStatus("APPLIED");
        overdueEarly.setCompanyName("Alpha");
        overdueEarly.setJobTitle("Backend");
        overdueEarly.setNextFollowUpAt(now.minusHours(5));
        JobApplication overdueLate = application(302L, USER_ID);
        overdueLate.setStatus("OFFER");
        overdueLate.setCompanyName("Beta");
        overdueLate.setJobTitle("Platform");
        overdueLate.setNextFollowUpAt(now.minusHours(1));
        JobApplication dueToday = application(303L, USER_ID);
        dueToday.setStatus("interviewing");
        dueToday.setCompanyName("Gamma");
        dueToday.setJobTitle("Java");
        dueToday.setNextFollowUpAt(now.plusHours(2));
        JobApplication rejected = application(304L, USER_ID);
        rejected.setStatus("rejected");
        rejected.setNextFollowUpAt(now.minusDays(1));
        JobApplication closed = application(305L, USER_ID);
        closed.setStatus("closed");
        closed.setNextFollowUpAt(now.minusDays(1));
        JobApplication future = application(306L, USER_ID);
        future.setStatus("APPLIED");
        future.setNextFollowUpAt(now.plusDays(1));
        JobApplication extraDueToday = application(307L, USER_ID);
        extraDueToday.setStatus("SAVED");
        extraDueToday.setNextFollowUpAt(now.plusHours(3));
        JobApplication extraDueToday2 = application(308L, USER_ID);
        extraDueToday2.setStatus("PREPARING");
        extraDueToday2.setNextFollowUpAt(now.plusHours(4));
        JobApplication extraDueToday3 = application(309L, USER_ID);
        extraDueToday3.setStatus("APPLIED");
        extraDueToday3.setNextFollowUpAt(now.plusHours(5));
        when(jobApplicationMapper.selectReminderCandidates(
                USER_ID,
                now,
                reminderDate.atStartOfDay(),
                reminderDate.plusDays(1).atStartOfDay(),
                5)).thenReturn(List.of(overdueEarly, overdueLate, dueToday, extraDueToday, extraDueToday2));

        List<ApplicationReminderCandidateVO> result =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(5, result.size());
        assertEquals("301", result.get(0).getBizId());
        assertEquals("302", result.get(1).getBizId());
        assertEquals("303", result.get(2).getBizId());
        assertEquals("APPLICATION_FOLLOW_UP_REMINDER", result.get(0).getType());
        assertEquals("JOB_APPLICATION", result.get(0).getBizType());
        assertEquals("/applications?applicationId=301&openEvents=1", result.get(0).getActionUrl());
        assertEquals("/applications?applicationId=303&openEvents=1", result.get(2).getActionUrl());
        assertEquals("/applications", result.get(0).getFallbackPath());
        assertEquals("查看投递工作台", result.get(0).getFallbackLabel());
        assertEquals(reminderDate, result.get(0).getPlanDate());
        assertTrue(result.get(0).getContent().contains("Alpha"));

        verify(jobApplicationMapper).selectReminderCandidates(
                USER_ID,
                now,
                reminderDate.atStartOfDay(),
                reminderDate.plusDays(1).atStartOfDay(),
                5);
    }

    @Test
    void listApplicationReminderCandidatesAppendsActiveCalendarEventsAndUsesStableReminderIdentity() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        when(jobApplicationMapper.selectReminderCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        CareerCalendarEvent confirmedInterview = calendarEvent(501L, "面试一面", "INTERVIEW", "CONFIRMED",
                LocalDateTime.of(2026, 6, 16, 10, 0), LocalDateTime.of(2026, 6, 16, 11, 0), "UTC");
        CareerCalendarEvent cancelled = calendarEvent(502L, "已取消笔试", "WRITTEN_TEST", "CANCELLED",
                LocalDateTime.of(2026, 6, 16, 8, 0), LocalDateTime.of(2026, 6, 16, 9, 0), "UTC");
        when(careerCalendarEventMapper.selectList(any())).thenReturn(List.of(confirmedInterview, cancelled));

        List<ApplicationReminderCandidateVO> first =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);
        List<ApplicationReminderCandidateVO> second =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(1, first.size());
        ApplicationReminderCandidateVO vo = first.get(0);
        assertEquals("CALENDAR_REMINDER", vo.getType());
        assertEquals("CAREER_CALENDAR_EVENT", vo.getBizType());
        assertEquals("501", vo.getBizId());
        assertEquals("/career-calendar", vo.getActionUrl());
        assertEquals("/career-calendar", vo.getFallbackPath());
        assertEquals("打开求职日历", vo.getFallbackLabel());
        assertEquals(reminderDate, vo.getPlanDate());
        assertEquals("今天的求职日程", vo.getTitle());
        assertTrue(vo.getContent().contains("面试"));
        assertTrue(vo.getContent().contains("面试一面"));
        assertEquals(vo.getType(), second.get(0).getType());
        assertEquals(vo.getBizType(), second.get(0).getBizType());
        assertEquals(vo.getBizId(), second.get(0).getBizId());
    }

    @Test
    void listApplicationReminderCandidatesKeepsOverdueAndTomorrowEventsButFiltersOutsideAndDeleted() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        when(jobApplicationMapper.selectReminderCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        CareerCalendarEvent overdue = calendarEvent(511L, "逾期跟进", "FOLLOW_UP", "CONFIRMED",
                LocalDateTime.of(2026, 6, 15, 6, 0), LocalDateTime.of(2026, 6, 15, 7, 0), "UTC");
        CareerCalendarEvent tomorrow = calendarEvent(512L, "明日笔试", "WRITTEN_TEST", "TENTATIVE",
                LocalDateTime.of(2026, 6, 17, 6, 0), LocalDateTime.of(2026, 6, 17, 7, 0), "UTC");
        CareerCalendarEvent outside = calendarEvent(513L, "后天事项", "FOLLOW_UP", "CONFIRMED",
                LocalDateTime.of(2026, 6, 18, 6, 0), LocalDateTime.of(2026, 6, 18, 7, 0), "UTC");
        CareerCalendarEvent deleted = calendarEvent(514L, "已删除面试", "INTERVIEW", "CONFIRMED",
                LocalDateTime.of(2026, 6, 16, 12, 0), LocalDateTime.of(2026, 6, 16, 13, 0), "UTC");
        deleted.setDeleted(1);
        when(careerCalendarEventMapper.selectList(any()))
                .thenReturn(List.of(tomorrow, outside, deleted, overdue));

        List<ApplicationReminderCandidateVO> result =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(List.of("511", "512"), result.stream().map(ApplicationReminderCandidateVO::getBizId).toList());
        assertEquals("求职日程已逾期", result.get(0).getTitle());
        assertEquals("明天的求职日程", result.get(1).getTitle());
        assertEquals(reminderDate, result.get(0).getPlanDate());
        assertEquals(reminderDate, result.get(1).getPlanDate());
    }

    @Test
    void listApplicationReminderCandidatesScopesCalendarQueryToOwnerAndActiveRows() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        when(jobApplicationMapper.selectReminderCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(careerCalendarEventMapper.selectList(any())).thenReturn(List.of());

        service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<CareerCalendarEvent>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(careerCalendarEventMapper).selectList(captor.capture());
        String sql = (captor.getValue().getSqlSegment() + " " + captor.getValue().getTargetSql())
                .replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.contains("deleted"), sql);
        assertTrue(sql.contains("status in"), sql);
        assertTrue(sql.contains("starts_at_utc"), sql);
        assertTrue(sql.contains("starts_at_utc >="), sql);
        assertTrue(sql.contains("limit 50"), sql);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(USER_ID));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("CONFIRMED"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("TENTATIVE"));
    }

    @Test
    void listApplicationReminderCandidatesLimitsCalendarEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        when(jobApplicationMapper.selectReminderCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        List<CareerCalendarEvent> events = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            events.add(calendarEvent(600L + i, "日程 " + i, "FOLLOW_UP", "CONFIRMED",
                    LocalDateTime.of(2026, 6, 16, 10, 0), LocalDateTime.of(2026, 6, 16, 11, 0), "UTC"));
        }
        when(careerCalendarEventMapper.selectList(any())).thenReturn(events);

        List<ApplicationReminderCandidateVO> result =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(5, result.size());
    }

    @Test
    void listApplicationReminderCandidatesReservesUpcomingSlotsWhenOverdueEventsAreNumerous() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        when(jobApplicationMapper.selectReminderCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        List<CareerCalendarEvent> events = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            events.add(calendarEvent(700L + i, "逾期日程 " + i, "FOLLOW_UP", "CONFIRMED",
                    LocalDateTime.of(2026, 6, 10 + i % 3, 6, 0),
                    LocalDateTime.of(2026, 6, 10 + i % 3, 7, 0), "UTC"));
        }
        events.add(calendarEvent(799L, "今天面试", "INTERVIEW", "CONFIRMED",
                LocalDateTime.of(2026, 6, 16, 10, 0),
                LocalDateTime.of(2026, 6, 16, 11, 0), "UTC"));
        events.add(calendarEvent(800L, "明天笔试", "WRITTEN_TEST", "CONFIRMED",
                LocalDateTime.of(2026, 6, 17, 10, 0),
                LocalDateTime.of(2026, 6, 17, 11, 0), "UTC"));
        events.add(calendarEvent(801L, "今天跟进", "FOLLOW_UP", "CONFIRMED",
                LocalDateTime.of(2026, 6, 16, 12, 0),
                LocalDateTime.of(2026, 6, 16, 13, 0), "UTC"));
        when(careerCalendarEventMapper.selectList(any())).thenReturn(events);

        List<ApplicationReminderCandidateVO> result =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(5, result.size());
        assertEquals(2, result.stream()
                .filter(item -> "求职日程已逾期".equals(item.getTitle()))
                .count());
        assertTrue(result.stream().anyMatch(item -> "今天的求职日程".equals(item.getTitle())));
        assertTrue(result.stream().anyMatch(item -> "明天的求职日程".equals(item.getTitle())));
    }

    @Test
    void listApplicationReminderCandidatesUsesEventTimezoneForLocalDate() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 9, 0);
        LocalDate reminderDate = LocalDate.of(2026, 6, 16);
        when(jobApplicationMapper.selectReminderCandidates(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        CareerCalendarEvent pacificLateNight = calendarEvent(811L, "太平洋时区面试", "INTERVIEW", "CONFIRMED",
                LocalDateTime.of(2026, 6, 17, 6, 30),
                LocalDateTime.of(2026, 6, 17, 7, 30), "America/Los_Angeles");
        when(careerCalendarEventMapper.selectList(any())).thenReturn(List.of(pacificLateNight));

        List<ApplicationReminderCandidateVO> result =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(1, result.size());
        assertEquals("今天的求职日程", result.get(0).getTitle());
        assertEquals(reminderDate, result.get(0).getPlanDate());
    }

    private CareerCalendarEvent calendarEvent(Long id, String title, String eventType, String status,
                                              LocalDateTime startsAtUtc, LocalDateTime endsAtUtc, String timezone) {
        CareerCalendarEvent event = new CareerCalendarEvent();
        event.setId(id);
        event.setUserId(USER_ID);
        event.setTitle(title);
        event.setEventType(eventType);
        event.setStatus(status);
        event.setStartsAtUtc(startsAtUtc);
        event.setEndsAtUtc(endsAtUtc);
        event.setTimezone(timezone);
        event.setDeleted(0);
        return event;
    }

    private void assertAgentContextQuery(LambdaQueryWrapper<JobApplication> query, boolean expectTargetJobFilter) {
        String sql = (query.getSqlSegment() + " " + query.getTargetSql()).replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.contains("deleted"), sql);
        assertEquals(expectTargetJobFilter, sql.contains("target_job_id"), sql);
        assertTrue(sql.contains("status in"), sql);
        assertTrue(query.getParamNameValuePairs().containsValue("SAVED"));
        assertTrue(query.getParamNameValuePairs().containsValue("PREPARING"));
        assertTrue(query.getParamNameValuePairs().containsValue("APPLIED"));
        assertTrue(query.getParamNameValuePairs().containsValue("INTERVIEWING"));
        assertTrue(query.getParamNameValuePairs().containsValue("OFFER"));
        int orderBy = sql.indexOf("order by");
        int nullLastOrder = sql.indexOf("next_follow_up_at is null", orderBy);
        int nextFollowUpOrder = sql.indexOf("next_follow_up_at asc",
                nullLastOrder + "next_follow_up_at is null".length());
        int updatedAtOrder = sql.indexOf("updated_at desc", nextFollowUpOrder);
        int limit = sql.indexOf("limit 20", updatedAtOrder);
        assertTrue(orderBy >= 0, sql);
        assertTrue(nullLastOrder > orderBy, sql);
        assertTrue(nextFollowUpOrder > nullLastOrder, sql);
        assertTrue(updatedAtOrder > nextFollowUpOrder, sql);
        assertTrue(limit > updatedAtOrder, sql);
    }

    @Test
    void rollbackRejectsVersionFromDifferentResumeBeforeMutating() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, USER_ID));
        when(resumeVersionMapper.selectById(2L)).thenReturn(resumeVersion(2L, USER_ID, 99L));

        assertThrows(BusinessException.class, () -> service.rollbackVersion(1L, 2L));
        verify(resumeMapper, never()).updateById(any(Resume.class));
        verify(resumeVersionMapper, never()).update(org.mockito.ArgumentMatchers.<ResumeVersion>isNull(), any());
        verify(resumeVersionMapper, never()).updateById(any(ResumeVersion.class));
    }

    @Test
    void createVersionStoresCurrentUserSnapshotAndClearsOnlyOwnedCurrentVersion() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, USER_ID));
        when(resumeVersionMapper.selectOne(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ResumeVersion>>any())).thenReturn(null);
        when(resumeProjectMapper.selectList(any())).thenReturn(List.of(project(11L)));

        service.createVersion(1L, null);

        ArgumentCaptor<ResumeVersion> versionCaptor = ArgumentCaptor.forClass(ResumeVersion.class);
        verify(resumeVersionMapper).update(eq(null), any());
        verify(resumeVersionMapper).insert(versionCaptor.capture());
        ResumeVersion version = versionCaptor.getValue();
        assertEquals(USER_ID, version.getUserId());
        assertEquals(1L, version.getResumeId());
        assertEquals(1, version.getVersionNo());
        assertEquals("V1", version.getVersionName());
        assertEquals("MANUAL", version.getSourceType());
        assertEquals(1, version.getCurrentFlag());
        assertTrue(version.getSnapshotJson().contains("\"projects\""));
        assertTrue(version.getSnapshotJson().contains("智能求职项目"));
        assertTrue(version.getSnapshotJson().contains("\"projectSnapshotSource\":\"RESUME_VERSION\""));
    }

    @Test
    void createVersionCanonicalizesSameSortProjectsIndependentOfDatabaseOrder() throws Exception {
        ResumeProject alpha = project(11L);
        alpha.setProjectName("Alpha project");
        alpha.setSort(null);
        alpha.setSortOrder(null);
        ResumeProject zulu = project(12L);
        zulu.setProjectName("Zulu project");
        zulu.setSort(null);
        zulu.setSortOrder(null);
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, USER_ID));
        when(resumeVersionMapper.selectOne(
                org.mockito.ArgumentMatchers.<LambdaQueryWrapper<ResumeVersion>>any())).thenReturn(null);
        when(resumeProjectMapper.selectList(any()))
                .thenReturn(List.of(zulu, alpha), List.of(alpha, zulu));

        service.createVersion(1L, null);
        service.createVersion(1L, null);

        ArgumentCaptor<ResumeVersion> versions = ArgumentCaptor.forClass(ResumeVersion.class);
        verify(resumeVersionMapper, times(2)).insert(versions.capture());
        String firstSnapshot = versions.getAllValues().get(0).getSnapshotJson();
        String secondSnapshot = versions.getAllValues().get(1).getSnapshotJson();
        assertEquals(firstSnapshot, secondSnapshot);
        var projects = new ObjectMapper().readTree(firstSnapshot).path("projects");
        assertEquals("Alpha project", projects.get(0).path("projectName").asText());
        assertEquals(0, projects.get(0).path("sort").asInt());
        assertEquals(0, projects.get(0).path("sortOrder").asInt());
    }

    private Resume resume(Long id, Long userId) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(userId);
        resume.setTitle("Java 后端简历");
        resume.setRealName("测试用户");
        resume.setTargetPosition("Java 后端工程师");
        resume.setSkillStack("Java, Spring Boot, MySQL");
        return resume;
    }

    private ResumeVersion resumeVersion(Long id, Long userId, Long resumeId) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setUserId(userId);
        version.setResumeId(resumeId);
        version.setVersionNo(1);
        version.setVersionName("V1");
        version.setSnapshotJson("{\"title\":\"Java 后端简历\"}");
        version.setCurrentFlag(0);
        return version;
    }

    private ResumeProject project(Long id) {
        ResumeProject project = new ResumeProject();
        project.setId(id);
        project.setResumeId(1L);
        project.setProjectName("智能求职项目");
        project.setRole("后端开发");
        project.setTechStack("Spring Cloud, Redis");
        project.setResponsibility("负责匹配报告与版本闭环");
        return project;
    }

    private ResumeJobMatchReport matchReport(Long id) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setId(id);
        report.setUserId(USER_ID);
        report.setResumeId(1L);
        report.setResumeVersionId(77L);
        report.setTargetJobId(88L);
        return report;
    }

    private TargetJob targetJob(Long id, Long userId) {
        TargetJob targetJob = new TargetJob();
        targetJob.setId(id);
        targetJob.setUserId(userId);
        targetJob.setJobTitle("Java Backend Engineer");
        targetJob.setCompanyName("Demo Company");
        return targetJob;
    }

    private JobApplication application(Long id, Long userId) {
        JobApplication app = new JobApplication();
        app.setId(id);
        app.setUserId(userId);
        app.setCompanyName("测试科技");
        app.setJobTitle("Java 后端工程师");
        app.setStatus("APPLIED");
        return app;
    }

    private JobApplicationEvent event(Long id, Long applicationId, String eventType, LocalDateTime eventTime) {
        JobApplicationEvent event = new JobApplicationEvent();
        event.setId(id);
        event.setUserId(USER_ID);
        event.setApplicationId(applicationId);
        event.setEventType(eventType);
        event.setEventTime(eventTime);
        return event;
    }
}
