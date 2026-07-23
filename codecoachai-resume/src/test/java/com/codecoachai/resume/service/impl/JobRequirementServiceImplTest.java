package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.JobRequirement;
import com.codecoachai.resume.domain.entity.JobRequirementEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.enums.JobDescriptionParseStatus;
import com.codecoachai.resume.domain.vo.JobRequirementEvidenceSourceRow;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.JobRequirementEvidenceMapper;
import com.codecoachai.resume.mapper.JobRequirementEvidenceSourceMapper;
import com.codecoachai.resume.mapper.JobRequirementMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobRequirementServiceImplTest {

    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private JobRequirementMapper jobRequirementMapper;
    @Mock
    private JobRequirementEvidenceMapper jobRequirementEvidenceMapper;
    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private ProjectSkillEvidenceMapper projectSkillEvidenceMapper;
    @Mock
    private JobRequirementEvidenceSourceMapper evidenceSourceMapper;

    private JobRequirementServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(TargetJob.class);
        initTableInfo(JobDescriptionAnalysis.class);
        initTableInfo(JobRequirement.class);
        initTableInfo(JobRequirementEvidence.class);
        initTableInfo(ProjectEvidence.class);
        initTableInfo(ProjectSkillEvidence.class);
        service = new JobRequirementServiceImpl(
                targetJobMapper,
                jobDescriptionAnalysisMapper,
                jobRequirementMapper,
                jobRequirementEvidenceMapper,
                projectEvidenceMapper,
                projectSkillEvidenceMapper,
                evidenceSourceMapper,
                new ObjectMapper());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        LoginUserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void materializeReusesRequirementFingerprintAndMarksFallbackLowConfidence() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobDescriptionAnalysis analysis = analysis();
        analysis.setRequiredSkillsJson("""
                [{"name":"Java","category":"backend","requiredLevel":"senior","weight":2}]
                """);
        analysis.setRawResultJson("""
                {"fallback":true,"trustStatus":"FALLBACK"}
                """);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis);

        AtomicReference<JobRequirement> stored = new AtomicReference<>();
        AtomicInteger queryCount = new AtomicInteger();
        when(jobRequirementMapper.selectList(any())).thenAnswer(invocation ->
                queryCount.getAndIncrement() == 0 || stored.get() == null
                        ? List.of() : List.of(stored.get()));
        when(jobRequirementMapper.insert(any(JobRequirement.class))).thenAnswer(invocation -> {
            JobRequirement requirement = invocation.getArgument(0);
            requirement.setId(501L);
            stored.set(requirement);
            return 1;
        });

        var first = service.materialize(11L);
        var second = service.materialize(11L);

        assertEquals(1, first.getInsertedCount());
        assertEquals(0, second.getInsertedCount());
        assertEquals(1, second.getUpdatedCount());
        assertEquals(first.getRequirements().get(0).getRequirementKey(),
                second.getRequirements().get(0).getRequirementKey());
        assertTrue(first.getRequirements().get(0).getSourceFallback());
        assertEquals("LOW", first.getRequirements().get(0).getConfidenceLevel());
        verify(jobRequirementMapper).insert(any(JobRequirement.class));
        verify(jobRequirementMapper).updateById(any(JobRequirement.class));
    }

    @Test
    void fallbackEvidenceCanOnlyProduceWeakCoverage() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobDescriptionAnalysis analysis = analysis();
        analysis.setRequiredSkillsJson("""
                [{"name":"Java","confidence":"HIGH"}]
                """);
        analysis.setRawResultJson("""
                {"trustStatus":"VERIFIED"}
                """);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis);

        AtomicReference<JobRequirement> requirementRef = new AtomicReference<>();
        AtomicInteger requirementQueryCount = new AtomicInteger();
        when(jobRequirementMapper.selectList(any())).thenAnswer(invocation -> {
            int count = requirementQueryCount.getAndIncrement();
            return count == 0 || requirementRef.get() == null ? List.of() : List.of(requirementRef.get());
        });
        when(jobRequirementMapper.insert(any(JobRequirement.class))).thenAnswer(invocation -> {
            JobRequirement requirement = invocation.getArgument(0);
            requirement.setId(501L);
            requirementRef.set(requirement);
            return 1;
        });

        ProjectEvidence project = new ProjectEvidence();
        project.setId(601L);
        project.setUserId(1001L);
        project.setTitle("Order Platform");
        project.setTargetJobId(11L);
        project.setDeleted(CommonConstants.NO);
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(project));

        ProjectSkillEvidence skill = new ProjectSkillEvidence();
        skill.setId(701L);
        skill.setUserId(1001L);
        skill.setProjectEvidenceId(601L);
        skill.setSkillName("Java");
        skill.setEvidenceText("Implemented the order state machine.");
        skill.setStrengthLevel("STRONG");
        skill.setSourceType("FALLBACK_IMPORT");
        skill.setConfirmed(CommonConstants.YES);
        skill.setDeleted(CommonConstants.NO);
        when(projectSkillEvidenceMapper.selectList(any())).thenReturn(List.of(skill));

        AtomicReference<JobRequirementEvidence> evidenceRef = new AtomicReference<>();
        AtomicInteger evidenceQueryCount = new AtomicInteger();
        when(jobRequirementEvidenceMapper.selectList(any())).thenAnswer(invocation ->
                evidenceQueryCount.getAndIncrement() == 0 || evidenceRef.get() == null
                        ? List.of() : List.of(evidenceRef.get()));
        when(jobRequirementEvidenceMapper.insert(any(JobRequirementEvidence.class))).thenAnswer(invocation -> {
            JobRequirementEvidence evidence = invocation.getArgument(0);
            evidence.setId(801L);
            evidenceRef.set(evidence);
            return 1;
        });

        var matrix = service.refreshMatrix(11L);

        assertEquals(0, matrix.getStrongCount());
        assertEquals(1, matrix.getWeakCount());
        assertEquals("WEAK", matrix.getRequirements().get(0).getCoverageLevel());
        assertTrue(matrix.getRequirements().get(0).getEvidences().get(0).getFallback());
        assertEquals("LOW", matrix.getRequirements().get(0).getEvidences().get(0).getConfidenceLevel());
    }

    @Test
    void rejectsTargetJobOwnedByAnotherUserBeforeReadingAnalysis() {
        when(targetJobMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.materialize(11L));

        verify(jobDescriptionAnalysisMapper, never()).selectOne(any());
    }

    @Test
    void refreshMatrixUsesOwnedTrustedBusinessEvidenceAndReturnsUnifiedActions() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobDescriptionAnalysis analysis = analysis();
        analysis.setRequiredSkillsJson("""
                [{"name":"Java","confidence":"HIGH"}]
                """);
        analysis.setRawResultJson("""
                {"trustStatus":"VERIFIED"}
                """);
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis);

        AtomicReference<JobRequirement> requirementRef = new AtomicReference<>();
        AtomicInteger requirementQueryCount = new AtomicInteger();
        when(jobRequirementMapper.selectList(any())).thenAnswer(invocation ->
                requirementQueryCount.getAndIncrement() == 0 || requirementRef.get() == null
                        ? List.of() : List.of(requirementRef.get()));
        when(jobRequirementMapper.insert(any(JobRequirement.class))).thenAnswer(invocation -> {
            JobRequirement requirement = invocation.getArgument(0);
            requirement.setId(501L);
            requirementRef.set(requirement);
            return 1;
        });
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of());

        when(evidenceSourceMapper.selectResumeMatchEvidence(1001L, 11L))
                .thenReturn(List.of(source("RESUME_MATCH", 301L, 302L, "Java match",
                        "Java evidence from resume", "SUCCESS", 88, true, false)));
        when(evidenceSourceMapper.selectInterviewReportEvidence(1001L, 11L))
                .thenReturn(List.of(source("INTERVIEW_REPORT", 401L, 402L, "Java interview",
                        "Java answer was strong", "GENERATED", 82, true, false)));
        when(evidenceSourceMapper.selectApplicationResultEvidence(1001L, 11L))
                .thenReturn(List.of(source("APPLICATION_RESULT", 601L, null, "Application offer",
                        "Application status: OFFER", "OFFER", null, true, false)));

        AtomicReference<List<JobRequirementEvidence>> evidenceRows =
                new AtomicReference<>(new java.util.ArrayList<>());
        AtomicInteger evidenceQueryCount = new AtomicInteger();
        when(jobRequirementEvidenceMapper.selectList(any())).thenAnswer(invocation ->
                evidenceQueryCount.getAndIncrement() == 0 ? List.of() : evidenceRows.get());
        when(jobRequirementEvidenceMapper.insert(any(JobRequirementEvidence.class))).thenAnswer(invocation -> {
            JobRequirementEvidence evidence = invocation.getArgument(0);
            evidence.setId(800L + evidenceRows.get().size());
            evidenceRows.get().add(evidence);
            return 1;
        });

        var matrix = service.refreshMatrix(11L);
        var item = matrix.getRequirements().get(0);

        assertEquals(3, item.getEvidences().size());
        assertTrue(matrix.getWarnings().contains("QUESTION_PRACTICE_UNAVAILABLE"));
        assertFalse(item.getNextActions().isEmpty());
        assertTrue(item.getNextActions().stream()
                .allMatch(action -> action.getPath().startsWith("/")));
        var resumeEvidence = item.getEvidences().stream()
                .filter(evidence -> "RESUME_MATCH".equals(evidence.getEvidenceType()))
                .findFirst().orElseThrow();
        assertEquals(301L, resumeEvidence.getEvidenceId());
        assertEquals(302L, resumeEvidence.getEvidenceSubId());
        assertEquals("Java match", resumeEvidence.getTitle());
        assertEquals("Java evidence from resume", resumeEvidence.getExcerpt());
        assertEquals("SUCCESS", resumeEvidence.getResultSource());
        assertTrue(resumeEvidence.getConfirmed());
        assertFalse(resumeEvidence.getFallback());
        assertNotNull(resumeEvidence.getOccurredAt());
        verify(evidenceSourceMapper).selectResumeMatchEvidence(1001L, 11L);
        verify(evidenceSourceMapper).selectInterviewReportEvidence(1001L, 11L);
        verify(evidenceSourceMapper).selectApplicationResultEvidence(1001L, 11L);
    }

    @Test
    void lowTrustExternalEvidenceCannotProduceStrongCoverage() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        JobDescriptionAnalysis analysis = analysis();
        analysis.setRequiredSkillsJson("[{\"name\":\"Java\",\"confidence\":\"HIGH\"}]");
        analysis.setRawResultJson("{\"trustStatus\":\"VERIFIED\"}");
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(analysis);
        AtomicReference<JobRequirement> requirementRef = new AtomicReference<>();
        AtomicInteger requirementQueryCount = new AtomicInteger();
        when(jobRequirementMapper.selectList(any())).thenAnswer(invocation ->
                requirementQueryCount.getAndIncrement() == 0 || requirementRef.get() == null
                        ? List.of() : List.of(requirementRef.get()));
        when(jobRequirementMapper.insert(any(JobRequirement.class))).thenAnswer(invocation -> {
            JobRequirement requirement = invocation.getArgument(0);
            requirement.setId(501L);
            requirementRef.set(requirement);
            return 1;
        });
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of());
        when(evidenceSourceMapper.selectResumeMatchEvidence(1001L, 11L)).thenReturn(List.of(
                source("RESUME_MATCH", 301L, 302L, "Java match", "Java evidence",
                        "FAILED", 95, false, true)));
        when(evidenceSourceMapper.selectInterviewReportEvidence(1001L, 11L)).thenReturn(List.of());
        when(evidenceSourceMapper.selectApplicationResultEvidence(1001L, 11L)).thenReturn(List.of());
        AtomicReference<JobRequirementEvidence> stored = new AtomicReference<>();
        AtomicInteger evidenceQueryCount = new AtomicInteger();
        when(jobRequirementEvidenceMapper.selectList(any())).thenAnswer(invocation ->
                evidenceQueryCount.getAndIncrement() == 0 || stored.get() == null
                        ? List.of() : List.of(stored.get()));
        when(jobRequirementEvidenceMapper.insert(any(JobRequirementEvidence.class))).thenAnswer(invocation -> {
            JobRequirementEvidence evidence = invocation.getArgument(0);
            evidence.setId(801L);
            stored.set(evidence);
            return 1;
        });

        var matrix = service.refreshMatrix(11L);

        assertEquals("WEAK", matrix.getRequirements().get(0).getCoverageLevel());
        assertEquals("LOW", matrix.getRequirements().get(0).getEvidences().get(0).getConfidenceLevel());
        assertTrue(matrix.getRequirements().get(0).getEvidences().get(0).getFallback());
    }

    private JobRequirementEvidenceSourceRow source(
            String evidenceType, Long evidenceId, Long evidenceSubId, String title,
            String excerpt, String resultSource, Integer score, boolean confirmed, boolean fallback) {
        JobRequirementEvidenceSourceRow row = new JobRequirementEvidenceSourceRow();
        row.setEvidenceType(evidenceType);
        row.setEvidenceId(evidenceId);
        row.setEvidenceSubId(evidenceSubId);
        row.setTitle(title);
        row.setExcerpt(excerpt);
        row.setResultSource(resultSource);
        row.setScore(score);
        row.setConfirmed(confirmed);
        row.setFallback(fallback);
        row.setOccurredAt(java.time.LocalDateTime.of(2026, 7, 11, 10, 0));
        return row;
    }

    private TargetJob targetJob() {
        TargetJob targetJob = new TargetJob();
        targetJob.setId(11L);
        targetJob.setUserId(1001L);
        targetJob.setDeleted(CommonConstants.NO);
        return targetJob;
    }

    private JobDescriptionAnalysis analysis() {
        JobDescriptionAnalysis analysis = new JobDescriptionAnalysis();
        analysis.setId(101L);
        analysis.setTargetJobId(11L);
        analysis.setUserId(1001L);
        analysis.setParseStatus(JobDescriptionParseStatus.PARSED.getCode());
        analysis.setResponsibilitiesJson("[]");
        analysis.setBonusSkillsJson("[]");
        analysis.setDeleted(CommonConstants.NO);
        return analysis;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
