package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeVersionSnapshotManagerTest {

    private static final long USER_ID = 10L;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeProjectMapper projectMapper;
    @Mock
    private ResumeVersionMapper versionMapper;

    private ResumeVersionSnapshotManager snapshotManager;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(ResumeVersion.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    ResumeVersion.class);
        }
    }

    @BeforeEach
    void setUp() {
        snapshotManager = new ResumeVersionSnapshotManager(
                resumeMapper, projectMapper, versionMapper, new ObjectMapper());
    }

    @Test
    void insertAndApplyIfCurrentRejectsStaleVersionWithoutOverwritingIndependentEdit() {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);
        resume.setSummary("Independent user edit");
        ResumeVersion current = new ResumeVersion();
        current.setId(4L);
        current.setUserId(USER_ID);
        current.setResumeId(1L);
        current.setCurrentFlag(1);
        ObjectNode historicalSnapshot = new ObjectMapper().createObjectNode()
                .put("summary", "Historical suggestion snapshot");

        when(resumeMapper.lockOwnedResume(1L, USER_ID)).thenReturn(1L);
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(versionMapper.selectCurrentForUpdate(USER_ID, 1L)).thenReturn(current);
        when(projectMapper.selectActiveByResumeIdForUpdate(1L)).thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> snapshotManager.insertAndApplyIfCurrent(
                        resume,
                        2L,
                        historicalSnapshot,
                        "SUGGESTION_ACCEPT",
                        9L,
                        "Suggestion #9"));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        assertEquals("Independent user edit", resume.getSummary());
        verify(resumeMapper).lockOwnedResume(1L, USER_ID);
        verify(resumeMapper).selectOne(any());
        verify(versionMapper).selectCurrentForUpdate(USER_ID, 1L);
        verify(projectMapper).selectActiveByResumeIdForUpdate(1L);
        verify(resumeMapper, never()).updateById(any(Resume.class));
        verify(versionMapper, never()).update(any(), any());
        verify(versionMapper, never()).insert(any(ResumeVersion.class));
        verify(projectMapper, never()).delete(any());
        verify(projectMapper, never()).insert(any(ResumeProject.class));
    }

    @Test
    void insertAndApplyIfCurrentRejectsSameVersionIdWhenLockedLiveResumeDrifted() {
        ObjectMapper mapper = new ObjectMapper();
        Resume staleCallerResume = resume("Historical summary");
        Resume lockedLiveResume = resume("Independent user edit");
        ResumeVersion current = currentVersion(2L, snapshot(mapper, "Historical summary", List.of()).toString());
        ObjectNode patchedSnapshot = snapshot(mapper, "Suggested rewrite", List.of());
        List<String> calls = new ArrayList<>();

        ResumeMapper guardedResumeMapper = mock(ResumeMapper.class, invocation -> {
            calls.add("resume." + invocation.getMethod().getName());
            return switch (invocation.getMethod().getName()) {
                case "lockOwnedResume" -> 1L;
                case "selectOne" -> lockedLiveResume;
                default -> Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        ResumeVersionMapper guardedVersionMapper = versionMapper(calls, current);
        ResumeProjectMapper guardedProjectMapper = projectMapper(calls, List.of());
        ResumeVersionSnapshotManager guardedManager = new ResumeVersionSnapshotManager(
                guardedResumeMapper, guardedProjectMapper, guardedVersionMapper, mapper);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> guardedManager.insertAndApplyIfCurrent(
                        staleCallerResume,
                        2L,
                        patchedSnapshot,
                        "SUGGESTION_ACCEPT",
                        9L,
                        "Suggestion #9"));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        assertTrue(calls.contains("resume.lockOwnedResume"), calls.toString());
        assertTrue(calls.contains("resume.selectOne"), calls.toString());
        assertTrue(calls.contains("version.selectCurrentForUpdate"), calls.toString());
        assertTrue(calls.contains("project.selectActiveByResumeIdForUpdate"), calls.toString());
        assertNoGuardedWrites(calls);
    }

    @Test
    void insertAndApplyIfCurrentRejectsSameVersionIdWhenLockedLiveProjectsDrifted() {
        ObjectMapper mapper = new ObjectMapper();
        Resume staleCallerResume = resume("Stable summary");
        Resume lockedLiveResume = resume("Stable summary");
        ResumeProject versionProject = project(11L, "Version project", 1, 1);
        ResumeProject editedProject = project(11L, "Independently edited project", 1, 1);
        ResumeVersion current = currentVersion(
                2L,
                snapshot(mapper, "Stable summary", List.of(versionProject)).toString());
        ObjectNode patchedSnapshot = snapshot(mapper, "Suggested rewrite", List.of(versionProject));
        List<String> calls = new ArrayList<>();

        ResumeMapper guardedResumeMapper = mock(ResumeMapper.class, invocation -> {
            calls.add("resume." + invocation.getMethod().getName());
            return switch (invocation.getMethod().getName()) {
                case "lockOwnedResume" -> 1L;
                case "selectOne" -> lockedLiveResume;
                default -> Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        ResumeVersionMapper guardedVersionMapper = versionMapper(calls, current);
        ResumeProjectMapper guardedProjectMapper = projectMapper(calls, List.of(editedProject));
        ResumeVersionSnapshotManager guardedManager = new ResumeVersionSnapshotManager(
                guardedResumeMapper, guardedProjectMapper, guardedVersionMapper, mapper);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> guardedManager.insertAndApplyIfCurrent(
                        staleCallerResume,
                        2L,
                        patchedSnapshot,
                        "SUGGESTION_ACCEPT",
                        9L,
                        "Suggestion #9"));

        assertEquals(ErrorCode.STALE_SOURCE_VERSION.getCode(), exception.getCode());
        assertNoGuardedWrites(calls);
    }

    @Test
    void insertAndApplyIfCurrentUsesParentCurrentProjectLockOrderBeforeWrites() {
        ObjectMapper mapper = new ObjectMapper();
        Resume callerResume = resume("Stable summary");
        Resume lockedLiveResume = resume("Stable summary");
        ResumeProject liveProject = project(11L, "Stable project", 1, 1);
        ResumeVersion current = currentVersion(
                2L,
                snapshot(mapper, "Stable summary", List.of(liveProject)).toString());
        ObjectNode patchedSnapshot = snapshot(mapper, "Suggested rewrite", List.of(liveProject));
        List<String> calls = new ArrayList<>();

        ResumeMapper guardedResumeMapper = mock(ResumeMapper.class, invocation -> {
            calls.add("resume." + invocation.getMethod().getName());
            return switch (invocation.getMethod().getName()) {
                case "lockOwnedResume" -> 1L;
                case "selectOne" -> lockedLiveResume;
                case "updateById" -> 1;
                default -> Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        ResumeVersionMapper guardedVersionMapper = versionMapper(calls, current);
        ResumeProjectMapper guardedProjectMapper = projectMapper(calls, List.of(liveProject));
        ResumeVersionSnapshotManager guardedManager = new ResumeVersionSnapshotManager(
                guardedResumeMapper, guardedProjectMapper, guardedVersionMapper, mapper);

        guardedManager.insertAndApplyIfCurrent(
                callerResume,
                2L,
                patchedSnapshot,
                "SUGGESTION_ACCEPT",
                9L,
                "Suggestion #9");

        assertBefore(calls, "resume.lockOwnedResume", "resume.selectOne");
        assertBefore(calls, "resume.selectOne", "version.selectCurrentForUpdate");
        assertBefore(calls, "version.selectCurrentForUpdate", "project.selectActiveByResumeIdForUpdate");
        assertBefore(calls, "project.selectActiveByResumeIdForUpdate", "version.selectLatestForUpdate");
        assertBefore(calls, "version.selectLatestForUpdate", "version.update");
        assertBefore(calls, "project.selectActiveByResumeIdForUpdate", "version.insert");
        assertBefore(calls, "project.selectActiveByResumeIdForUpdate", "resume.updateById");
        assertBefore(calls, "project.selectActiveByResumeIdForUpdate", "project.delete");
    }

    @Test
    void insertAndApplyIfCurrentTreatsOppositeSameSortProjectOrderAsCanonicalEquivalent() {
        ObjectMapper mapper = new ObjectMapper();
        Resume callerResume = resume("Stable summary");
        Resume lockedLiveResume = resume("Stable summary");
        ResumeProject alpha = project(11L, "Alpha project", 1, 1);
        ResumeProject zulu = project(12L, "Zulu project", 1, 1);
        ResumeVersion current = currentVersion(
                2L,
                snapshot(mapper, "Stable summary", List.of(zulu, alpha)).toString());
        ObjectNode patchedSnapshot = snapshot(mapper, "Suggested rewrite", List.of(zulu, alpha));
        List<String> calls = new ArrayList<>();

        ResumeVersionSnapshotManager guardedManager = guardedManager(
                mapper, calls, callerResume, lockedLiveResume, current, List.of(alpha, zulu));

        guardedManager.insertAndApplyIfCurrent(
                callerResume,
                2L,
                patchedSnapshot,
                "SUGGESTION_ACCEPT",
                9L,
                "Suggestion #9");

        assertTrue(calls.contains("version.insert"), calls.toString());
        assertTrue(calls.contains("resume.updateById"), calls.toString());
    }

    @Test
    void insertAndApplyIfCurrentAcceptsLegacySnapshotWithoutProjectsOrSourceMarker() {
        ObjectMapper mapper = new ObjectMapper();
        Resume callerResume = resume("Stable summary");
        Resume lockedLiveResume = resume("Stable summary");
        ObjectNode legacySnapshot = mapper.createObjectNode().put("summary", "Stable summary");
        ResumeVersion current = currentVersion(2L, legacySnapshot.toString());
        ObjectNode patchedSnapshot = snapshot(mapper, "Suggested rewrite", List.of());
        List<String> calls = new ArrayList<>();

        ResumeVersionSnapshotManager guardedManager = guardedManager(
                mapper, calls, callerResume, lockedLiveResume, current, List.of());

        guardedManager.insertAndApplyIfCurrent(
                callerResume,
                2L,
                patchedSnapshot,
                "SUGGESTION_ACCEPT",
                9L,
                "Suggestion #9");

        assertTrue(calls.contains("version.insert"), calls.toString());
    }

    @Test
    void insertAndApplyIfCurrentTreatsMissingLegacyProjectSortFieldsAsZero() {
        ObjectMapper mapper = new ObjectMapper();
        Resume callerResume = resume("Stable summary");
        Resume lockedLiveResume = resume("Stable summary");
        ResumeProject liveProject = project(11L, "Legacy project", 0, 0);
        ObjectNode legacySnapshot = snapshot(mapper, "Stable summary", List.of(liveProject));
        ObjectNode legacyProject = (ObjectNode) legacySnapshot.path("projects").get(0);
        legacyProject.remove("sort");
        legacyProject.remove("sortOrder");
        legacySnapshot.remove("projectSnapshotSource");
        ResumeVersion current = currentVersion(2L, legacySnapshot.toString());
        ObjectNode patchedSnapshot = snapshot(mapper, "Suggested rewrite", List.of(liveProject));
        List<String> calls = new ArrayList<>();

        ResumeVersionSnapshotManager guardedManager = guardedManager(
                mapper, calls, callerResume, lockedLiveResume, current, List.of(liveProject));

        guardedManager.insertAndApplyIfCurrent(
                callerResume,
                2L,
                patchedSnapshot,
                "SUGGESTION_ACCEPT",
                9L,
                "Suggestion #9");

        assertTrue(calls.contains("version.insert"), calls.toString());
    }

    private ResumeVersionSnapshotManager guardedManager(
            ObjectMapper mapper,
            List<String> calls,
            Resume callerResume,
            Resume lockedLiveResume,
            ResumeVersion current,
            List<ResumeProject> liveProjects) {
        ResumeMapper guardedResumeMapper = mock(ResumeMapper.class, invocation -> {
            calls.add("resume." + invocation.getMethod().getName());
            return switch (invocation.getMethod().getName()) {
                case "lockOwnedResume" -> callerResume.getId();
                case "selectOne" -> lockedLiveResume;
                case "updateById" -> 1;
                default -> Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        return new ResumeVersionSnapshotManager(
                guardedResumeMapper,
                projectMapper(calls, liveProjects),
                versionMapper(calls, current),
                mapper);
    }

    private ResumeVersionMapper versionMapper(List<String> calls, ResumeVersion current) {
        return mock(ResumeVersionMapper.class, invocation -> {
            calls.add("version." + invocation.getMethod().getName());
            return switch (invocation.getMethod().getName()) {
                case "selectOne", "selectCurrentForUpdate", "selectLatestForUpdate" -> current;
                case "insert", "update" -> 1;
                default -> Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
    }

    private ResumeProjectMapper projectMapper(List<String> calls, List<ResumeProject> projects) {
        return mock(ResumeProjectMapper.class, invocation -> {
            calls.add("project." + invocation.getMethod().getName());
            return switch (invocation.getMethod().getName()) {
                case "selectActiveByResumeIdForUpdate" -> projects;
                case "delete", "insert" -> 1;
                default -> Answers.RETURNS_DEFAULTS.answer(invocation);
            };
        });
    }

    private Resume resume(String summary) {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(USER_ID);
        resume.setSummary(summary);
        return resume;
    }

    private ResumeVersion currentVersion(Long id, String snapshotJson) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setUserId(USER_ID);
        version.setResumeId(1L);
        version.setVersionNo(2);
        version.setCurrentFlag(1);
        version.setSnapshotJson(snapshotJson);
        return version;
    }

    private ResumeProject project(Long id, String name, int sortOrder, int sort) {
        ResumeProject project = new ResumeProject();
        project.setId(id);
        project.setResumeId(1L);
        project.setProjectName(name);
        project.setSortOrder(sortOrder);
        project.setSort(sort);
        return project;
    }

    private ObjectNode snapshot(ObjectMapper mapper, String summary, List<ResumeProject> projects) {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.putNull("title");
        snapshot.putNull("realName");
        snapshot.putNull("email");
        snapshot.putNull("phone");
        snapshot.putNull("targetPosition");
        snapshot.putNull("skillStack");
        snapshot.putNull("workExperience");
        snapshot.putNull("educationExperience");
        snapshot.put("summary", summary);
        ArrayNode projectArray = snapshot.putArray("projects");
        for (ResumeProject project : projects) {
            ObjectNode value = projectArray.addObject();
            putNullable(value, "projectName", project.getProjectName());
            putNullable(value, "projectPeriod", project.getProjectPeriod());
            putNullable(value, "projectBackground", project.getProjectBackground());
            putNullable(value, "role", project.getRole());
            putNullable(value, "techStack", project.getTechStack());
            putNullable(value, "responsibility", project.getResponsibility());
            putNullable(value, "coreFeatures", project.getCoreFeatures());
            putNullable(value, "technicalDifficulties", project.getTechnicalDifficulties());
            putNullable(value, "optimizationResults", project.getOptimizationResults());
            putNullable(value, "description", project.getDescription());
            putNullable(value, "highlights", project.getHighlights());
            value.put("sort", project.getSort());
            value.put("sortOrder", project.getSortOrder());
        }
        snapshot.put("projectSnapshotSource", "RESUME_VERSION");
        return snapshot;
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void assertNoGuardedWrites(List<String> calls) {
        assertTrue(calls.stream().noneMatch(call -> call.equals("version.update")), calls.toString());
        assertTrue(calls.stream().noneMatch(call -> call.equals("version.insert")), calls.toString());
        assertTrue(calls.stream().noneMatch(call -> call.equals("resume.updateById")), calls.toString());
        assertTrue(calls.stream().noneMatch(call -> call.equals("project.delete")), calls.toString());
        assertTrue(calls.stream().noneMatch(call -> call.equals("project.insert")), calls.toString());
    }

    private void assertBefore(List<String> calls, String first, String second) {
        int firstIndex = calls.indexOf(first);
        int secondIndex = calls.indexOf(second);
        assertTrue(firstIndex >= 0, first + " missing from " + calls);
        assertTrue(secondIndex >= 0, second + " missing from " + calls);
        assertTrue(firstIndex < secondIndex, first + " must precede " + second + ": " + calls);
    }
}
