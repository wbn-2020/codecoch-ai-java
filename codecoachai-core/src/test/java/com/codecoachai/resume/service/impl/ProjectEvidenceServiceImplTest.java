package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ProjectEvidenceQueryDTO;
import com.codecoachai.resume.domain.dto.ProjectEvidenceSaveDTO;
import com.codecoachai.resume.domain.dto.ProjectSkillEvidenceSaveDTO;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.vo.ProjectEvidenceListVO;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper.ConfirmedCount;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.EvidenceProfileFeedbackOutboxService;
import com.codecoachai.resume.service.support.ProjectEvidenceVersionManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectEvidenceServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ProjectEvidenceMapper projectEvidenceMapper;
    @Mock
    private ProjectSkillEvidenceMapper skillEvidenceMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper resumeProjectMapper;
    @Mock
    private TargetJobMapper targetJobMapper;
    @Mock
    private AgentBusinessActionNotifier agentBusinessActionNotifier;
    @Mock
    private ProjectEvidenceVersionManager projectEvidenceVersionManager;
    @Mock
    private EvidenceProfileFeedbackOutboxService profileFeedbackOutboxService;

    private ProjectEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUserContext.setLoginUser(LoginUser.builder().userId(USER_ID).username("owner").build());
        service = new ProjectEvidenceServiceImpl(
                projectEvidenceMapper,
                skillEvidenceMapper,
                resumeMapper,
                resumeProjectMapper,
                targetJobMapper,
                agentBusinessActionNotifier,
                projectEvidenceVersionManager,
                profileFeedbackOutboxService);
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void listLoadsCountsAndSourceAvailabilityInFixedBatchQueries() {
        ProjectEvidence manual = project(1L, null, null);
        ProjectEvidence available = project(2L, 10L, 20L);
        ProjectEvidence unavailable = project(3L, 11L, 21L);
        Page<ProjectEvidence> page = new Page<>(1, 3);
        page.setRecords(List.of(manual, available, unavailable));
        page.setTotal(3L);
        when(projectEvidenceMapper.selectPage(any(Page.class), any())).thenReturn(page);

        when(skillEvidenceMapper.selectConfirmedCounts(eq(USER_ID), anyList()))
                .thenReturn(List.of(count(1L, 2L), count(2L, 1L)));
        Resume ownedResume = resume(10L, USER_ID);
        Resume otherUsersResume = resume(11L, 99L);
        when(resumeMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(ownedResume, otherUsersResume));
        when(resumeProjectMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(resumeProject(20L, 10L), resumeProject(21L, 11L)));

        ProjectEvidenceQueryDTO query = new ProjectEvidenceQueryDTO();
        query.setPageSize(3L);
        PageResult<ProjectEvidenceListVO> result = service.list(query);

        assertEquals(List.of(2L, 1L, 0L),
                result.getRecords().stream().map(ProjectEvidenceListVO::getSkillEvidenceCount).toList());
        assertTrue(result.getRecords().get(0).getSourceAvailable());
        assertTrue(result.getRecords().get(1).getSourceAvailable());
        assertFalse(result.getRecords().get(2).getSourceAvailable());

        ArgumentCaptor<List<Long>> projectIds = ArgumentCaptor.forClass(List.class);
        verify(skillEvidenceMapper).selectConfirmedCounts(eq(USER_ID), projectIds.capture());
        assertTrue(projectIds.getValue().containsAll(List.of(1L, 2L, 3L)));
        verify(skillEvidenceMapper, never()).selectCount(any());
        verify(resumeMapper).selectBatchIds(anyCollection());
        verify(resumeProjectMapper).selectBatchIds(anyCollection());
        verify(resumeMapper, never()).selectOne(any());
        verify(resumeProjectMapper, never()).selectById(any());
    }

    @Test
    void deletingProjectRequeuesItsAbilityProjection() {
        ProjectEvidence project = project(5L, null, null);
        when(projectEvidenceMapper.selectById(5L)).thenReturn(project);

        service.delete(project.getId());

        verify(projectEvidenceMapper).updateById(project);
        verify(profileFeedbackOutboxService)
                .requeueAbilityProjectionForProject(USER_ID, project.getId());
    }

    @Test
    void addingSkillEvidenceRequeuesProjectAbilityProjection() {
        ProjectEvidence project = project(5L, null, null);
        when(projectEvidenceMapper.selectById(5L)).thenReturn(project);
        ProjectSkillEvidenceSaveDTO dto = skillSave("Redis", true);
        when(skillEvidenceMapper.insert(any(ProjectSkillEvidence.class))).thenAnswer(invocation -> {
            ProjectSkillEvidence evidence = invocation.getArgument(0);
            evidence.setId(8L);
            return 1;
        });
        when(skillEvidenceMapper.selectById(8L)).thenAnswer(invocation -> skillEvidence(8L, 5L));

        service.addSkillEvidence(project.getId(), dto);

        verify(skillEvidenceMapper).insert(any(ProjectSkillEvidence.class));
        verify(profileFeedbackOutboxService)
                .requeueAbilityProjectionForProject(USER_ID, project.getId());
    }

    @Test
    void updatingSkillEvidenceRequeuesProjectAbilityProjection() {
        ProjectEvidence project = project(5L, null, null);
        ProjectSkillEvidence evidence = skillEvidence(8L, project.getId());
        when(projectEvidenceMapper.selectById(5L)).thenReturn(project);
        when(skillEvidenceMapper.selectById(8L)).thenReturn(evidence);

        service.updateSkillEvidence(project.getId(), evidence.getId(),
                skillSave("Redis Cluster", false));

        verify(skillEvidenceMapper).updateById(evidence);
        verify(profileFeedbackOutboxService)
                .requeueAbilityProjectionForProject(USER_ID, project.getId());
    }

    @Test
    void deletingSkillEvidenceRequeuesProjectAbilityProjection() {
        ProjectEvidence project = project(5L, null, null);
        ProjectSkillEvidence evidence = skillEvidence(8L, project.getId());
        when(projectEvidenceMapper.selectById(5L)).thenReturn(project);
        when(skillEvidenceMapper.selectById(8L)).thenReturn(evidence);

        service.deleteSkillEvidence(project.getId(), evidence.getId());

        verify(skillEvidenceMapper).updateById(evidence);
        verify(profileFeedbackOutboxService)
                .requeueAbilityProjectionForProject(USER_ID, project.getId());
    }

    @Test
    void detailDistinguishesMissingProjectFromForeignOwnedProject() {
        when(projectEvidenceMapper.selectById(404L)).thenReturn(null);

        BusinessException missing = assertThrows(
                BusinessException.class, () -> service.detail(404L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), missing.getCode());

        ProjectEvidence foreign = project(405L, null, null);
        foreign.setUserId(99L);
        when(projectEvidenceMapper.selectById(405L)).thenReturn(foreign);

        BusinessException forbidden = assertThrows(
                BusinessException.class, () -> service.detail(405L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), forbidden.getCode());
    }

    @Test
    void updateReadsBackPersistedTimesBeforeCapturingVersionAndReturningDetail() {
        ProjectEvidence before = project(5L, null, null);
        ProjectEvidence persisted = project(5L, null, null);
        persisted.setTitle("Updated project");
        persisted.setCreatedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        persisted.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 16, 0));
        when(projectEvidenceMapper.selectById(5L)).thenReturn(before, persisted);
        ProjectEvidenceSaveDTO dto = new ProjectEvidenceSaveDTO();
        dto.setTitle("Updated project");

        var result = service.update(5L, dto);

        assertEquals(persisted.getCreatedAt(), result.getCreatedAt());
        assertEquals(persisted.getUpdatedAt(), result.getUpdatedAt());
        verify(projectEvidenceVersionManager).capture(persisted, "MANUAL", 5L);
    }

    private ProjectEvidence project(Long id, Long resumeId, Long resumeProjectId) {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(id);
        project.setUserId(USER_ID);
        project.setTitle("Project " + id);
        project.setSourceResumeId(resumeId);
        project.setSourceResumeProjectId(resumeProjectId);
        return project;
    }

    private ConfirmedCount count(Long projectId, Long value) {
        ConfirmedCount count = new ConfirmedCount();
        count.setProjectEvidenceId(projectId);
        count.setConfirmedCount(value);
        return count;
    }

    private ProjectSkillEvidenceSaveDTO skillSave(String skillName, boolean confirmed) {
        ProjectSkillEvidenceSaveDTO dto = new ProjectSkillEvidenceSaveDTO();
        dto.setSkillName(skillName);
        dto.setConfirmed(confirmed);
        return dto;
    }

    private ProjectSkillEvidence skillEvidence(Long id, Long projectId) {
        ProjectSkillEvidence evidence = new ProjectSkillEvidence();
        evidence.setId(id);
        evidence.setUserId(USER_ID);
        evidence.setProjectEvidenceId(projectId);
        evidence.setSkillName("Redis");
        evidence.setConfirmed(1);
        return evidence;
    }

    private Resume resume(Long id, Long userId) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(userId);
        resume.setDeleted(0);
        return resume;
    }

    private ResumeProject resumeProject(Long id, Long resumeId) {
        ResumeProject project = new ResumeProject();
        project.setId(id);
        project.setResumeId(resumeId);
        project.setDeleted(0);
        return project;
    }
}
