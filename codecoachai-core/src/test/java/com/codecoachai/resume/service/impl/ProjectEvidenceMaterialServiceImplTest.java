package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.resume.domain.dto.ProjectStoryGenerationQueryDTO;
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
        lenient().when(projectEvidenceMapper.selectOne(any())).thenReturn(project());
        lenient().when(skillEvidenceMapper.selectList(any())).thenReturn(List.of(skill()));
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
    void listAcceptedStoriesReturnsOwnedStarEntriesWithProjectTitle() {
        ProjectStoryGeneration generation = new ProjectStoryGeneration();
        generation.setId(71L);
        generation.setUserId(1001L);
        generation.setProjectEvidenceId(31L);
        generation.setGenerationType("STAR_STORY");
        generation.setResultText("Situation: Redis cache miss.\nTask: cut p99.");
        generation.setAccepted(CommonConstants.YES);
        when(storyGenerationMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ProjectStoryGeneration> page = invocation.getArgument(0);
            LambdaQueryWrapper<ProjectStoryGeneration> wrapper = invocation.getArgument(1);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("exists (select 1 from project_evidence p"));
            assertTrue(sql.contains("p.id = project_story_generation.project_evidence_id"));
            assertTrue(sql.contains("p.user_id = #{"));
            assertTrue(sql.contains("p.deleted = #{"));
            assertTrue(sql.contains("generation_type = #{"));
            assertTrue(sql.contains("accepted = #{"));
            assertTrue(sql.contains("ORDER BY updated_at DESC,id DESC"));
            assertTrue(!sql.toLowerCase().contains("limit"));
            assertEquals(3L, wrapper.getParamNameValuePairs().values().stream()
                    .filter(CommonConstants.NO::equals).count() + wrapper.getParamNameValuePairs().values().stream()
                    .filter(CommonConstants.YES::equals).count());
            assertEquals(2L, wrapper.getParamNameValuePairs().values().stream().filter(Long.valueOf(1001L)::equals).count());
            assertTrue(wrapper.getParamNameValuePairs().containsValue("STAR_STORY"));
            assertEquals(11L, page.getCurrent());
            assertEquals(10L, page.getSize());
            page.setRecords(List.of(generation));
            page.setTotal(101L);
            return page;
        });
        when(projectEvidenceMapper.selectList(any())).thenReturn(List.of(project()));
        ProjectStoryGenerationQueryDTO query = new ProjectStoryGenerationQueryDTO();
        query.setPageNo(11L);

        var result = service.listAcceptedStories(query);

        assertEquals(101L, result.getTotal());
        assertEquals(11L, result.getPageNo());
        assertEquals(10L, result.getPageSize());
        assertEquals(11L, result.getPages());
        assertEquals(1, result.getRecords().size());
        assertEquals(31L, result.getRecords().get(0).getProjectEvidenceId());
        assertEquals("Redis project", result.getRecords().get(0).getProjectTitle());
        assertEquals("STAR_STORY", result.getRecords().get(0).getGenerationType());
        assertTrue(result.getRecords().get(0).getAccepted());
        assertTrue(result.getRecords().get(0).getResultText().contains("Redis cache miss"));
    }

    @Test
    void acceptedStoriesEmptyPagePreservesTotalAndSanitizesPagination() {
        when(storyGenerationMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ProjectStoryGeneration> page = invocation.getArgument(0);
            page.setTotal(125L);
            return page;
        });
        ProjectStoryGenerationQueryDTO query = new ProjectStoryGenerationQueryDTO();
        query.setPageNo(99L);
        query.setPageSize(1000L);
        var result = service.listAcceptedStories(query);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(125L, result.getTotal());
        assertEquals(99L, result.getPageNo());
        assertEquals(100L, result.getPageSize());
        assertEquals(2L, result.getPages());

        query.setPageNo(0L);
        query.setPageSize(-1L);
        var sanitized = service.listAcceptedStories(query);
        assertEquals(1L, sanitized.getPageNo());
        assertEquals(10L, sanitized.getPageSize());
        var defaults = service.listAcceptedStories(null);
        assertEquals(1L, defaults.getPageNo());
        assertEquals(10L, defaults.getPageSize());
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
