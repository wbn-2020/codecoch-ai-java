package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.mq.domain.MqDispatchReceipt;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.dto.ResumeJobMatchCreateDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeAnalysisRecord;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.enums.ResumeParseStatus;
import com.codecoachai.resume.domain.vo.ResumeJobMatchSubmitVO;
import com.codecoachai.resume.feign.AiFeignClient;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ResumeAnalysisRecordMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.mq.ResumeJobMatchMqDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ResumeJobMatchServiceImplTest {

    private static final long USER_ID = 10L;
    private static final long RESUME_ID = 1L;
    private static final long TARGET_JOB_ID = 2L;
    private static final long JD_ANALYSIS_ID = 3L;
    private static final long RESUME_VERSION_ID = 4L;
    private static final LocalDateTime REPORT_TIME = LocalDateTime.of(2026, 6, 15, 8, 0);

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper projectMapper;
    @Mock
    private ResumeAnalysisRecordMapper analysisRecordMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private ResumeJobMatchReportMapper reportMapper;
    @Mock
    private ResumeJobMatchDetailMapper detailMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private AiFeignClient aiFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ResumeJobMatchMqDispatcher mqDispatcher;

    private ResumeJobMatchServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        initTableInfo(Resume.class);
        initTableInfo(ResumeProject.class);
        initTableInfo(ResumeAnalysisRecord.class);
        initTableInfo(TargetJob.class);
        initTableInfo(JobDescriptionAnalysis.class);
        initTableInfo(ResumeJobMatchReport.class);
        initTableInfo(ResumeVersion.class);
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
                .username("match-user")
                .build());
        service = new ResumeJobMatchServiceImpl(
                resumeMapper,
                projectMapper,
                analysisRecordMapper,
                targetJobMapper,
                jobDescriptionAnalysisMapper,
                reportMapper,
                detailMapper,
                resumeVersionMapper,
                aiFeignClient,
                new ObjectMapper(),
                transactionTemplate,
                Optional.of(mqDispatcher));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createReportReusesLatestSuccessWhenInputsAreNotNewer() {
        stubMatchContext(REPORT_TIME.minusHours(2));
        ResumeJobMatchReport existing = successReport(99L, REPORT_TIME);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        ResumeJobMatchSubmitVO result = service.createReport(createDto());

        assertEquals(99L, result.getReportId());
        assertEquals(ResumeJobMatchStatus.SUCCESS.getCode(), result.getStatus());
        verify(reportMapper, never()).insert(any(ResumeJobMatchReport.class));
        verify(mqDispatcher, never()).dispatchAnalyzeWithReceipt(any(), any());
    }

    @Test
    void createReportDoesNotReuseWhenResumeUpdatedAfterReport() {
        stubMatchContext(REPORT_TIME.plusMinutes(5));
        when(reportMapper.selectOne(any())).thenReturn(successReport(99L, REPORT_TIME));
        stubProcessingReportCreate(200L);

        ResumeJobMatchSubmitVO result = service.createReport(createDto());

        assertEquals(200L, result.getReportId());
        assertEquals("msg-200", result.getAsyncMessageId());
        assertEquals(ResumeJobMatchStatus.PROCESSING.getCode(), result.getStatus());
        verify(reportMapper).insert(any(ResumeJobMatchReport.class));
    }

    @Test
    void createReportDoesNotReuseWhenProjectUpdatedAfterReport() {
        stubMatchContext(
                REPORT_TIME.minusHours(2),
                REPORT_TIME.plusMinutes(5),
                REPORT_TIME.minusHours(2),
                REPORT_TIME.minusHours(2),
                REPORT_TIME.minusHours(2));
        when(reportMapper.selectOne(any())).thenReturn(successReport(99L, REPORT_TIME));
        stubProcessingReportCreate(201L);

        ResumeJobMatchSubmitVO result = service.createReport(createDto());

        assertEquals(201L, result.getReportId());
        assertEquals("msg-201", result.getAsyncMessageId());
        assertEquals(ResumeJobMatchStatus.PROCESSING.getCode(), result.getStatus());
        verify(reportMapper).insert(any(ResumeJobMatchReport.class));
    }

    @Test
    void createReportStoresResumeVersionWhenVersionIsRequested() {
        stubMatchContext(REPORT_TIME.minusHours(2));
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resumeVersion(RESUME_VERSION_ID, RESUME_ID));
        ResumeJobMatchReport existing = successReport(99L, REPORT_TIME);
        when(reportMapper.selectOne(any())).thenReturn(existing);
        stubProcessingReportCreate(202L);

        ResumeJobMatchSubmitVO result = service.createReport(createDto(RESUME_VERSION_ID));

        assertEquals(202L, result.getReportId());
        assertEquals(RESUME_VERSION_ID, result.getResumeVersionId());
        ArgumentCaptor<ResumeJobMatchReport> captor = ArgumentCaptor.forClass(ResumeJobMatchReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals(RESUME_VERSION_ID, captor.getValue().getResumeVersionId());
    }

    @Test
    void createReportRejectsVersionFromDifferentResume() {
        when(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resume(REPORT_TIME.minusHours(2)));
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resumeVersion(RESUME_VERSION_ID, 99L));

        assertThrows(BusinessException.class, () -> service.createReport(createDto(RESUME_VERSION_ID)));
        verify(reportMapper, never()).insert(any(ResumeJobMatchReport.class));
    }

    @Test
    void getReportEvidenceReturnsSuccessfulOwnedReportAndFiltersQuery() {
        ResumeJobMatchReport report = successReport(99L, REPORT_TIME);
        when(reportMapper.selectOne(any())).thenReturn(report);

        var evidence = service.getReportEvidence(USER_ID, 99L);

        assertEquals(99L, evidence.getId());
        assertEquals(USER_ID, evidence.getUserId());
        assertEquals(RESUME_ID, evidence.getResumeId());
        assertEquals(TARGET_JOB_ID, evidence.getTargetJobId());
        assertEquals(JD_ANALYSIS_ID, evidence.getJdAnalysisId());
        assertEquals(ResumeJobMatchStatus.SUCCESS.getCode(), evidence.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<ResumeJobMatchReport>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper).selectOne(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("user_id"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(sqlSegment.contains("deleted"));
    }

    @Test
    void getReportEvidenceRejectsMissingOrUnsuccessfulReport() {
        when(reportMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getReportEvidence(USER_ID, 99L));
    }

    private void stubMatchContext(LocalDateTime resumeUpdatedAt) {
        stubMatchContext(
                resumeUpdatedAt,
                REPORT_TIME.minusHours(2),
                REPORT_TIME.minusHours(2),
                REPORT_TIME.minusHours(2),
                REPORT_TIME.minusHours(2));
    }

    @SuppressWarnings("unchecked")
    private void stubMatchContext(
            LocalDateTime resumeUpdatedAt,
            LocalDateTime projectUpdatedAt,
            LocalDateTime resumeAnalysisUpdatedAt,
            LocalDateTime targetJobUpdatedAt,
            LocalDateTime jdAnalysisUpdatedAt) {
        when(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resume(resumeUpdatedAt));
        when(projectMapper.selectList(any())).thenReturn(List.of(project(projectUpdatedAt)));
        when(analysisRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resumeAnalysis(resumeAnalysisUpdatedAt));
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob(targetJobUpdatedAt));
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(jdAnalysis(jdAnalysisUpdatedAt));
    }

    private void stubProcessingReportCreate(long reportId) {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(reportMapper.insert(any(ResumeJobMatchReport.class))).thenAnswer(invocation -> {
            ResumeJobMatchReport report = invocation.getArgument(0);
            report.setId(reportId);
            report.setCreatedAt(REPORT_TIME.plusMinutes(6));
            report.setUpdatedAt(REPORT_TIME.plusMinutes(6));
            return 1;
        });
        when(mqDispatcher.dispatchAnalyzeWithReceipt(reportId, USER_ID)).thenReturn(MqDispatchReceipt.builder()
                .messageId("msg-" + reportId)
                .traceId("trace-" + reportId)
                .bizType("resume-job-match.analyze")
                .bizId(String.valueOf(reportId))
                .userId(USER_ID)
                .sendStatus("SENT")
                .build());
    }

    private ResumeJobMatchCreateDTO createDto() {
        return createDto(null);
    }

    private ResumeJobMatchCreateDTO createDto(Long resumeVersionId) {
        ResumeJobMatchCreateDTO dto = new ResumeJobMatchCreateDTO();
        dto.setResumeId(RESUME_ID);
        dto.setTargetJobId(TARGET_JOB_ID);
        dto.setResumeVersionId(resumeVersionId);
        return dto;
    }

    private Resume resume(LocalDateTime updatedAt) {
        Resume resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        resume.setTargetPosition("Java 后端工程师");
        resume.setSummary("有 Spring Cloud 项目经验");
        resume.setCreatedAt(updatedAt.minusDays(1));
        resume.setUpdatedAt(updatedAt);
        return resume;
    }

    private ResumeProject project(LocalDateTime updatedAt) {
        ResumeProject project = new ResumeProject();
        project.setId(11L);
        project.setResumeId(RESUME_ID);
        project.setProjectName("智能求职项目");
        project.setCreatedAt(updatedAt.minusDays(1));
        project.setUpdatedAt(updatedAt);
        return project;
    }

    private ResumeVersion resumeVersion(Long id, Long resumeId) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setUserId(USER_ID);
        version.setResumeId(resumeId);
        version.setVersionNo(2);
        version.setVersionName("面试投递版");
        version.setSnapshotJson("{\"targetPosition\":\"Java 后端工程师\",\"workExperience\":\"3 年\"}");
        version.setCreatedAt(REPORT_TIME.minusDays(1));
        version.setUpdatedAt(REPORT_TIME.minusHours(1));
        return version;
    }

    private ResumeAnalysisRecord resumeAnalysis(LocalDateTime updatedAt) {
        ResumeAnalysisRecord record = new ResumeAnalysisRecord();
        record.setId(12L);
        record.setUserId(USER_ID);
        record.setResumeId(RESUME_ID);
        record.setParseStatus(ResumeParseStatus.SUCCESS.getCode());
        record.setStructuredJson("{}");
        record.setCreatedAt(updatedAt.minusDays(1));
        record.setUpdatedAt(updatedAt);
        return record;
    }

    private TargetJob targetJob(LocalDateTime updatedAt) {
        TargetJob targetJob = new TargetJob();
        targetJob.setId(TARGET_JOB_ID);
        targetJob.setUserId(USER_ID);
        targetJob.setJobTitle("Java 后端工程师");
        targetJob.setJdText("Spring Cloud、Redis、MySQL");
        targetJob.setCreatedAt(updatedAt.minusDays(1));
        targetJob.setUpdatedAt(updatedAt);
        return targetJob;
    }

    private JobDescriptionAnalysis jdAnalysis(LocalDateTime updatedAt) {
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(JD_ANALYSIS_ID);
        analysis.setUserId(USER_ID);
        analysis.setTargetJobId(TARGET_JOB_ID);
        analysis.setParseStatus(JobDescriptionParseStatus.PARSED.getCode());
        analysis.setSummary("岗位要求 Java 微服务经验");
        analysis.setCreatedAt(updatedAt.minusDays(1));
        analysis.setUpdatedAt(updatedAt);
        return analysis;
    }

    private ResumeJobMatchReport successReport(Long id, LocalDateTime updatedAt) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setId(id);
        report.setUserId(USER_ID);
        report.setResumeId(RESUME_ID);
        report.setTargetJobId(TARGET_JOB_ID);
        report.setJdAnalysisId(JD_ANALYSIS_ID);
        report.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        report.setOverallScore(86);
        report.setAiCallLogId(66L);
        report.setSummary("匹配度较高");
        report.setCreatedAt(updatedAt.minusMinutes(5));
        report.setUpdatedAt(updatedAt);
        return report;
    }
}
