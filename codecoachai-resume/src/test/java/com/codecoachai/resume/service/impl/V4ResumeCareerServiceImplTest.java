package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.codecoachai.resume.domain.vo.ApplicationReminderCandidateVO;
import com.codecoachai.resume.domain.vo.JobApplicationAgentContextVO;
import com.codecoachai.resume.domain.vo.JobApplicationEventVO;
import com.codecoachai.resume.domain.vo.JobApplicationStatsVO;
import com.codecoachai.resume.domain.vo.JobApplicationVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeSuggestionAdoptionMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
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
    private AgentBusinessActionNotifier agentBusinessActionNotifier;
    @Mock
    private NotificationBusinessResolver notificationBusinessResolver;

    private V4ResumeCareerServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(ResumeVersion.class);
        initTableInfo(ResumeProject.class);
        initTableInfo(ResumeJobMatchReport.class);
        initTableInfo(JobApplication.class);
        initTableInfo(JobApplicationEvent.class);
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
                agentBusinessActionNotifier,
                notificationBusinessResolver,
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
        when(jobApplicationMapper.selectList(any())).thenReturn(List.of(
                dueToday, rejected, overdueLate, future, closed, extraDueToday3,
                extraDueToday2, overdueEarly, extraDueToday));

        List<ApplicationReminderCandidateVO> result =
                service.listApplicationReminderCandidates(USER_ID, reminderDate, now);

        assertEquals(5, result.size());
        assertEquals("301", result.get(0).getBizId());
        assertEquals("302", result.get(1).getBizId());
        assertEquals("303", result.get(2).getBizId());
        assertEquals("APPLICATION_FOLLOW_UP_REMINDER", result.get(0).getType());
        assertEquals("JOB_APPLICATION", result.get(0).getBizType());
        assertEquals("/applications?followUp=overdue", result.get(0).getActionUrl());
        assertEquals("/applications?followUp=due-today", result.get(2).getActionUrl());
        assertEquals("/applications", result.get(0).getFallbackPath());
        assertEquals("查看投递工作台", result.get(0).getFallbackLabel());
        assertEquals(reminderDate, result.get(0).getPlanDate());
        assertTrue(result.get(0).getContent().contains("Alpha"));

        ArgumentCaptor<LambdaQueryWrapper<JobApplication>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(jobApplicationMapper).selectList(queryCaptor.capture());
        String sql = (queryCaptor.getValue().getSqlSegment() + " " + queryCaptor.getValue().getTargetSql())
                .replaceAll("\\s+", " ")
                .toLowerCase();
        assertTrue(sql.contains("user_id"), sql);
        assertTrue(sql.contains("deleted"), sql);
        assertFalse(sql.contains("status in"), sql);
        assertTrue(sql.contains("next_follow_up_at is not null"), sql);
        assertFalse(queryCaptor.getValue().getParamNameValuePairs().containsValue("REJECTED"));
        assertFalse(queryCaptor.getValue().getParamNameValuePairs().containsValue("CLOSED"));
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
