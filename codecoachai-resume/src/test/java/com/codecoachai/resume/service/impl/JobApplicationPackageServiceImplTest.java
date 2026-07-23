package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
                packageSnapshotManager);
        LoginUser user = new LoginUser();
        user.setUserId(USER_ID);
        LoginUserContext.setLoginUser(user);
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis());
        when(resumeVersionMapper.selectOne(any())).thenReturn(resumeVersion());
        when(resumeJobMatchReportMapper.selectOne(any())).thenReturn(null);
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(completeProject()));
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
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        AtomicBoolean wasNull = new AtomicBoolean();
        Map<String, Long> longs = new HashMap<>();
        longs.put("id", PACKAGE_ID);
        longs.put("user_id", USER_ID);
        longs.put("target_job_id", TARGET_JOB_ID);
        longs.put("jd_analysis_id", 21L);
        longs.put("resume_version_id", 41L);
        longs.put("match_report_id", null);
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
        when(resultSet.getString("snapshot_json")).thenReturn("{}");
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
