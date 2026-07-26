package com.codecoachai.resume.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.codecoachai.common.security.context.LoginUser;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.ProjectEvidenceQueryDTO;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.vo.ProjectEvidenceListVO;
import com.codecoachai.resume.mapper.ProjectEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper.ConfirmedCount;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.TargetJobMapper;
import com.codecoachai.resume.service.support.ProjectEvidenceVersionManager;
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
                projectEvidenceVersionManager);
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
