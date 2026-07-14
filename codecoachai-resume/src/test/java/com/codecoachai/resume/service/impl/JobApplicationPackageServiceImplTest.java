package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JobApplicationPackageServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final Long TARGET_JOB_ID = 11L;
    private static final Long PROJECT_ID = 31L;

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

    private final ObjectMapper objectMapper = new ObjectMapper();
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
                jdbcTemplate);
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

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
