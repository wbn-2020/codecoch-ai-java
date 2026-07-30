package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobApplicationPackageSnapshot;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.codecoachai.resume.domain.vo.JobReadinessSnapshotVO;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchDetailMapper;
import com.codecoachai.resume.mapper.ResumeJobMatchReportMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.JobReadinessService;
import com.codecoachai.resume.service.JobRequirementService;
import com.codecoachai.resume.service.V4ResumeCareerService;
import com.codecoachai.resume.service.support.JobApplicationPackageSnapshotManager;
import com.codecoachai.resume.service.support.ResumeJobMatchTrustPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JobApplicationPackageServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final Long TARGET_JOB_ID = 11L;
    private static final Long PROJECT_ID = 31L;
    private static final Long MATCH_REPORT_ID = 61L;
    private static final Long PACKAGE_ID = 71L;

    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private ResumeJobMatchReportMapper resumeJobMatchReportMapper;
    @Mock
    private ResumeJobMatchDetailMapper resumeJobMatchDetailMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private JobRequirementService jobRequirementService;
    @Mock
    private JobReadinessService jobReadinessService;
    @Mock
    private V4ResumeCareerService v4ResumeCareerService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private JobApplicationPackageSnapshotManager packageSnapshotManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private JobApplicationPackageServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(TargetJob.class);
        initTableInfo(JobDescriptionAnalysis.class);
        initTableInfo(ResumeVersion.class);
        initTableInfo(ResumeJobMatchReport.class);
        initTableInfo(ProjectEvidence.class);
    }

    @BeforeEach
    void setUp() {
        service = new JobApplicationPackageServiceImpl(
                targetJobMapper,
                jobDescriptionAnalysisMapper,
                resumeVersionMapper,
                resumeJobMatchReportMapper,
                resumeJobMatchDetailMapper,
                projectEvidenceMapper,
                jobRequirementService,
                jobReadinessService,
                v4ResumeCareerService,
                objectMapper,
                jdbcTemplate,
                packageSnapshotManager,
                new ResumeJobMatchTrustPolicy(objectMapper));
        LoginUser user = new LoginUser();
        user.setUserId(USER_ID);
        LoginUserContext.setLoginUser(user);
        lenient().when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis());
        lenient().when(resumeVersionMapper.selectOne(any())).thenReturn(resumeVersion());
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(null);
        lenient().when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(completeProject()));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void previewInheritsReadyOnlyFromCurrentTrustedSnapshot() {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "STRONG", strongEvidence(PROJECT_ID)),
                requirement(102L, "Redis", "MUST", "STRONG", strongEvidence(PROJECT_ID)));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID)).thenReturn(snapshot(matrix, "READY", false, "HIGH"));

        var preview = service.preview(TARGET_JOB_ID, null, null, null, List.of(PROJECT_ID));

        assertEquals("READY", preview.getReadinessLevel());
        assertEquals(88, preview.getReadinessScore());
        assertFalse(preview.getFallback());
        assertNotNull(preview.getRequirementReadinessSource());
        assertEquals(901L, preview.getRequirementReadinessSource().getSnapshotId());
        assertEquals("requirement-evidence-v1", preview.getRequirementReadinessSource().getPolicyVersion());
        assertEquals(2, preview.getProjectEvidenceCoverage().getCoveredRequirements().size());
    }

    @Test
    void completeProjectDoesNotCreateCoverageWithoutStableRequirementEvidence() {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "MISSING", null),
                requirement(102L, "Redis", "MUST", "MISSING", null));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID)).thenReturn(snapshot(matrix, "NEEDS_WORK", false, "HIGH"));

        var preview = service.preview(TARGET_JOB_ID, null, null, null, List.of(PROJECT_ID));

        assertTrue(preview.getProjectEvidenceCoverage().getCoveredRequirements().isEmpty());
        assertEquals(List.of("Java", "Redis"),
                preview.getProjectEvidenceCoverage().getInsufficientRequirements());
        assertEquals("NEEDS_EVIDENCE", preview.getReadinessLevel());
    }

    @Test
    void missingSnapshotConservativelyBlocksReadyAndPersistsWarningsInVo() {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "STRONG", strongEvidence(PROJECT_ID)),
                requirement(102L, "Redis", "MUST", "STRONG", strongEvidence(PROJECT_ID)));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID)).thenReturn(null);

        var preview = service.preview(TARGET_JOB_ID, null, null, null, List.of(PROJECT_ID));

        assertEquals("NEEDS_EVIDENCE", preview.getReadinessLevel());
        assertTrue(preview.getFallback());
        assertTrue(preview.getRequirementReadinessSource().getWarnings()
                .contains("READINESS_SNAPSHOT_MISSING"));
        assertTrue(preview.getFallbackReason().contains("READINESS_SNAPSHOT_MISSING"));
    }

    @Test
    void schemaWarningMatchIsPartialNonFallbackAndNotTrustedForDownstreamUse() {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "STRONG", strongEvidence(PROJECT_ID)),
                requirement(102L, "Redis", "MUST", "STRONG", strongEvidence(PROJECT_ID)));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID)).thenReturn(snapshot(matrix, "READY", false, "HIGH"));
        JobDescriptionAnalysis trustedAnalysis = analysis();
        trustedAnalysis.setInterviewFocusJson("[\"JD concurrency topic\"]");
        trustedAnalysis.setRawResultJson("""
                {"trustStatus":"VERIFIED","fallback":false,"schemaWarnings":[]}
                """);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(trustedAnalysis);
        ResumeJobMatchReport partialReport = matchReport("""
                {
                  "trustStatus": "PARTIAL",
                  "fallback": false,
                  "schemaWarnings": [
                    {"field":"evidenceBoundary","message":"unsupported evidence removed"}
                  ]
                }
                """);
        partialReport.setGapsJson("[\"REPORT_ONLY_GAP\"]");
        partialReport.setRecommendedInterviewTopicsJson("[\"REPORT_ONLY_TOPIC\"]");
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(partialReport);

        var preview = service.preview(
                TARGET_JOB_ID, null, null, MATCH_REPORT_ID, List.of(PROJECT_ID));

        assertNull(preview.getMatchReportId());
        assertEquals("PARTIAL", preview.getMatchSummary().getTrustStatus());
        assertFalse(preview.getMatchSummary().getFallback());
        assertEquals(1, preview.getMatchSummary().getSchemaWarningCount());
        assertNull(preview.getMatchSummary().getOverallScore());
        assertNull(preview.getMatchSummary().getSummary());
        assertTrue(preview.getMatchSummary().getGaps().isEmpty());
        assertEquals(List.of("JD concurrency topic"), preview.getMatchSummary().getInterviewTopics());
        assertEquals(List.of("JD concurrency topic"), preview.getInterviewPreparation().getTopics());
        assertFalse(preview.getInterviewPreparation().getTopics().contains("REPORT_ONLY_TOPIC"));
        assertNull(preview.getInterviewPreparation().getCreateParams().get("matchReportId"));
        assertFalse(preview.getInterviewPreparation().getEntryUrl().contains(String.valueOf(MATCH_REPORT_ID)));
        assertTrue(preview.getEvidenceSources().stream()
                .noneMatch(source -> "RESUME_JOB_MATCH_REPORT".equals(source.getSourceType())));
        assertTrue(preview.getRecommendedResume().getReason().contains("尚未形成可信"));
        assertEquals("MEDIUM", preview.getSuggestions().stream()
                .filter(suggestion -> "resume".equals(suggestion.getId()))
                .findFirst()
                .orElseThrow()
                .getConfidence());
        assertTrue(preview.getChecklist().stream()
                .flatMap(item -> item.getEvidenceSourceIds().stream())
                .noneMatch("match"::equals));
        assertTrue(preview.getActions().stream()
                .flatMap(action -> action.getEvidenceSourceIds().stream())
                .noneMatch("match"::equals));
        assertTrue(preview.getSuggestions().stream()
                .flatMap(suggestion -> suggestion.getEvidenceSourceIds().stream())
                .noneMatch("match"::equals));
        assertFalse(preview.getId().contains(String.valueOf(MATCH_REPORT_ID)));
        assertFalse(preview.getTrace().getInputSummary().contains(String.valueOf(MATCH_REPORT_ID)));
    }

    @Test
    void verifiedMatchIsTrustedForDownstreamUse() {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "STRONG", strongEvidence(PROJECT_ID)),
                requirement(102L, "Redis", "MUST", "STRONG", strongEvidence(PROJECT_ID)));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID)).thenReturn(snapshot(matrix, "READY", false, "HIGH"));
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(matchReport("""
                {"trustStatus":"VERIFIED","fallback":false,"schemaWarnings":[]}
                """));

        var preview = service.preview(
                TARGET_JOB_ID, null, null, MATCH_REPORT_ID, List.of(PROJECT_ID));

        assertEquals("VERIFIED", preview.getMatchSummary().getTrustStatus());
        assertFalse(preview.getMatchSummary().getFallback());
        assertEquals(MATCH_REPORT_ID, preview.getMatchReportId());
        assertEquals("Grounded match result", preview.getMatchSummary().getSummary());
        assertEquals(List.of("Java scenarios"), preview.getInterviewPreparation().getTopics());
        assertTrue(preview.getEvidenceSources().stream()
                .anyMatch(source -> "RESUME_JOB_MATCH_REPORT".equals(source.getSourceType())));
        assertEquals("HIGH", preview.getSuggestions().stream()
                .filter(suggestion -> "resume".equals(suggestion.getId()))
                .findFirst()
                .orElseThrow()
                .getConfidence());
        assertEquals(MATCH_REPORT_ID,
                preview.getInterviewPreparation().getCreateParams().get("matchReportId"));
    }

    @Test
    void rawFallbackMatchRemainsFallbackAndNotTrustedForDownstreamUse() {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "STRONG", strongEvidence(PROJECT_ID)),
                requirement(102L, "Redis", "MUST", "STRONG", strongEvidence(PROJECT_ID)));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID)).thenReturn(snapshot(matrix, "READY", false, "HIGH"));
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(matchReport("""
                {"trustStatus":"VERIFIED","fallback":true,"schemaWarnings":[]}
                """));

        var preview = service.preview(
                TARGET_JOB_ID, null, null, MATCH_REPORT_ID, List.of(PROJECT_ID));

        assertEquals("FALLBACK", preview.getMatchSummary().getTrustStatus());
        assertTrue(preview.getMatchSummary().getFallback());
        assertNull(preview.getMatchReportId());
        assertNull(preview.getMatchSummary().getSummary());
        assertTrue(preview.getMatchSummary().getGaps().isEmpty());
        assertNull(preview.getInterviewPreparation().getCreateParams().get("matchReportId"));
        assertFalse(preview.getInterviewPreparation().getTopics().contains("Java scenarios"));
        assertTrue(preview.getEvidenceSources().stream()
                .noneMatch(source -> "RESUME_JOB_MATCH_REPORT".equals(source.getSourceType())));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void legacySnapshotWithPartialMatchIsSanitizedWhenRead() throws Exception {
        ResumeJobMatchReport partialReport = matchReport("""
                {
                  "trustStatus":"VERIFIED",
                  "fallback":false,
                  "schemaWarnings":[{"field":"legacy","message":"requires review"}]
                }
                """);
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(partialReport);
        JobDescriptionAnalysis trustedAnalysis = analysis();
        trustedAnalysis.setInterviewFocusJson("[\"Trusted JD topic\"]");
        trustedAnalysis.setRawResultJson("""
                {"trustStatus":"VERIFIED","fallback":false,"schemaWarnings":[]}
                """);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(trustedAnalysis);
        String legacySnapshot = """
                {
                  "matchReportId":61,
                  "targetJobId":11,
                  "jdAnalysisId":21,
                  "recommendedResumeVersionId":41,
                  "projectEvidenceIds":[31],
                  "matchSummary":{
                    "overallScore":88,
                    "trustStatus":"VERIFIED",
                    "fallback":false,
                    "schemaWarningCount":0,
                    "summary":"LEGACY_REPORT_SUMMARY",
                    "gaps":["LEGACY_REPORT_GAP"],
                    "interviewTopics":["LEGACY_REPORT_TOPIC"]
                  },
                  "interviewPreparation":{
                    "entryUrl":"/interviews/create?matchReportId=61",
                    "topics":["LEGACY_REPORT_TOPIC"],
                    "createParams":{"matchReportId":61}
                  },
                  "recommendedResume":{"reason":"LEGACY_TRUSTED_REASON"},
                  "evidenceSources":[{
                    "id":"match",
                    "sourceType":"RESUME_JOB_MATCH_REPORT",
                    "sourceId":"61",
                    "summary":"LEGACY_REPORT_SUMMARY"
                  }],
                  "checklist":[{
                    "key":"MATCH_SCORE_THRESHOLD",
                    "passed":true,
                    "evidenceSourceIds":["match"]
                  }],
                  "actions":[{
                    "id":"create-application",
                    "actionType":"CREATE_APPLICATION_RECORD",
                    "evidenceSourceIds":["match"]
                  }],
                  "riskSignals":[{
                    "key":"MATCH_SCORE_LOW",
                    "evidenceSourceIds":["match"]
                  }],
                  "suggestions":[{
                    "id":"resume",
                    "confidence":"HIGH",
                    "evidenceSourceIds":["match"]
                  }],
                  "trace":{
                    "traceId":"legacy:61",
                    "inputSummary":"matchReportId=61"
                  }
                }
                """;
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    if (sql.contains("SELECT *")
                            && sql.contains("FROM job_application_package")) {
                        return List.of(mapper.mapRow(
                                packageRowResultSet(1, 901L, MATCH_REPORT_ID, legacySnapshot), 0));
                    }
                    return List.of();
                });
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        var detail = service.detail(PACKAGE_ID);

        assertNull(detail.getMatchReportId());
        assertEquals("PARTIAL", detail.getMatchSummary().getTrustStatus());
        assertFalse(detail.getMatchSummary().getFallback());
        assertEquals(1, detail.getMatchSummary().getSchemaWarningCount());
        assertNull(detail.getMatchSummary().getOverallScore());
        assertNull(detail.getMatchSummary().getSummary());
        assertTrue(detail.getMatchSummary().getGaps().isEmpty());
        assertEquals(List.of("Trusted JD topic"), detail.getMatchSummary().getInterviewTopics());
        assertEquals(List.of("Trusted JD topic"), detail.getInterviewPreparation().getTopics());
        assertNull(detail.getInterviewPreparation().getCreateParams().get("matchReportId"));
        assertTrue(detail.getEvidenceSources().isEmpty());
        assertEquals("MEDIUM", detail.getSuggestions().get(0).getConfidence());
        assertTrue(detail.getChecklist().stream()
                .flatMap(item -> item.getEvidenceSourceIds().stream())
                .noneMatch("match"::equals));
        assertTrue(detail.getActions().stream()
                .flatMap(action -> action.getEvidenceSourceIds().stream())
                .noneMatch("match"::equals));
        assertFalse(detail.getTrace().getTraceId().contains(String.valueOf(MATCH_REPORT_ID)));
        assertFalse(detail.getTrace().getInputSummary().contains(String.valueOf(MATCH_REPORT_ID)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void refreshPersistsAndPublishesTheActualImmutableSnapshotVersion() throws Exception {
        JobRequirementMatrixVO matrix = matrix(
                requirement(101L, "Java", "MUST", "STRONG", strongEvidence(PROJECT_ID)),
                requirement(102L, "Redis", "MUST", "STRONG", strongEvidence(PROJECT_ID)));
        when(jobRequirementService.getMatrix(TARGET_JOB_ID)).thenReturn(matrix);
        when(jobReadinessService.latest(TARGET_JOB_ID))
                .thenReturn(snapshot(matrix, "READY", false, "HIGH"));

        JobApplicationPackageSnapshot immutableSnapshot = new JobApplicationPackageSnapshot();
        immutableSnapshot.setId(907L);
        immutableSnapshot.setPackageId(PACKAGE_ID);
        immutableSnapshot.setUserId(USER_ID);
        immutableSnapshot.setSnapshotVersion(7);
        when(packageSnapshotManager.capture(
                eq(USER_ID), eq(PACKAGE_ID), any(), eq("SAVE")))
                .thenReturn(immutableSnapshot);

        AtomicInteger packageReads = new AtomicInteger();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper mapper = invocation.getArgument(1);
                    if (sql.contains("SELECT *")
                            && sql.contains("FROM job_application_package")) {
                        boolean initialRead = packageReads.getAndIncrement() == 0;
                        ResultSet resultSet = packageRowResultSet(
                                initialRead ? 2 : 7,
                                initialRead ? 902L : immutableSnapshot.getId());
                        return List.of(mapper.mapRow(resultSet, 0));
                    }
                    return List.of();
                });
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        List<JdbcUpdateCall> updateCalls = new ArrayList<>();
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object[] invocationArguments = invocation.getArguments();
                    updateCalls.add(new JdbcUpdateCall(
                            invocation.getArgument(0),
                            Arrays.copyOfRange(
                                    invocationArguments, 1, invocationArguments.length)));
                    return 1;
                });

        var refreshed = service.refresh(PACKAGE_ID);

        assertEquals(7, refreshed.getSnapshotVersion());
        assertEquals(immutableSnapshot.getId(), refreshed.getCurrentSnapshotId());
        verify(packageSnapshotManager).capture(
                eq(USER_ID), eq(PACKAGE_ID), any(), eq("SAVE"));

        JdbcUpdateCall rootUpdate = updateCalls.stream()
                .filter(call -> call.sql().contains("UPDATE job_application_package"))
                .findFirst()
                .orElseThrow();
        assertEquals(7, rootUpdate.args()[19]);
        assertEquals(immutableSnapshot.getId(), rootUpdate.args()[20]);
        assertEquals(7, objectMapper.readTree((String) rootUpdate.args()[11])
                .path("snapshotVersion").intValue());

        JdbcUpdateCall refreshEvent = updateCalls.stream()
                .filter(call -> call.sql().contains("INSERT INTO job_application_package_event"))
                .findFirst()
                .orElseThrow();
        assertEquals(7, refreshEvent.args()[13]);
        assertEquals(7, objectMapper.readTree((String) refreshEvent.args()[8])
                .path("snapshotVersion").intValue());
    }

    private TargetJob targetJob() {
        TargetJob target = new TargetJob();
        target.setId(TARGET_JOB_ID);
        target.setUserId(USER_ID);
        target.setJobTitle("Java Engineer");
        target.setCompanyName("CodeCoachAI");
        target.setDeleted(CommonConstants.NO);
        return target;
    }

    private JobDescriptionAnalysis analysis() {
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(21L);
        analysis.setUserId(USER_ID);
        analysis.setTargetJobId(TARGET_JOB_ID);
        analysis.setJobTitle("Java Engineer");
        analysis.setParseStatus(JobDescriptionParseStatus.PARSED.getCode());
        analysis.setDeleted(CommonConstants.NO);
        return analysis;
    }

    private ResumeVersion resumeVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setId(41L);
        version.setUserId(USER_ID);
        version.setResumeId(51L);
        version.setVersionNo(2);
        version.setVersionName("Backend resume");
        version.setCurrentFlag(CommonConstants.YES);
        version.setDeleted(CommonConstants.NO);
        return version;
    }

    private ResumeJobMatchReport matchReport(String rawResultJson) {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setId(MATCH_REPORT_ID);
        report.setUserId(USER_ID);
        report.setResumeId(51L);
        report.setResumeVersionId(41L);
        report.setTargetJobId(TARGET_JOB_ID);
        report.setJdAnalysisId(21L);
        report.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        report.setOverallScore(88);
        report.setTechStackScore(86);
        report.setProjectExperienceScore(84);
        report.setBusinessFitScore(90);
        report.setCommunicationScore(89);
        report.setAiCallLogId(91L);
        report.setSummary("Grounded match result");
        report.setGapsJson("[]");
        report.setRecommendedInterviewTopicsJson("[\"Java scenarios\"]");
        report.setRawResultJson(rawResultJson);
        report.setDeleted(CommonConstants.NO);
        return report;
    }

    private ProjectEvidence completeProject() {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(PROJECT_ID);
        project.setUserId(USER_ID);
        project.setTargetJobId(TARGET_JOB_ID);
        project.setTitle("Redis platform");
        project.setCompletenessScore(100);
        project.setCompletenessStatus("READY");
        project.setDeleted(CommonConstants.NO);
        return project;
    }

    private JobRequirementMatrixVO matrix(JobRequirementMatrixVO.RequirementItem... items) {
        JobRequirementMatrixVO matrix = new JobRequirementMatrixVO();
        matrix.setTargetJobId(TARGET_JOB_ID);
        matrix.setJdAnalysisId(21L);
        matrix.setRequirements(List.of(items));
        matrix.setRequirementCount(items.length);
        matrix.setStrongCount((int) List.of(items).stream()
                .filter(item -> "STRONG".equals(item.getCoverageLevel())).count());
        matrix.setWeakCount(0);
        matrix.setMissingCount(items.length - matrix.getStrongCount());
        return matrix;
    }

    private JobRequirementMatrixVO.RequirementItem requirement(Long id, String name, String priority,
                                                               String coverage,
                                                               JobRequirementMatrixVO.EvidenceItem evidence) {
        JobRequirementMatrixVO.RequirementItem item = new JobRequirementMatrixVO.RequirementItem();
        item.setRequirementId(id);
        item.setRequirementKey(name.toLowerCase());
        item.setRequirementName(name);
        item.setRequirementType("SKILL");
        item.setPriority(priority);
        item.setWeight(BigDecimal.ONE);
        item.setRequirementConfidence("HIGH");
        item.setRequirementFallback(false);
        item.setCoverageLevel(coverage);
        item.setEvidences(evidence == null ? List.of() : List.of(evidence));
        return item;
    }

    private JobRequirementMatrixVO.EvidenceItem strongEvidence(Long projectId) {
        JobRequirementMatrixVO.EvidenceItem evidence = new JobRequirementMatrixVO.EvidenceItem();
        evidence.setId(projectId + 100L);
        evidence.setProjectEvidenceId(projectId);
        evidence.setCoverageLevel("STRONG");
        evidence.setConfidenceLevel("HIGH");
        evidence.setConfirmed(true);
        evidence.setFallback(false);
        return evidence;
    }

    private JobReadinessSnapshotVO snapshot(JobRequirementMatrixVO matrix, String level,
                                             boolean fallback, String confidence) {
        JobReadinessSnapshotVO snapshot = new JobReadinessSnapshotVO();
        snapshot.setId(901L);
        snapshot.setTargetJobId(TARGET_JOB_ID);
        snapshot.setJdAnalysisId(21L);
        snapshot.setSnapshotHash("snapshot-hash");
        snapshot.setPolicyVersion("requirement-evidence-v1");
        snapshot.setReadinessScore(88);
        snapshot.setReadinessLevel(level);
        snapshot.setConfidenceLevel(confidence);
        snapshot.setFallback(fallback);
        snapshot.setRequirementCount(matrix.getRequirementCount());
        snapshot.setStrongCount(matrix.getStrongCount());
        snapshot.setWeakCount(matrix.getWeakCount());
        snapshot.setMissingCount(matrix.getMissingCount());
        snapshot.setMustRequirementCount(2);
        snapshot.setMustMissingCount("READY".equals(level) ? 0 : 2);
        snapshot.setMatrix(objectMapper.valueToTree(matrix));
        snapshot.setGeneratedAt(LocalDateTime.of(2026, 7, 11, 10, 0));
        return snapshot;
    }

    private ResultSet packageRowResultSet(int snapshotVersion, Long currentSnapshotId)
            throws SQLException {
        return packageRowResultSet(snapshotVersion, currentSnapshotId, null, "{}");
    }

    private ResultSet packageRowResultSet(int snapshotVersion, Long currentSnapshotId,
                                          Long matchReportId, String snapshotJson)
            throws SQLException {
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        AtomicBoolean wasNull = new AtomicBoolean();
        Map<String, Long> longs = new HashMap<>();
        longs.put("id", PACKAGE_ID);
        longs.put("user_id", USER_ID);
        longs.put("target_job_id", TARGET_JOB_ID);
        longs.put("jd_analysis_id", 21L);
        longs.put("resume_version_id", 41L);
        longs.put("match_report_id", matchReportId);
        longs.put("application_id", 81L);
        longs.put("current_snapshot_id", currentSnapshotId);
        Map<String, Integer> integers = Map.of(
                "readiness_score", 88,
                "fallback", 0,
                "snapshot_version", snapshotVersion);

        when(resultSet.getLong(anyString())).thenAnswer(invocation -> {
            Long value = longs.get(invocation.getArgument(0, String.class));
            wasNull.set(value == null);
            return value == null ? 0L : value;
        });
        when(resultSet.getInt(anyString())).thenAnswer(invocation -> {
            Integer value = integers.get(invocation.getArgument(0, String.class));
            wasNull.set(value == null);
            return value == null ? 0 : value;
        });
        when(resultSet.wasNull()).thenAnswer(invocation -> wasNull.get());
        when(resultSet.getString("package_no")).thenReturn("PKG-71");
        when(resultSet.getString("company_name")).thenReturn("CodeCoachAI");
        when(resultSet.getString("job_title")).thenReturn("Java Engineer");
        when(resultSet.getString("readiness_level")).thenReturn("READY");
        when(resultSet.getString("readiness_reason")).thenReturn("证据已覆盖核心要求");
        when(resultSet.getString("package_status")).thenReturn("READY");
        when(resultSet.getString("snapshot_json")).thenReturn(snapshotJson);
        when(resultSet.getString("checklist_json")).thenReturn("[]");
        when(resultSet.getString("actions_json")).thenReturn("[]");
        when(resultSet.getString("project_evidence_ids_json")).thenReturn("[31]");
        when(resultSet.getString("trace_id")).thenReturn("trace-71");
        when(resultSet.getString("result_source")).thenReturn("REAL");
        when(resultSet.getString("fallback_reason")).thenReturn(null);
        Timestamp timestamp = Timestamp.valueOf("2026-07-23 09:00:00");
        when(resultSet.getTimestamp("refreshed_at")).thenReturn(timestamp);
        when(resultSet.getTimestamp("created_at")).thenReturn(timestamp);
        when(resultSet.getTimestamp("updated_at")).thenReturn(timestamp);
        return resultSet;
    }

    private record JdbcUpdateCall(String sql, Object[] args) {
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
