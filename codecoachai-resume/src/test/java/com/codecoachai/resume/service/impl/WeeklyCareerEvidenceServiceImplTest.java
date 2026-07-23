package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationEvent;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobSearchExperiment;
import com.codecoachai.resume.domain.entity.JobSearchExperimentRelation;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.entity.UserAbilityProfile;
import com.codecoachai.resume.domain.vo.WeeklyCareerEvidenceVO;
import com.codecoachai.resume.mapper.JobApplicationEventMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentMapper;
import com.codecoachai.resume.mapper.JobSearchExperimentRelationMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.UserAbilityProfileMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyCareerEvidenceServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long TARGET_JOB_ID = 20L;
    private static final LocalDateTime RANGE_START = LocalDateTime.of(2026, 7, 12, 16, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2026, 7, 19, 16, 0);
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 7, 18, 12, 0);

    @Mock
    private JobApplicationMapper jobApplicationMapper;
    @Mock
    private JobApplicationEventMapper jobApplicationEventMapper;
    @Mock
    private CareerCalendarEventMapper careerCalendarEventMapper;
    @Mock
    private JobSearchExperimentMapper experimentMapper;
    @Mock
    private JobSearchExperimentRelationMapper relationMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private ResumeJobMatchReportMapper matchReportMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private UserAbilityProfileMapper abilityProfileMapper;

    private ObjectMapper objectMapper;
    private WeeklyCareerEvidenceServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(TargetJob.class);
        init(ResumeVersion.class);
        init(JobDescriptionAnalysis.class);
        init(ResumeJobMatchReport.class);
        init(ProjectEvidence.class);
        init(UserAbilityProfile.class);
    }

    private static void init(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    entityType);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new WeeklyCareerEvidenceServiceImpl(
                jobApplicationMapper,
                jobApplicationEventMapper,
                careerCalendarEventMapper,
                experimentMapper,
                relationMapper,
                targetJobMapper,
                resumeVersionMapper,
                jobDescriptionAnalysisMapper,
                matchReportMapper,
                projectEvidenceMapper,
                abilityProfileMapper,
                objectMapper);
    }

    @Test
    void returnsNormalStructuredWeeklyEvidence() {
        JobApplication application = application(1L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(2));
        application.setSource("linkedin");
        application.setStatus("applied");
        application.setResumeVersionId(30L);

        JobApplicationEvent event = event(
                2L,
                USER_ID,
                application.getId(),
                "INTERVIEW_COMPLETED",
                RANGE_START.plusDays(2));
        event.setReviewJson("""
                {"structuredReview":{"scenario":"INTERVIEW_COMPLETED","privateText":"alice@example.com"}}
                """);

        CareerCalendarEvent calendar = calendar(
                3L,
                USER_ID,
                application.getId(),
                "INTERVIEW",
                RANGE_START.plusDays(3));

        JobSearchExperiment experiment = experiment(4L, USER_ID);
        experiment.setTargetDirection("backend");
        JobSearchExperimentRelation relation = relation(
                5L,
                USER_ID,
                experiment.getId(),
                "TARGET_JOB",
                TARGET_JOB_ID);
        TargetJob targetJob = targetJob(TARGET_JOB_ID, USER_ID);

        stubSources(
                null,
                List.of(application),
                List.of(event),
                List.of(calendar),
                List.of(experiment));
        when(relationMapper.selectWeeklyEvidenceRelations(
                eq(USER_ID),
                eq(List.of(experiment.getId())),
                eq(CUTOFF),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENT_RELATIONS + 1)))
                .thenReturn(List.of(relation));
        when(targetJobMapper.selectList(any())).thenReturn(List.of(targetJob));

        WeeklyCareerEvidenceVO result = collect(null);

        assertEquals(USER_ID, result.getUserId());
        assertEquals(1, result.getApplications().size());
        assertEquals("CHANNEL:LINKEDIN", result.getApplications().get(0).getChannelKey());
        assertEquals("APPLIED", result.getApplications().get(0).getCurrentStatus());
        assertEquals(TARGET_JOB_ID, result.getApplications().get(0).getTargetJobId());
        assertEquals(1, result.getApplicationEvents().size());
        assertTrue(result.getApplicationEvents().get(0).getStructuredReview());
        assertEquals("INTERVIEW_COMPLETED", result.getApplicationEvents().get(0).getEventType());
        assertEquals(1, result.getCalendarEvents().size());
        assertEquals("SCHEDULED_INTERVIEW", result.getCalendarEvents().get(0).getEventType());
        assertEquals(1, result.getExperiments().size());
        assertEquals("DIRECTION:BACKEND", result.getExperiments().get(0).getTargetDirection());
        assertTrue(result.getExperiments().get(0).getRelations().get(0).getIncluded());
        assertEquals(TARGET_JOB_ID,
                result.getExperiments().get(0).getRelations().get(0).getRelationObjectId());
        assertEquals("COMPLETE", result.getConsistencyLevel());
        assertFalse(result.getTruncated());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getSourceCounts().get("applications.included"));
        assertEquals(1, result.getSourceCounts().get("applicationEvents.included"));
        assertEquals(1, result.getSourceCounts().get("calendarEvents.included"));
        assertEquals(1, result.getSourceCounts().get("experiments.included"));
        assertEquals(1, result.getSourceCounts().get("experimentRelations.included"));
    }

    @Test
    void filtersEverySourceToRequestedTargetScope() {
        TargetJob requestedTarget = targetJob(TARGET_JOB_ID, USER_ID);
        TargetJob otherTarget = targetJob(21L, USER_ID);
        when(targetJobMapper.selectOne(any())).thenReturn(requestedTarget);

        JobApplication matching = application(
                1L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(1));
        JobApplication other = application(
                2L, USER_ID, otherTarget.getId(), RANGE_START.plusHours(2));
        JobApplicationEvent matchingEvent = event(
                3L, USER_ID, matching.getId(), "FOLLOW_UP", RANGE_START.plusHours(3));
        JobApplicationEvent otherEvent = event(
                4L, USER_ID, other.getId(), "OFFER", RANGE_START.plusHours(4));
        CareerCalendarEvent matchingCalendar = calendar(
                5L, USER_ID, matching.getId(), "FOLLOW_UP", RANGE_START.plusHours(5));
        CareerCalendarEvent unlinkedCalendar = calendar(
                6L, USER_ID, null, "INTERVIEW", RANGE_START.plusHours(6));

        JobSearchExperiment experiment = experiment(7L, USER_ID);
        JobSearchExperimentRelation matchingRelation = relation(
                8L, USER_ID, experiment.getId(), "TARGET_JOB", TARGET_JOB_ID);
        JobSearchExperimentRelation otherRelation = relation(
                9L, USER_ID, experiment.getId(), "TARGET_JOB", otherTarget.getId());

        stubSources(
                TARGET_JOB_ID,
                List.of(matching, other),
                List.of(matchingEvent, otherEvent),
                List.of(matchingCalendar, unlinkedCalendar),
                List.of(experiment));
        when(relationMapper.selectWeeklyEvidenceRelations(
                eq(USER_ID),
                eq(List.of(experiment.getId())),
                eq(CUTOFF),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENT_RELATIONS + 1)))
                .thenReturn(List.of(matchingRelation, otherRelation));
        when(targetJobMapper.selectList(any())).thenReturn(List.of(requestedTarget, otherTarget));

        WeeklyCareerEvidenceVO result = collect(TARGET_JOB_ID);

        assertEquals(List.of(matching.getId()),
                result.getApplications().stream().map(
                        WeeklyCareerEvidenceVO.ApplicationItem::getApplicationId).toList());
        assertEquals(List.of(matchingEvent.getId()),
                result.getApplicationEvents().stream().map(
                        WeeklyCareerEvidenceVO.ApplicationEventItem::getEventId).toList());
        assertEquals(List.of(matchingCalendar.getId()),
                result.getCalendarEvents().stream().map(
                        WeeklyCareerEvidenceVO.CalendarEventItem::getEventId).toList());
        assertEquals(1, result.getExperiments().size());
        assertEquals(2, result.getExperiments().get(0).getRelations().size());
        assertTrue(result.getExperiments().get(0).getRelations().get(0).getIncluded());
        assertFalse(result.getExperiments().get(0).getRelations().get(1).getIncluded());
        assertNull(result.getExperiments().get(0).getRelations().get(1).getRelationObjectId());
        assertEquals("TARGET_SCOPE_MISMATCH",
                result.getExperiments().get(0).getRelations().get(1).getExcludeReason());
        assertEquals("PARTIAL", result.getConsistencyLevel());
        assertTrue(result.getWarnings().stream().allMatch(
                WeeklyCareerEvidenceServiceImplTest::isInternalCode));
        assertTrue(result.getExperiments().stream()
                .flatMap(item -> item.getRelations().stream())
                .map(WeeklyCareerEvidenceVO.ExperimentRelationItem::getExcludeReason)
                .filter(java.util.Objects::nonNull)
                .allMatch(WeeklyCareerEvidenceServiceImplTest::isInternalCode));

        verify(jobApplicationMapper).selectWeeklyEvidenceApplications(
                USER_ID,
                RANGE_START,
                RANGE_END,
                CUTOFF,
                TARGET_JOB_ID,
                WeeklyCareerEvidenceServiceImpl.MAX_APPLICATIONS + 1);
        verify(jobApplicationEventMapper).selectWeeklyEvidenceEvents(
                USER_ID,
                RANGE_START,
                RANGE_END,
                CUTOFF,
                TARGET_JOB_ID,
                WeeklyCareerEvidenceServiceImpl.MAX_APPLICATION_EVENTS + 1);
        verify(careerCalendarEventMapper).selectWeeklyEvidenceEvents(
                USER_ID,
                RANGE_START,
                RANGE_END,
                CUTOFF,
                TARGET_JOB_ID,
                WeeklyCareerEvidenceServiceImpl.MAX_CALENDAR_EVENTS + 1);
    }

    @Test
    void appliesStartInclusiveEndExclusiveAndCutoffBoundaries() {
        JobApplication atStart = application(1L, USER_ID, TARGET_JOB_ID, RANGE_START);
        JobApplication beforeEnd = application(
                2L, USER_ID, TARGET_JOB_ID, RANGE_END.minusNanos(1));
        JobApplication atEnd = application(3L, USER_ID, TARGET_JOB_ID, RANGE_END);
        JobApplication beforeStart = application(
                4L, USER_ID, TARGET_JOB_ID, RANGE_START.minusNanos(1));
        JobApplication atCutoff = application(
                5L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(4));
        atCutoff.setCreatedAt(CUTOFF);
        atCutoff.setUpdatedAt(CUTOFF);
        JobApplication afterCutoff = application(
                6L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(5));
        afterCutoff.setCreatedAt(CUTOFF.plusNanos(1));
        afterCutoff.setUpdatedAt(CUTOFF.plusNanos(1));

        JobApplicationEvent eventAtStart = event(
                10L, USER_ID, atStart.getId(), "NOTE", RANGE_START);
        JobApplicationEvent eventAtEnd = event(
                11L, USER_ID, atStart.getId(), "NOTE", RANGE_END);
        CareerCalendarEvent calendarAtStart = calendar(
                12L, USER_ID, atStart.getId(), "FOLLOW_UP", RANGE_START);
        CareerCalendarEvent calendarAtEnd = calendar(
                13L, USER_ID, atStart.getId(), "FOLLOW_UP", RANGE_END);

        stubSources(
                null,
                List.of(atStart, beforeEnd, atEnd, beforeStart, atCutoff, afterCutoff),
                List.of(eventAtStart, eventAtEnd),
                List.of(calendarAtStart, calendarAtEnd),
                List.of());
        when(targetJobMapper.selectList(any())).thenReturn(List.of(targetJob(TARGET_JOB_ID, USER_ID)));

        WeeklyCareerEvidenceVO result = collect(null);

        assertEquals(List.of(atStart.getId(), atCutoff.getId(), beforeEnd.getId()),
                result.getApplications().stream()
                        .map(WeeklyCareerEvidenceVO.ApplicationItem::getApplicationId)
                        .toList());
        assertEquals(List.of(eventAtStart.getId()),
                result.getApplicationEvents().stream()
                        .map(WeeklyCareerEvidenceVO.ApplicationEventItem::getEventId)
                        .toList());
        assertEquals(List.of(calendarAtStart.getId()),
                result.getCalendarEvents().stream()
                        .map(WeeklyCareerEvidenceVO.CalendarEventItem::getEventId)
                        .toList());
        assertEquals("PARTIAL", result.getConsistencyLevel());
        assertTrue(result.getWarnings().contains("APPLICATION_RECORDS_EXCLUDED"));
        assertTrue(result.getWarnings().contains("APPLICATION_EVENT_RECORDS_EXCLUDED"));
        assertTrue(result.getWarnings().contains("CALENDAR_EVENT_RECORDS_EXCLUDED"));
    }

    @Test
    void excludesMutableSourcesUpdatedAfterTheCutoff() {
        JobApplication stableApplication = application(
                1L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(1));
        JobApplication updatedApplication = application(
                2L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(2));
        updatedApplication.setUpdatedAt(CUTOFF.plusNanos(1));

        JobApplicationEvent updatedEvent = event(
                3L,
                USER_ID,
                stableApplication.getId(),
                "INTERVIEW_COMPLETED",
                RANGE_START.plusHours(3));
        updatedEvent.setUpdatedAt(CUTOFF.plusNanos(1));

        CareerCalendarEvent updatedCalendar = calendar(
                4L,
                USER_ID,
                stableApplication.getId(),
                "INTERVIEW",
                RANGE_START.plusHours(4));
        updatedCalendar.setUpdatedAt(CUTOFF.plusNanos(1));

        JobSearchExperiment updatedExperiment = experiment(5L, USER_ID);
        updatedExperiment.setUpdatedAt(CUTOFF.plusNanos(1));
        JobSearchExperiment stableExperiment = experiment(6L, USER_ID);
        JobSearchExperimentRelation updatedRelation = relation(
                7L,
                USER_ID,
                stableExperiment.getId(),
                "TARGET_JOB",
                TARGET_JOB_ID);
        updatedRelation.setUpdatedAt(CUTOFF.plusNanos(1));

        stubSources(
                null,
                List.of(stableApplication, updatedApplication),
                List.of(updatedEvent),
                List.of(updatedCalendar),
                List.of(updatedExperiment, stableExperiment));
        when(relationMapper.selectWeeklyEvidenceRelations(
                eq(USER_ID),
                eq(List.of(updatedExperiment.getId(), stableExperiment.getId())),
                eq(CUTOFF),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENT_RELATIONS + 1)))
                .thenReturn(List.of(updatedRelation));
        when(targetJobMapper.selectList(any())).thenReturn(List.of(targetJob(TARGET_JOB_ID, USER_ID)));

        WeeklyCareerEvidenceVO result = collect(null);

        assertEquals(List.of(stableApplication.getId()),
                result.getApplications().stream()
                        .map(WeeklyCareerEvidenceVO.ApplicationItem::getApplicationId)
                        .toList());
        assertTrue(result.getApplicationEvents().isEmpty());
        assertTrue(result.getCalendarEvents().isEmpty());
        assertEquals(List.of(stableExperiment.getId()),
                result.getExperiments().stream()
                        .map(WeeklyCareerEvidenceVO.ExperimentItem::getExperimentId)
                        .toList());
        assertTrue(result.getExperiments().get(0).getRelations().isEmpty());
        assertEquals(1, result.getSourceCounts().get("applications.excluded"));
        assertEquals(1, result.getSourceCounts().get("applicationEvents.excluded"));
        assertEquals(1, result.getSourceCounts().get("calendarEvents.excluded"));
        assertEquals(1, result.getSourceCounts().get("experiments.excluded"));
        assertEquals(1, result.getSourceCounts().get("experimentRelations.excluded"));
        assertEquals("PARTIAL", result.getConsistencyLevel());
        assertTrue(result.getWarnings().stream().allMatch(
                WeeklyCareerEvidenceServiceImplTest::isInternalCode));
    }

    @Test
    void doesNotReintroduceScopedTargetUpdatedAfterTheCutoff() {
        TargetJob updatedTarget = targetJob(TARGET_JOB_ID, USER_ID);
        updatedTarget.setUpdatedAt(CUTOFF.plusNanos(1));
        JobApplication application = application(
                1L, USER_ID, TARGET_JOB_ID, RANGE_START.plusHours(1));
        JobSearchExperiment experiment = experiment(2L, USER_ID);
        JobSearchExperimentRelation relation = relation(
                3L,
                USER_ID,
                experiment.getId(),
                "TARGET_JOB",
                TARGET_JOB_ID);

        when(targetJobMapper.selectOne(any())).thenReturn(updatedTarget);
        stubSources(
                TARGET_JOB_ID,
                List.of(application),
                List.of(),
                List.of(),
                List.of(experiment));
        when(relationMapper.selectWeeklyEvidenceRelations(
                eq(USER_ID),
                eq(List.of(experiment.getId())),
                eq(CUTOFF),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENT_RELATIONS + 1)))
                .thenReturn(List.of(relation));
        when(targetJobMapper.selectList(any())).thenReturn(List.of(updatedTarget));

        WeeklyCareerEvidenceVO result = collect(TARGET_JOB_ID);

        assertNull(result.getApplications().get(0).getTargetJobId());
        assertEquals(Boolean.FALSE,
                result.getApplications().get(0).getMetadata().get("targetAssociationVerified"));
        assertTrue(result.getExperiments().isEmpty());
        assertEquals(1, result.getSourceCounts().get("experiments.excluded"));
        assertEquals(1, result.getSourceCounts().get("experimentRelations.excluded"));
        assertEquals("PARTIAL", result.getConsistencyLevel());
        assertTrue(result.getWarnings().contains("TARGET_ASSOCIATION_REDACTED"));
        assertTrue(result.getWarnings().contains("EXPERIMENT_RELATION_OBJECTS_EXCLUDED"));
        assertTrue(result.getWarnings().contains("EXPERIMENT_TARGET_SCOPE_EXCLUDED"));
        assertTrue(result.getWarnings().stream().allMatch(
                WeeklyCareerEvidenceServiceImplTest::isInternalCode));
    }

    @Test
    void returnsCompleteEmptyEvidenceForEmptySample() {
        stubSources(null, List.of(), List.of(), List.of(), List.of());

        WeeklyCareerEvidenceVO result = collect(null);

        assertTrue(result.getApplications().isEmpty());
        assertTrue(result.getApplicationEvents().isEmpty());
        assertTrue(result.getCalendarEvents().isEmpty());
        assertTrue(result.getExperiments().isEmpty());
        assertEquals("COMPLETE", result.getConsistencyLevel());
        assertFalse(result.getTruncated());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(0, result.getSourceCounts().get("applications.total"));
        assertEquals(0, result.getSourceCounts().get("applicationEvents.total"));
        assertEquals(0, result.getSourceCounts().get("calendarEvents.total"));
        assertEquals(0, result.getSourceCounts().get("experiments.total"));
        assertEquals(0, result.getSourceCounts().get("experimentRelations.total"));
    }

    @Test
    void failsClosedForForeignOwnerRowsAndRelations() {
        long foreignUserId = 99L;
        JobApplication foreignApplication = application(
                1L, foreignUserId, TARGET_JOB_ID, RANGE_START.plusHours(1));
        JobApplicationEvent foreignEvent = event(
                2L, foreignUserId, foreignApplication.getId(), "OFFER", RANGE_START.plusHours(2));
        CareerCalendarEvent foreignCalendar = calendar(
                3L, foreignUserId, foreignApplication.getId(), "INTERVIEW", RANGE_START.plusHours(3));
        JobSearchExperiment foreignExperiment = experiment(4L, foreignUserId);
        JobSearchExperimentRelation foreignRelation = relation(
                5L,
                foreignUserId,
                foreignExperiment.getId(),
                "JOB_APPLICATION",
                foreignApplication.getId());

        stubSources(
                null,
                List.of(foreignApplication),
                List.of(foreignEvent),
                List.of(foreignCalendar),
                List.of(foreignExperiment));
        when(relationMapper.selectWeeklyEvidenceRelations(
                eq(USER_ID),
                eq(List.of(foreignExperiment.getId())),
                eq(CUTOFF),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENT_RELATIONS + 1)))
                .thenReturn(List.of(foreignRelation));
        when(jobApplicationMapper.selectWeeklyEvidenceOwnedApplications(
                eq(USER_ID),
                eq(List.of(foreignApplication.getId())),
                eq(CUTOFF)))
                .thenReturn(List.of(foreignApplication));

        WeeklyCareerEvidenceVO result = collect(null);

        assertTrue(result.getApplications().isEmpty());
        assertTrue(result.getApplicationEvents().isEmpty());
        assertTrue(result.getCalendarEvents().isEmpty());
        assertTrue(result.getExperiments().isEmpty());
        assertEquals("PARTIAL", result.getConsistencyLevel());
        assertTrue(result.getSourceCounts().get("applications.excluded") > 0);
        assertTrue(result.getSourceCounts().get("applicationEvents.excluded") > 0);
        assertTrue(result.getSourceCounts().get("calendarEvents.excluded") > 0);
        assertTrue(result.getSourceCounts().get("experiments.excluded") > 0);
        assertTrue(result.getSourceCounts().get("experimentRelations.excluded") > 0);
    }

    @Test
    void rejectsForeignTargetBeforeReadingWeeklySources() {
        TargetJob foreignTarget = targetJob(TARGET_JOB_ID, 99L);
        when(targetJobMapper.selectOne(any())).thenReturn(foreignTarget);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> collect(TARGET_JOB_ID));

        assertEquals("目标岗位不存在或当前用户无权访问", exception.getMessage());
        verify(jobApplicationMapper, never()).selectWeeklyEvidenceApplications(
                any(), any(), any(), any(), any(), any());
        verify(jobApplicationEventMapper, never()).selectWeeklyEvidenceEvents(
                any(), any(), any(), any(), any(), any());
        verify(careerCalendarEventMapper, never()).selectWeeklyEvidenceEvents(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void validationErrorsUseChineseUserVisibleMessages() {
        assertEquals(
                "userId 必须为正整数",
                assertThrows(BusinessException.class, () -> service.getWeeklyEvidence(
                        0L, RANGE_START, RANGE_END, CUTOFF, null, "Asia/Shanghai", null))
                        .getMessage());
        assertEquals(
                "rangeStartUtc、rangeEndUtc 和 sourceCutoffAt 不能为空",
                assertThrows(BusinessException.class, () -> service.getWeeklyEvidence(
                        USER_ID, null, RANGE_END, CUTOFF, null, "Asia/Shanghai", null))
                        .getMessage());
        assertEquals(
                "rangeStartUtc 必须早于 rangeEndUtc",
                assertThrows(BusinessException.class, () -> service.getWeeklyEvidence(
                        USER_ID, RANGE_END, RANGE_END, CUTOFF, null, "Asia/Shanghai", null))
                        .getMessage());
        assertEquals(
                "targetJobId 必须为正整数",
                assertThrows(BusinessException.class, () -> service.getWeeklyEvidence(
                        USER_ID, RANGE_START, RANGE_END, CUTOFF, 0L, "Asia/Shanghai", null))
                        .getMessage());
        assertEquals(
                "timezone 必须是有效的 ZoneId",
                assertThrows(BusinessException.class, () -> service.getWeeklyEvidence(
                        USER_ID, RANGE_START, RANGE_END, CUTOFF, null, "not/a-zone", null))
                        .getMessage());
        assertEquals(
                "experimentIds 只能包含正整数",
                assertThrows(BusinessException.class, () -> service.getWeeklyEvidence(
                        USER_ID, RANGE_START, RANGE_END, CUTOFF, null, "Asia/Shanghai", List.of(-1L)))
                        .getMessage());
    }

    @Test
    void truncatesAtContractLimitsAndNeverReturnsFreeTextOrPii() throws Exception {
        List<JobApplication> applications = IntStream.rangeClosed(
                        1, WeeklyCareerEvidenceServiceImpl.MAX_APPLICATIONS + 1)
                .mapToObj(index -> application(
                        (long) index,
                        USER_ID,
                        TARGET_JOB_ID,
                        RANGE_START.plusMinutes(index)))
                .toList();
        applications.forEach(application -> {
            application.setCompanyName("Secret Company");
            application.setJobTitle("Private Candidate Job");
            application.setSource("alice@example.com token=top-secret 13800138000");
            application.setNote("private note with password=hidden");
        });

        JobApplicationEvent event = event(
                1000L,
                USER_ID,
                applications.get(0).getId(),
                "INTERVIEW_COMPLETED",
                RANGE_START.plusDays(1));
        event.setSummary("Recruiter email alice@example.com and phone 13800138000");
        event.setReviewJson("""
                {"structuredReview":{"privateText":"token=top-secret","phone":"13800138000"}}
                """);

        CareerCalendarEvent calendar = calendar(
                1001L,
                USER_ID,
                applications.get(0).getId(),
                "INTERVIEW",
                RANGE_START.plusDays(2));
        calendar.setTitle("Private interview with alice@example.com");
        calendar.setLocation("13800138000");
        calendar.setDescription("password=hidden");

        JobSearchExperiment experiment = experiment(1002L, USER_ID);
        experiment.setTitle("Private experiment alice@example.com");
        experiment.setGoal("phone 13800138000");
        experiment.setTargetDirection("alice@example.com token=top-secret");
        experiment.setSummary("password=hidden");
        JobSearchExperimentRelation relation = relation(
                1003L,
                USER_ID,
                experiment.getId(),
                "TARGET_JOB",
                TARGET_JOB_ID);
        relation.setRelationSummary("Private relation alice@example.com");
        relation.setMetadataJson("{\"token\":\"top-secret\"}");

        TargetJob targetJob = targetJob(TARGET_JOB_ID, USER_ID);
        targetJob.setCompanyName("Secret Company");
        targetJob.setJobTitle("Private Candidate Job");
        targetJob.setJdText("alice@example.com 13800138000 token=top-secret");

        stubSources(
                null,
                applications,
                List.of(event),
                List.of(calendar),
                List.of(experiment));
        when(relationMapper.selectWeeklyEvidenceRelations(
                eq(USER_ID),
                eq(List.of(experiment.getId())),
                eq(CUTOFF),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENT_RELATIONS + 1)))
                .thenReturn(List.of(relation));
        when(targetJobMapper.selectList(any())).thenReturn(List.of(targetJob));

        WeeklyCareerEvidenceVO result = collect(null);
        String json = objectMapper.writeValueAsString(result);

        assertEquals(WeeklyCareerEvidenceServiceImpl.MAX_APPLICATIONS,
                result.getApplications().size());
        assertTrue(result.getTruncated());
        assertEquals("BEST_EFFORT", result.getConsistencyLevel());
        assertTrue(result.getWarnings().contains("APPLICATIONS_TRUNCATED"));
        assertTrue(result.getApplications().get(0).getChannelKey().startsWith("CHANNEL:HASH_"));
        assertTrue(result.getExperiments().get(0).getTargetDirection().startsWith("DIRECTION:HASH_"));
        assertTrue(result.getApplications().get(0).getSourceHash().matches("sha256:[0-9a-f]{64}"));
        assertTrue(result.getApplications().get(0).getSafeSummary().length() <= 500);
        assertTrue(result.getApplications().get(0).getSafeSummary().startsWith("投递记录："));

        assertFalse(json.contains("alice@example.com"));
        assertFalse(json.contains("13800138000"));
        assertFalse(json.contains("top-secret"));
        assertFalse(json.contains("password=hidden"));
        assertFalse(json.contains("Secret Company"));
        assertFalse(json.contains("Private Candidate Job"));
        assertFalse(json.contains("private note"));
        assertFalse(json.contains("Recruiter email"));
        assertFalse(json.contains("Private interview"));
        assertFalse(json.contains("Private experiment"));
        assertFalse(json.contains("Private relation"));
    }

    private WeeklyCareerEvidenceVO collect(Long targetJobId) {
        return service.getWeeklyEvidence(
                USER_ID,
                RANGE_START,
                RANGE_END,
                CUTOFF,
                targetJobId,
                "Asia/Shanghai",
                null);
    }

    private void stubSources(
            Long targetJobId,
            List<JobApplication> applications,
            List<JobApplicationEvent> events,
            List<CareerCalendarEvent> calendarEvents,
            List<JobSearchExperiment> experiments) {
        when(jobApplicationMapper.selectWeeklyEvidenceApplications(
                eq(USER_ID),
                eq(RANGE_START),
                eq(RANGE_END),
                eq(CUTOFF),
                nullable(Long.class),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_APPLICATIONS + 1)))
                .thenReturn(applications);
        when(jobApplicationEventMapper.selectWeeklyEvidenceEvents(
                eq(USER_ID),
                eq(RANGE_START),
                eq(RANGE_END),
                eq(CUTOFF),
                nullable(Long.class),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_APPLICATION_EVENTS + 1)))
                .thenReturn(events);
        when(careerCalendarEventMapper.selectWeeklyEvidenceEvents(
                eq(USER_ID),
                eq(RANGE_START),
                eq(RANGE_END),
                eq(CUTOFF),
                nullable(Long.class),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_CALENDAR_EVENTS + 1)))
                .thenReturn(calendarEvents);
        when(experimentMapper.selectWeeklyEvidenceExperiments(
                eq(USER_ID),
                eq(LocalDate.of(2026, 7, 13)),
                eq(LocalDate.of(2026, 7, 20)),
                eq(CUTOFF),
                nullable(Long.class),
                nullable(List.class),
                eq(WeeklyCareerEvidenceServiceImpl.MAX_EXPERIMENTS + 1)))
                .thenReturn(experiments);
    }

    private static JobApplication application(
            Long id,
            Long userId,
            Long targetJobId,
            LocalDateTime appliedAt) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setUserId(userId);
        application.setTargetJobId(targetJobId);
        application.setSource("LINKEDIN");
        application.setStatus("APPLIED");
        application.setAppliedAt(appliedAt);
        application.setCreatedAt(RANGE_START.minusDays(2));
        application.setUpdatedAt(RANGE_START.minusDays(1));
        application.setDeleted(0);
        return application;
    }

    private static JobApplicationEvent event(
            Long id,
            Long userId,
            Long applicationId,
            String eventType,
            LocalDateTime eventTime) {
        JobApplicationEvent event = new JobApplicationEvent();
        event.setId(id);
        event.setUserId(userId);
        event.setApplicationId(applicationId);
        event.setEventType(eventType);
        event.setEventTime(eventTime);
        event.setCreatedAt(RANGE_START.minusHours(1));
        event.setUpdatedAt(RANGE_START);
        event.setDeleted(0);
        return event;
    }

    private static CareerCalendarEvent calendar(
            Long id,
            Long userId,
            Long applicationId,
            String eventType,
            LocalDateTime startsAtUtc) {
        CareerCalendarEvent event = new CareerCalendarEvent();
        event.setId(id);
        event.setUserId(userId);
        event.setApplicationId(applicationId);
        event.setEventType(eventType);
        event.setStartsAtUtc(startsAtUtc);
        event.setEndsAtUtc(startsAtUtc.plusHours(1));
        event.setStatus("CONFIRMED");
        event.setSourceType("MANUAL");
        event.setCreatedAt(RANGE_START.minusHours(1));
        event.setUpdatedAt(RANGE_START);
        event.setDeleted(0);
        return event;
    }

    private static JobSearchExperiment experiment(Long id, Long userId) {
        JobSearchExperiment experiment = new JobSearchExperiment();
        experiment.setId(id);
        experiment.setUserId(userId);
        experiment.setStartDate(LocalDate.of(2026, 7, 13));
        experiment.setEndDate(LocalDate.of(2026, 7, 19));
        experiment.setStatus("RUNNING");
        experiment.setDemoFlag(0);
        experiment.setCreatedAt(RANGE_START.minusDays(3));
        experiment.setUpdatedAt(RANGE_START.minusDays(1));
        experiment.setDeleted(0);
        return experiment;
    }

    private static JobSearchExperimentRelation relation(
            Long id,
            Long userId,
            Long experimentId,
            String relationType,
            Long relationId) {
        JobSearchExperimentRelation relation = new JobSearchExperimentRelation();
        relation.setId(id);
        relation.setUserId(userId);
        relation.setExperimentId(experimentId);
        relation.setRelationType(relationType);
        relation.setRelationId(relationId);
        relation.setDemoFlag(0);
        relation.setCreatedAt(RANGE_START.minusDays(1));
        relation.setUpdatedAt(RANGE_START.minusHours(1));
        relation.setDeleted(0);
        return relation;
    }

    private static TargetJob targetJob(Long id, Long userId) {
        TargetJob targetJob = new TargetJob();
        targetJob.setId(id);
        targetJob.setUserId(userId);
        targetJob.setCreatedAt(RANGE_START.minusDays(5));
        targetJob.setUpdatedAt(RANGE_START.minusDays(1));
        targetJob.setDeleted(0);
        return targetJob;
    }

    private static boolean isInternalCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]*");
    }
}
