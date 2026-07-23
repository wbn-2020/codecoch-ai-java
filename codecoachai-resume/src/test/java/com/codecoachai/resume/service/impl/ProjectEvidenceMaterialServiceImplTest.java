package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ProjectJdCoverageRequestDTO;
import com.codecoachai.resume.domain.entity.JobDescriptionAnalysis;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.ProjectStoryGeneration;
import com.codecoachai.resume.domain.entity.TargetJob;
import com.codecoachai.resume.domain.vo.JobRequirementMatrixVO;
import com.codecoachai.resume.mapper.JobDescriptionAnalysisMapper;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectStoryGenerationMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.JobRequirementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectEvidenceMaterialServiceImplTest {

    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private ProjectSkillEvidenceMapper skillEvidenceMapper;
    @Mock
    private ProjectStoryGenerationMapper storyGenerationMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private JobDescriptionAnalysisMapper jobDescriptionAnalysisMapper;
    @Mock
    private JobRequirementService jobRequirementService;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;

    private ProjectEvidenceMaterialServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProjectEvidence.class);
        initTableInfo(ProjectSkillEvidence.class);
        initTableInfo(ProjectStoryGeneration.class);
        initTableInfo(TargetJob.class);
        initTableInfo(JobDescriptionAnalysis.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProjectEvidenceMaterialServiceImpl(
                projectEvidenceMapper,
                skillEvidenceMapper,
                storyGenerationMapper,
                targetJobMapper,
                jobDescriptionAnalysisMapper,
                new ObjectMapper(),
                jobRequirementService,
                agentBusinessActionNotifier);
        LoginUser user = new LoginUser();
        user.setUserId(1001L);
        LoginUserContext.setLoginUser(user);
        when(projectEvidenceMapper.selectOne(any())).thenReturn(project());
        when(skillEvidenceMapper.selectList(any())).thenReturn(List.of(skill()));
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void strongCoverageRequiresEvidenceFromCurrentProject() {
        when(targetJobMapper.selectOne(any())).thenReturn(targetJob());
        when(jobDescriptionAnalysisMapper.selectOne(any())).thenReturn(null);
        JobRequirementMatrixVO matrix = new JobRequirementMatrixVO();
        matrix.setTargetJobId(11L);
        matrix.setRequirementCount(1);
        matrix.setRequirements(List.of(requirementWithEvidence(99L)));
        when(jobRequirementService.getMatrix(11L)).thenReturn(matrix);
        ProjectJdCoverageRequestDTO request = new ProjectJdCoverageRequestDTO();
        request.setTargetJobId(11L);

        var result = service.analyzeJdCoverage(31L, request);

        assertTrue(result.getCoveredSkills().isEmpty());
        assertEquals(List.of("Redis"), result.getMissingSkills());
        assertEquals("JOB_REQUIREMENT_MATRIX", result.getSourceType());
    }

    @Test
    void rawTextFallbackCanOnlyProduceWeakCoverage() {
        ProjectJdCoverageRequestDTO request = new ProjectJdCoverageRequestDTO();
        request.setJdText("Redis");

        var result = service.analyzeJdCoverage(31L, request);

        assertTrue(result.getCoveredSkills().isEmpty());
        assertEquals(List.of("Redis"), result.getWeakCoveredSkills());
        assertTrue(result.getFallback());
        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getWarnings().contains("STRONG_COVERAGE_DISABLED_WITHOUT_REQUIREMENT_MATRIX"));
    }

    private ProjectEvidence project() {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(31L);
        project.setUserId(1001L);
        project.setTargetJobId(11L);
        project.setTitle("Redis project");
        project.setTechStack("Java Redis");
        project.setCompletenessScore(100);
        project.setCompletenessStatus("READY");
        project.setDeleted(CommonConstants.NO);
        return project;
    }

    private ProjectSkillEvidence skill() {
        ProjectSkillEvidence skill = new ProjectSkillEvidence();
        skill.setId(41L);
        skill.setUserId(1001L);
        skill.setProjectEvidenceId(31L);
        skill.setSkillName("Redis");
        skill.setStrengthLevel("STRONG");
        skill.setConfirmed(CommonConstants.YES);
        skill.setDeleted(CommonConstants.NO);
        return skill;
    }

    private TargetJob targetJob() {
        TargetJob target = new TargetJob();
        target.setId(11L);
        target.setUserId(1001L);
        target.setDeleted(CommonConstants.NO);
        return target;
    }

    private JobRequirementMatrixVO.RequirementItem requirementWithEvidence(Long projectId) {
        JobRequirementMatrixVO.EvidenceItem evidence = new JobRequirementMatrixVO.EvidenceItem();
        evidence.setProjectEvidenceId(projectId);
        evidence.setCoverageLevel("STRONG");
        evidence.setConfidenceLevel("HIGH");
        evidence.setConfirmed(true);
        evidence.setFallback(false);
        JobRequirementMatrixVO.RequirementItem requirement = new JobRequirementMatrixVO.RequirementItem();
        requirement.setRequirementId(101L);
        requirement.setRequirementKey("redis");
        requirement.setRequirementName("Redis");
        requirement.setRequirementType("SKILL");
        requirement.setPriority("MUST");
        requirement.setCoverageLevel("STRONG");
        requirement.setRequirementConfidence("HIGH");
        requirement.setRequirementFallback(false);
        requirement.setEvidences(List.of(evidence));
        return requirement;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
