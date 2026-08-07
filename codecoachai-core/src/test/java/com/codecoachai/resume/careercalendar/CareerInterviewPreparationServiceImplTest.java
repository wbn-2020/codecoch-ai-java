package com.codecoachai.resume.careercalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobReadinessSnapshot;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.feign.InterviewEvidenceFeignClient;
import com.codecoachai.resume.feign.dto.GenerateInterviewPreparationAiDTO;
import com.codecoachai.resume.feign.vo.GenerateInterviewPreparationAiVO;
import com.codecoachai.resume.feign.vo.InterviewWeaknessSummaryVO;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobReadinessSnapshotMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mapper.careercalendar.CareerCalendarEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerInterviewPreparationServiceImplTest {

    private static final long USER_ID = 10L;

    @Mock
    private CareerCalendarEventMapper eventMapper;
    @Mock
    private JobApplicationMapper applicationMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper analysisMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private JobReadinessSnapshotMapper readinessSnapshotMapper;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private InterviewEvidenceFeignClient interviewEvidenceFeignClient;

    private ObjectMapper objectMapper;
    private CareerInterviewPreparationServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        init(JobDescriptionAnalysis.class);
        init(JobReadinessSnapshot.class);
        init(ProjectEvidence.class);
    }

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder()
                .userId(USER_ID)
                .username("preparation-user")
                .build());
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new CareerInterviewPreparationServiceImpl(
                eventMapper,
                applicationMapper,
                targetJobMapper,
                analysisMapper,
                resumeVersionMapper,
                projectEvidenceMapper,
                readinessSnapshotMapper,
                aiFeignClient,
                Optional.of(interviewEvidenceFeignClient),
                objectMapper);
        lenient().when(interviewEvidenceFeignClient.weaknessSummary(anyLong(), any()))
                .thenReturn(Result.success(new InterviewWeaknessSummaryVO()));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsOtherUsersAndNonInterviewEventsBeforeEvidenceLoading() {
        CareerCalendarEvent otherUsers = event(1L, "INTERVIEW");
        otherUsers.setUserId(99L);
        when(eventMapper.selectById(1L)).thenReturn(otherUsers);

        assertThrows(BusinessException.class,
                () -> service.generate(1L, new CareerInterviewPreparationGenerateDTO()));

        CareerCalendarEvent followUp = event(2L, "FOLLOW_UP");
        when(eventMapper.selectById(2L)).thenReturn(followUp);

        assertThrows(BusinessException.class,
                () -> service.generate(2L, new CareerInterviewPreparationGenerateDTO()));

        verify(applicationMapper, never()).selectById(anyLong());
        verify(aiFeignClient, never()).generateInterviewPreparation(any());
    }

    @Test
    void normalizesTimeBudgetAndBuildsLowConfidenceFallbackWithoutApplication() {
        CareerCalendarEvent event = event(3L, "TECHNICAL_INTERVIEW");
        when(eventMapper.selectById(3L)).thenReturn(event);
        stubSuccessfulStateMachine(event);
        CareerInterviewPreparationGenerateDTO request = new CareerInterviewPreparationGenerateDTO();
        request.setTimeBudgetMinutes(45);

        CareerInterviewPreparationVO result = service.generate(3L, request);

        assertEquals(60, result.getTimeBudgetMinutes());
        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getFallback());
        assertEquals("FALLBACK", result.getStatus());
        assertEquals(5, result.getFocusAreas().size());
        assertEquals(5, result.getPracticeQuestions().size());
        verify(aiFeignClient, never()).generateInterviewPreparation(any());
        verify(projectEvidenceMapper, never()).selectList(any());
    }

    @Test
    void persistsAiResultAndReusesSameSourceHashWithoutSecondAiCall() {
        CareerCalendarEvent event = linkedEvent(4L);
        JobApplication application = application();
        TargetJob targetJob = targetJob();
        ResumeVersion resumeVersion = resumeVersion();
        when(eventMapper.selectById(4L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application);
        when(targetJobMapper.selectById(50L)).thenReturn(targetJob);
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion);
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of());
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(null);

        GenerateInterviewPreparationAiVO ai = new GenerateInterviewPreparationAiVO();
        ai.setSummary("当前准备包基于已关联投递证据生成。");
        ai.setFocusAreas(List.of("Spring 事务边界"));
        ai.setPracticeQuestions(List.of("建议练习方向：说明事务传播机制。"));
        ai.setChecklist(List.of("确认会议链接。"));
        ai.setNextActions(List.of("复练一次回答。"));
        ai.setAiCallLogId(88L);
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(ai));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationGenerateDTO request = new CareerInterviewPreparationGenerateDTO();
        request.setTimeBudgetMinutes(30);
        CareerInterviewPreparationVO first = service.generate(4L, request);
        CareerInterviewPreparationVO repeated = service.generate(4L, request);

        assertFalse(first.getFallback());
        assertEquals("READY", first.getStatus());
        assertEquals(88L, first.getAiCallLogId());
        assertEquals(3, first.getFocusAreas().size());
        assertEquals(1, first.getProjectStories().size());
        assertEquals(3, first.getPracticeQuestions().size());
        assertEquals(first.getSourceHash(), repeated.getSourceHash());
        verify(aiFeignClient).generateInterviewPreparation(any());
    }

    @Test
    void reusesPreparationWhenOnlyPreparationSaveChangesEventUpdatedAt() {
        CareerCalendarEvent event = linkedEvent(6L);
        LocalDateTime originalUpdatedAt = event.getUpdatedAt();
        when(eventMapper.selectById(6L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application());
        when(targetJobMapper.selectById(50L)).thenReturn(targetJob());
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of());
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(null);

        GenerateInterviewPreparationAiVO ai = new GenerateInterviewPreparationAiVO();
        ai.setSummary("准备包仅生成一次。");
        ai.setFocusAreas(List.of("事务边界"));
        ai.setPracticeQuestions(List.of("建议练习方向：说明事务边界。"));
        ai.setChecklist(List.of("确认会议链接。"));
        ai.setNextActions(List.of("完成一次复练。"));
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(ai));
        stubSuccessfulStateMachine(event, true);

        CareerInterviewPreparationGenerateDTO request = new CareerInterviewPreparationGenerateDTO();
        request.setTimeBudgetMinutes(60);
        CareerInterviewPreparationVO first = service.generate(6L, request);
        CareerInterviewPreparationVO repeated = service.generate(6L, request);

        assertEquals(originalUpdatedAt.plusSeconds(1), event.getUpdatedAt());
        assertEquals(first.getSourceHash(), repeated.getSourceHash());
        verify(aiFeignClient).generateInterviewPreparation(any());
        verify(eventMapper, times(2)).compareAndSetPreparation(
                eq(6L), eq(USER_ID), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void returnsCasWinnerWhenConcurrentRequestSavesSameSourceHash() {
        CareerCalendarEvent event = linkedEvent(5L);
        when(eventMapper.selectById(5L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application());
        when(targetJobMapper.selectById(50L)).thenReturn(targetJob());
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(analysisMapper.selectOne(any())).thenReturn(null);
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of());
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(null);
        GenerateInterviewPreparationAiVO ai = new GenerateInterviewPreparationAiVO();
        ai.setSummary("并发生成的准备包");
        ai.setFocusAreas(List.of("一个关注点"));
        ai.setPracticeQuestions(List.of("建议练习方向：完成一个问题练习。"));
        ai.setChecklist(List.of("完成一项检查。"));
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(ai));
        when(eventMapper.compareAndSetPreparation(
                eq(5L), eq(USER_ID), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String status = invocation.getArgument(7);
                    applyPreparationState(event, invocation);
                    return "GENERATING".equals(status) ? 1 : 0;
                });

        CareerInterviewPreparationVO result =
                service.generate(5L, new CareerInterviewPreparationGenerateDTO());

        assertEquals(event.getPreparationSourceHash(), result.getSourceHash());
        assertEquals("READY", result.getStatus());
    }

    @Test
    void missingTargetJobNeverQueriesUserWideProjectEvidence() {
        CareerCalendarEvent event = linkedEvent(7L);
        JobApplication application = application();
        application.setTargetJobId(null);
        when(eventMapper.selectById(7L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application);
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(7L, new CareerInterviewPreparationGenerateDTO());

        assertEquals("LOW", result.getConfidenceLevel());
        verify(projectEvidenceMapper, never()).selectList(any());
        verify(readinessSnapshotMapper, never()).selectOne(any());
    }

    @Test
    void malformedStructuredSourcesAreOmittedAndWarnedWithoutSendingRawText() throws Exception {
        CareerCalendarEvent event = linkedEvent(8L);
        JobDescriptionAnalysis analysis = parsedAnalysis();
        analysis.setRequiredSkillsJson("RAW_SECRET_JD");
        analysis.setResponsibilitiesJson("[\"负责高并发服务\",{\"raw\":\"RAW_OBJECT\"}]");
        ResumeVersion version = resumeVersion();
        version.setSnapshotJson("RAW_SECRET_RESUME");
        JobReadinessSnapshot readiness = readiness();
        readiness.setSummaryJson("RAW_SECRET_SUMMARY");
        readiness.setMatrixJson("RAW_SECRET_READINESS");
        readiness.setDimensionJson("{\"raw\":\"RAW_DIMENSION\"}");

        when(eventMapper.selectById(8L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application());
        when(targetJobMapper.selectById(50L)).thenReturn(parsedTargetJob());
        when(analysisMapper.selectOne(any())).thenReturn(analysis);
        when(resumeVersionMapper.selectById(60L)).thenReturn(version);
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(projectEvidence(81L)));
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(readiness);
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        service.generate(8L, new CareerInterviewPreparationGenerateDTO());

        ArgumentCaptor<GenerateInterviewPreparationAiDTO> captor =
                ArgumentCaptor.forClass(GenerateInterviewPreparationAiDTO.class);
        verify(aiFeignClient).generateInterviewPreparation(captor.capture());
        String payload = objectMapper.writeValueAsString(captor.getValue());
        assertFalse(payload.contains("RAW_SECRET_JD"));
        assertFalse(payload.contains("RAW_OBJECT"));
        assertFalse(payload.contains("RAW_SECRET_RESUME"));
        assertFalse(payload.contains("RAW_SECRET_SUMMARY"));
        assertFalse(payload.contains("RAW_SECRET_READINESS"));
        assertFalse(payload.contains("RAW_DIMENSION"));
        assertTrue(captor.getValue().getSourceWarnings().stream()
                .anyMatch(value -> value.contains("解析失败") || value.contains("白名单")));
        assertTrue(captor.getValue().getSourceWarnings().stream()
                .allMatch(value -> value.matches(".*\\p{IsHan}.*")));
    }

    @Test
    void unsafeAiNaturalLanguageFallsBackAfterSanitization() {
        CareerCalendarEvent event = linkedEvent(9L);
        configureLinkedContext(event, null, List.of(), null);
        GenerateInterviewPreparationAiVO unsafe = new GenerateInterviewPreparationAiVO();
        unsafe.setSummary("English only summary");
        unsafe.setFacts(List.of("面试官认为你一定会通过"));
        unsafe.setLimits(List.of("guaranteed to pass"));
        unsafe.setFocusAreas(List.of("必考真题"));
        unsafe.setProjectStories(List.of("real interview question"));
        unsafe.setPracticeQuestions(List.of("will ask this"));
        unsafe.setChecklist(List.of("123456789012345678"));
        unsafe.setSchedule(List.of("0-10 minutes"));
        unsafe.setNextActions(List.of("English only"));
        unsafe.setAiCallLogId(99L);
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(unsafe));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(9L, new CareerInterviewPreparationGenerateDTO());

        assertTrue(result.getFallback());
        assertEquals("FALLBACK", result.getStatus());
        assertEquals("LOW", result.getConfidenceLevel());
        assertNull(result.getAiCallLogId());
        assertFalse(allNaturalLanguage(result).contains("English only"));
        assertFalse(allNaturalLanguage(result).contains("必考真题"));
    }

    @Test
    void safeChineseAiContentMasksPiiAndDropsOutOfBoundsItems() {
        CareerCalendarEvent event = linkedEvent(10L);
        configureLinkedContext(event, null, List.of(), null);
        GenerateInterviewPreparationAiVO mixed = safeAiResponse();
        mixed.setSummary("请联系 candidate@example.com 后复习事务边界。");
        mixed.setFocusAreas(List.of("复习 Spring 事务边界。", "English only focus", "必考真题"));
        mixed.setProjectStories(List.of("用真实项目说明责任边界，联系电话 13812345678。"));
        mixed.setPracticeQuestions(List.of(
                "建议练习方向：说明一次事务回滚的验证过程。",
                "面试官一定会问这道原题。"));
        mixed.setChecklist(List.of("确认备用电话 13912345678。"));
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(mixed));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(10L, new CareerInterviewPreparationGenerateDTO());

        String text = allNaturalLanguage(result);
        assertFalse(result.getFallback());
        assertTrue(text.contains("[邮箱已脱敏]"));
        assertTrue(text.contains("[电话已脱敏]"));
        assertFalse(text.contains("candidate@example.com"));
        assertFalse(text.contains("13812345678"));
        assertFalse(text.contains("13912345678"));
        assertFalse(text.contains("English only focus"));
        assertFalse(text.contains("原题"));
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("可信边界校验")));
    }

    @Test
    void readinessFallbackCapsOtherwiseHighConfidence() {
        CareerCalendarEvent event = linkedEvent(11L);
        JobReadinessSnapshot readiness = readiness();
        readiness.setFallback(1);
        readiness.setConfidenceLevel("HIGH");
        configureLinkedContext(event, parsedAnalysis(), List.of(projectEvidence(111L)), readiness);
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(11L, new CareerInterviewPreparationGenerateDTO());

        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("不会标记为高置信度")));
    }

    @Test
    void readinessLowConfidenceCapsOtherwiseHighConfidence() {
        CareerCalendarEvent event = linkedEvent(111L);
        JobReadinessSnapshot readiness = readiness();
        readiness.setFallback(0);
        readiness.setConfidenceLevel("LOW");
        configureLinkedContext(event, parsedAnalysis(), List.of(projectEvidence(1111L)), readiness);
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(111L, new CareerInterviewPreparationGenerateDTO());

        assertEquals("MEDIUM", result.getConfidenceLevel());
    }

    @Test
    void unfinishedJdParseCapsOtherwiseStrongContext() {
        CareerCalendarEvent event = linkedEvent(112L);
        TargetJob targetJob = targetJob();
        targetJob.setParseStatus("PARSING");
        JobDescriptionAnalysis analysis = parsedAnalysis();
        analysis.setParseStatus("PARSING");
        when(eventMapper.selectById(112L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application());
        when(targetJobMapper.selectById(50L)).thenReturn(targetJob);
        when(analysisMapper.selectOne(any())).thenReturn(analysis);
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(projectEvidence(1121L)));
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(readiness());
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(112L, new CareerInterviewPreparationGenerateDTO());

        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("JD 解析尚未完成")));
    }

    @Test
    void failedOptionalSourceCapsOtherwiseHighConfidence() {
        CareerCalendarEvent event = linkedEvent(113L);
        configureLinkedContext(event, parsedAnalysis(), List.of(projectEvidence(1131L)), readiness());
        when(interviewEvidenceFeignClient.weaknessSummary(anyLong(), any()))
                .thenThrow(new IllegalStateException("interview source unavailable"));
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO result =
                service.generate(113L, new CareerInterviewPreparationGenerateDTO());

        assertEquals("MEDIUM", result.getConfidenceLevel());
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("证据加载失败")));
    }

    @Test
    void sourceHashUsesAllNormalizedAiInputFields() {
        CareerCalendarEvent event = linkedEvent(12L);
        JobApplication application = application();
        when(eventMapper.selectById(12L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application);
        when(targetJobMapper.selectById(50L)).thenReturn(parsedTargetJob());
        when(analysisMapper.selectOne(any())).thenReturn(parsedAnalysis());
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(projectEvidence(121L)));
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(readiness());
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO first =
                service.generate(12L, new CareerInterviewPreparationGenerateDTO());
        application.setCompanyName("Changed Company");
        CareerInterviewPreparationVO second =
                service.generate(12L, new CareerInterviewPreparationGenerateDTO());

        assertNotEquals(first.getSourceHash(), second.getSourceHash());
        verify(aiFeignClient, times(2)).generateInterviewPreparation(any());
    }

    @Test
    void projectEvidenceOrderingIncludesIdTieBreaker() {
        CareerCalendarEvent event = linkedEvent(13L);
        when(eventMapper.selectById(13L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application());
        when(targetJobMapper.selectById(50L)).thenReturn(parsedTargetJob());
        when(analysisMapper.selectOne(any())).thenReturn(parsedAnalysis());
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(projectEvidenceMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<ProjectEvidence> wrapper = invocation.getArgument(0);
            String sql = wrapper.getCustomSqlSegment().toLowerCase();
            assertTrue(sql.contains("order by"));
            assertTrue(sql.contains("id desc"));
            return List.of(projectEvidence(131L));
        });
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(readiness());
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        service.generate(13L, new CareerInterviewPreparationGenerateDTO());

        verify(projectEvidenceMapper).selectList(any());
    }

    @Test
    void getMarksSavedPreparationStaleWhenCalendarEventWasUpdated() throws Exception {
        CareerCalendarEvent event = linkedEvent(14L);
        CareerInterviewPreparationVO saved = new CareerInterviewPreparationVO();
        saved.setStatus("READY");
        saved.setSummary("旧准备包");
        saved.setSourceHash("old-hash");
        event.setPreparationJson(objectMapper.writeValueAsString(saved));
        event.setPreparationStatus("STALE");
        event.setPreparationSourceHash("old-hash");
        event.setPreparationGeneratedAt(LocalDateTime.of(2026, 7, 18, 11, 0));
        when(eventMapper.selectById(14L)).thenReturn(event);

        CareerInterviewPreparationVO result = service.get(14L);

        assertEquals("STALE", result.getStatus());
        assertTrue(result.getStale());
        assertNotNull(result.getStaleReason());
        assertTrue(result.getLimits().stream().anyMatch(value -> value.contains("不能作为本次面试的当前结果")));
    }

    @Test
    void getMarksPreparationStaleWhenLinkedEvidenceChanges() {
        CareerCalendarEvent event = linkedEvent(114L);
        JobApplication application = application();
        when(eventMapper.selectById(114L)).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application);
        when(targetJobMapper.selectById(50L)).thenReturn(parsedTargetJob());
        when(analysisMapper.selectOne(any())).thenReturn(parsedAnalysis());
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(projectEvidence(1141L)));
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(readiness());
        when(aiFeignClient.generateInterviewPreparation(any())).thenReturn(Result.success(safeAiResponse()));
        stubSuccessfulStateMachine(event);

        CareerInterviewPreparationVO generated =
                service.generate(114L, new CareerInterviewPreparationGenerateDTO());
        application.setCompanyName("Changed Company");

        CareerInterviewPreparationVO result = service.get(114L);

        assertEquals("READY", generated.getStatus());
        assertEquals("STALE", result.getStatus());
        assertTrue(result.getStale());
        assertTrue(result.getStaleReason().contains("证据已更新"));
        verify(eventMapper).markPreparationStale(114L, USER_ID);
    }

    @Test
    void concurrentSameHashRequestsShareOneFinalResultAndOneAiCall() throws Exception {
        CareerCalendarEvent state = linkedEvent(15L);
        configureLinkedContext(state, null, List.of(), null);
        Object stateLock = new Object();
        when(eventMapper.selectById(15L)).thenAnswer(invocation -> {
            synchronized (stateLock) {
                return copyEvent(state);
            }
        });
        when(eventMapper.compareAndSetPreparation(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    synchronized (stateLock) {
                        if (!Objects.equals(state.getUpdatedAt(), invocation.getArgument(2))
                                || !Objects.equals(state.getPreparationStatus(), invocation.getArgument(3))
                                || !Objects.equals(state.getPreparationSourceHash(), invocation.getArgument(4))
                                || !Objects.equals(state.getPreparationGeneratedAt(), invocation.getArgument(5))) {
                            return 0;
                        }
                        applyPreparationState(state, invocation);
                        return 1;
                    }
                });
        AtomicInteger aiCalls = new AtomicInteger();
        CountDownLatch aiEntered = new CountDownLatch(1);
        when(aiFeignClient.generateInterviewPreparation(any())).thenAnswer(invocation -> {
            aiCalls.incrementAndGet();
            aiEntered.countDown();
            Thread.sleep(150);
            return Result.success(safeAiResponse());
        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CareerInterviewPreparationVO> first = executor.submit(() -> {
                LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("first").build());
                try {
                    start.await();
                    return service.generate(15L, new CareerInterviewPreparationGenerateDTO());
                } finally {
                    LoginUserContext.clear();
                }
            });
            Future<CareerInterviewPreparationVO> second = executor.submit(() -> {
                LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("second").build());
                try {
                    start.await();
                    return service.generate(15L, new CareerInterviewPreparationGenerateDTO());
                } finally {
                    LoginUserContext.clear();
                }
            });

            start.countDown();
            assertTrue(aiEntered.await(2, TimeUnit.SECONDS));
            CareerInterviewPreparationVO firstResult = first.get(5, TimeUnit.SECONDS);
            CareerInterviewPreparationVO secondResult = second.get(5, TimeUnit.SECONDS);

            assertEquals(firstResult.getSourceHash(), secondResult.getSourceHash());
            assertEquals("READY", firstResult.getStatus());
            assertEquals("READY", secondResult.getStatus());
            assertEquals(1, aiCalls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private void configureLinkedContext(
            CareerCalendarEvent event,
            JobDescriptionAnalysis analysis,
            List<ProjectEvidence> projects,
            JobReadinessSnapshot readiness) {
        when(eventMapper.selectById(event.getId())).thenReturn(event);
        when(applicationMapper.selectById(40L)).thenReturn(application());
        when(targetJobMapper.selectById(50L)).thenReturn(parsedTargetJob());
        when(analysisMapper.selectOne(any())).thenReturn(analysis);
        when(resumeVersionMapper.selectById(60L)).thenReturn(resumeVersion());
        when(projectEvidenceMapper.selectList(any())).thenReturn(projects);
        when(readinessSnapshotMapper.selectOne(any())).thenReturn(readiness);
    }

    private void stubSuccessfulStateMachine(CareerCalendarEvent event) {
        stubSuccessfulStateMachine(event, false);
    }

    private void stubSuccessfulStateMachine(
            CareerCalendarEvent event,
            boolean bumpUpdatedAtAfterCompletion) {
        when(eventMapper.compareAndSetPreparation(
                eq(event.getId()), eq(USER_ID), any(), any(), any(), any(),
                any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    applyPreparationState(event, invocation);
                    String status = invocation.getArgument(7);
                    if (bumpUpdatedAtAfterCompletion && !"GENERATING".equals(status)) {
                        event.setUpdatedAt(event.getUpdatedAt().plusSeconds(1));
                    }
                    return 1;
                });
    }

    private void applyPreparationState(
            CareerCalendarEvent event,
            InvocationOnMock invocation) {
        event.setPreparationJson(invocation.getArgument(6));
        event.setPreparationStatus(invocation.getArgument(7));
        event.setPreparationAiCallLogId(invocation.getArgument(8));
        event.setPreparationGeneratedAt(invocation.getArgument(9));
        event.setPreparationSourceHash(invocation.getArgument(10));
    }

    private CareerCalendarEvent copyEvent(CareerCalendarEvent source) {
        CareerCalendarEvent copy = new CareerCalendarEvent();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setApplicationId(source.getApplicationId());
        copy.setTitle(source.getTitle());
        copy.setEventType(source.getEventType());
        copy.setStartsAtUtc(source.getStartsAtUtc());
        copy.setEndsAtUtc(source.getEndsAtUtc());
        copy.setTimezone(source.getTimezone());
        copy.setAllDayFlag(source.getAllDayFlag());
        copy.setLocation(source.getLocation());
        copy.setDescription(source.getDescription());
        copy.setStatus(source.getStatus());
        copy.setDeleted(source.getDeleted());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setPreparationJson(source.getPreparationJson());
        copy.setPreparationStatus(source.getPreparationStatus());
        copy.setPreparationAiCallLogId(source.getPreparationAiCallLogId());
        copy.setPreparationGeneratedAt(source.getPreparationGeneratedAt());
        copy.setPreparationSourceHash(source.getPreparationSourceHash());
        return copy;
    }

    private GenerateInterviewPreparationAiVO safeAiResponse() {
        GenerateInterviewPreparationAiVO ai = new GenerateInterviewPreparationAiVO();
        ai.setSummary("当前准备包基于已关联证据生成。");
        ai.setFocusAreas(List.of("复习事务边界并说明验证步骤。"));
        ai.setProjectStories(List.of("用真实项目说明职责、取舍和可核验结果。"));
        ai.setPracticeQuestions(List.of("建议练习方向：说明一次故障定位和验证过程。"));
        ai.setChecklist(List.of("确认会议链接和设备状态。"));
        ai.setSchedule(List.of("0-15 分钟：复习岗位要求。"));
        ai.setNextActions(List.of("完成一次计时复练。"));
        ai.setAiCallLogId(88L);
        return ai;
    }

    private TargetJob parsedTargetJob() {
        TargetJob job = targetJob();
        job.setParseStatus("PARSED");
        return job;
    }

    private JobDescriptionAnalysis parsedAnalysis() {
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(70L);
        analysis.setUserId(USER_ID);
        analysis.setTargetJobId(50L);
        analysis.setParseStatus("PARSED");
        analysis.setRequiredSkillsJson("[\"Spring Boot\",\"MySQL\"]");
        analysis.setResponsibilitiesJson("[\"负责高并发服务\"]");
        analysis.setInterviewFocusJson("[\"事务边界\"]");
        analysis.setSummary("后端工程岗位，关注稳定性和事务边界。");
        analysis.setDeleted(0);
        analysis.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 8, 10));
        return analysis;
    }

    private JobReadinessSnapshot readiness() {
        JobReadinessSnapshot readiness = new JobReadinessSnapshot();
        readiness.setId(80L);
        readiness.setUserId(USER_ID);
        readiness.setTargetJobId(50L);
        readiness.setSnapshotHash("readiness-hash");
        readiness.setConfidenceLevel("HIGH");
        readiness.setFallback(0);
        readiness.setRequirementCount(3);
        readiness.setStrongCount(2);
        readiness.setWeakCount(1);
        readiness.setMissingCount(0);
        readiness.setMatrixJson("""
                {"requirements":[
                  {"requirementName":"Spring 事务","coverageLevel":"WEAK","priority":"MUST",
                   "requirementConfidence":"HIGH","requirementFallback":false}
                ]}
                """);
        readiness.setDimensionJson("[]");
        readiness.setSummaryJson("{\"confidenceLevel\":\"HIGH\",\"fallback\":false}");
        readiness.setGeneratedAt(LocalDateTime.of(2026, 7, 18, 8, 20));
        readiness.setDeleted(0);
        return readiness;
    }

    private ProjectEvidence projectEvidence(Long id) {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(id);
        project.setUserId(USER_ID);
        project.setTargetJobId(50L);
        project.setTitle("订单一致性治理");
        project.setRole("后端负责人");
        project.setTechStack("Java, Spring Boot, MySQL");
        project.setSolution("通过事务边界和幂等机制控制重复写入。");
        project.setResult("线上重复写入问题得到可核验改善。");
        project.setCompletenessScore(90);
        project.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 8, 15));
        project.setDeleted(0);
        return project;
    }

    private String allNaturalLanguage(CareerInterviewPreparationVO vo) {
        return String.join("\n",
                text(vo.getSummary()),
                String.join("\n", vo.getFacts()),
                String.join("\n", vo.getLimits()),
                String.join("\n", vo.getFocusAreas()),
                String.join("\n", vo.getProjectStories()),
                String.join("\n", vo.getPracticeQuestions()),
                String.join("\n", vo.getChecklist()),
                String.join("\n", vo.getSchedule()),
                String.join("\n", vo.getNextActions()));
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private static void init(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    private CareerCalendarEvent event(Long id, String type) {
        CareerCalendarEvent event = new CareerCalendarEvent();
        event.setId(id);
        event.setUserId(USER_ID);
        event.setTitle("Technical interview");
        event.setEventType(type);
        event.setStartsAtUtc(LocalDateTime.of(2026, 7, 20, 2, 0));
        event.setEndsAtUtc(LocalDateTime.of(2026, 7, 20, 3, 0));
        event.setTimezone("Asia/Shanghai");
        event.setStatus("CONFIRMED");
        event.setDeleted(0);
        event.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 10, 0));
        return event;
    }

    private CareerCalendarEvent linkedEvent(Long id) {
        CareerCalendarEvent event = event(id, "INTERVIEW_SCHEDULED");
        event.setApplicationId(40L);
        return event;
    }

    private JobApplication application() {
        JobApplication application = new JobApplication();
        application.setId(40L);
        application.setUserId(USER_ID);
        application.setTargetJobId(50L);
        application.setResumeVersionId(60L);
        application.setCompanyName("Example Co");
        application.setJobTitle("Backend Engineer");
        application.setStatus("INTERVIEWING");
        application.setDeleted(0);
        application.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 9, 0));
        return application;
    }

    private TargetJob targetJob() {
        TargetJob job = new TargetJob();
        job.setId(50L);
        job.setUserId(USER_ID);
        job.setCompanyName("Example Co");
        job.setJobTitle("Backend Engineer");
        job.setJdText("Spring Boot, MySQL, Redis");
        job.setDeleted(0);
        job.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 8, 0));
        return job;
    }

    private ResumeVersion resumeVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setId(60L);
        version.setUserId(USER_ID);
        version.setVersionNo(3);
        version.setVersionName("Backend V3");
        version.setSnapshotJson("{\"targetPosition\":\"Backend Engineer\",\"skillStack\":[\"Java\",\"Spring\"]}");
        version.setDeleted(0);
        version.setUpdatedAt(LocalDateTime.of(2026, 7, 18, 8, 30));
        return version;
    }
}
