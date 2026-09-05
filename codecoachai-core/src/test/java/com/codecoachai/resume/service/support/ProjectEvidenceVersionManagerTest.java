package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.mapper.ProjectEvidenceVersionMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectEvidenceVersionManagerTest {

    private static final long USER_ID = 10L;
    private static final long PROJECT_ID = 31L;

    @Mock
    private ProjectEvidenceVersionMapper versionMapper;
    @Mock
    private ProjectSkillEvidenceMapper skillEvidenceMapper;

    private ProjectEvidenceVersionManager manager;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProjectEvidence.class);
        initTableInfo(ProjectSkillEvidence.class);
        initTableInfo(ProjectEvidenceVersion.class);
    }

    @BeforeEach
    void setUp() {
        manager = new ProjectEvidenceVersionManager(
                versionMapper, skillEvidenceMapper, new ObjectMapper());
    }

    @Test
    void unchangedContentReturnsExistingVersionWithoutCreatingAnotherRow() {
        ProjectEvidenceVersion existing = version(8L, 3, "existing-hash");
        when(skillEvidenceMapper.selectList(any())).thenReturn(java.util.List.of());
        when(versionMapper.selectByContentHash(eq(PROJECT_ID), eq(USER_ID), anyString()))
                .thenReturn(existing);

        ProjectEvidenceVersion result = manager.capture(project("stable"), "manual", PROJECT_ID);

        assertSame(existing, result);
        verify(versionMapper).selectByContentHash(eq(PROJECT_ID), eq(USER_ID), anyString());
        verify(versionMapper, never()).selectLatestForUpdate(any(), any());
        verify(versionMapper, never()).insert(any(ProjectEvidenceVersion.class));
    }

    @Test
    void changedContentIncrementsVersionAndKeepsPreviousVersionUntouched() {
        ProjectEvidenceVersion previous = version(8L, 3, "previous-hash");
        when(skillEvidenceMapper.selectList(any())).thenReturn(java.util.List.of(skill()));
        when(versionMapper.selectByContentHash(eq(PROJECT_ID), eq(USER_ID), anyString()))
                .thenReturn(null);
        when(versionMapper.selectLatestForUpdate(PROJECT_ID, USER_ID)).thenReturn(previous);
        when(versionMapper.insert(any(ProjectEvidenceVersion.class))).thenAnswer(invocation -> {
            ProjectEvidenceVersion inserted = invocation.getArgument(0);
            inserted.setId(9L);
            return 1;
        });

        ProjectEvidenceVersion result =
                manager.capture(project("changed"), "resume_project", 77L);

        assertEquals(9L, result.getId());
        assertEquals(4, result.getVersionNo());
        assertEquals("RESUME_PROJECT", result.getSourceType());
        assertEquals(77L, result.getSourceId());
        assertEquals(3, previous.getVersionNo());
        assertEquals("previous-hash", previous.getContentHash());

        ArgumentCaptor<ProjectEvidenceVersion> captor =
                ArgumentCaptor.forClass(ProjectEvidenceVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getVersionNo());
        verify(versionMapper, never()).updateById(any(ProjectEvidenceVersion.class));
    }

    private ProjectEvidence project(String result) {
        ProjectEvidence project = new ProjectEvidence();
        project.setId(PROJECT_ID);
        project.setUserId(USER_ID);
        project.setTitle("Redis project");
        project.setRole("Backend engineer");
        project.setTechStack("Java, Redis");
        project.setResult(result);
        return project;
    }

    private ProjectSkillEvidence skill() {
        ProjectSkillEvidence skill = new ProjectSkillEvidence();
        skill.setId(41L);
        skill.setUserId(USER_ID);
        skill.setProjectEvidenceId(PROJECT_ID);
        skill.setSkillName("Redis");
        skill.setEvidenceText("Implemented cache invalidation");
        return skill;
    }

    private ProjectEvidenceVersion version(Long id, int versionNo, String contentHash) {
        ProjectEvidenceVersion version = new ProjectEvidenceVersion();
        version.setId(id);
        version.setProjectEvidenceId(PROJECT_ID);
        version.setUserId(USER_ID);
        version.setVersionNo(versionNo);
        version.setContentHash(contentHash);
        return version;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
