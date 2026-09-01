package com.codecoachai.resume.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeProject;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.mapper.ResumeProjectMapper;
import com.codecoachai.resume.mapper.ResumeVersionMapper;
import com.codecoachai.resume.support.ResumeAnchorResolver;
import com.codecoachai.resume.support.ResumeDocumentNormalizer;
import com.codecoachai.resume.support.ResumeDocumentProjector;
import com.codecoachai.resume.support.ResumeDocumentStore;
import com.codecoachai.resume.support.ResumePresentationConfigNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ResumeVersionSnapshotManager {

    private static final int VERSION_INSERT_ATTEMPTS = 5;
    private static final String PROJECT_SNAPSHOT_SOURCE = "RESUME_VERSION";
    private static final Pattern PROJECT_SECTION = Pattern.compile("projects\\[(\\d+)]\\.([A-Za-z][A-Za-z0-9]*)");

    private static final String IDENTITY_ANCHOR_PREFIX = "section:";
    private static final List<String> MIRROR_FIELDS = List.of("realName", "targetPosition", "email", "phone",
            "summary", "skillStack", "workExperience", "educationExperience");
    private static final Map<String, String> MIRROR_PROJECT_FIELDS = Map.of(
            "projectName", "projectName",
            "projectPeriod", "projectTime",
            "role", "role",
            "techStack", "techStack",
            "projectBackground", "projectBackground",
            "coreFeatures", "coreFeatures",
            "technicalDifficulties", "technicalChallenges",
            "optimizationResults", "optimizationResult");

    private final ResumeMapper resumeMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public ResumeVersionSnapshotManager(ResumeMapper resumeMapper, ResumeProjectMapper projectMapper,
                                        ResumeVersionMapper versionMapper, ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    public ResumeVersion ownedVersion(Long versionId, Long userId) {
        ResumeVersion version = versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume version does not exist");
        }
        return version;
    }

    public Resume ownedResume(Long resumeId, Long userId) {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId)
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("LIMIT 1"));
        if (resume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume does not exist");
        }
        return resume;
    }

    public void lockOwnedResume(Resume resume) {
        Long lockedId = resumeMapper.lockOwnedResume(resume.getId(), resume.getUserId());
        if (!Objects.equals(resume.getId(), lockedId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume does not exist");
        }
    }

    public ObjectNode readSnapshot(ResumeVersion version) {
        try {
            JsonNode root = objectMapper.readTree(version.getSnapshotJson());
            if (!(root instanceof ObjectNode object)) {
                throw new IllegalArgumentException();
            }
            return object.deepCopy();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume version snapshot is invalid");
        }
    }

    public String sectionText(ObjectNode snapshot, String sectionKey) {
        if (isIdentityAnchor(sectionKey)) {
            String anchored = ResumeAnchorResolver.resolveText(requireDocument(snapshot), sectionKey);
            if (anchored == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "Suggestion anchor no longer matches its source text");
            }
            return anchored;
        }
        JsonNode node = sectionNode(snapshot, sectionKey);
        if (node == null || node.isNull() || !node.isValueNode()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Suggestion section is not a text field");
        }
        return node.asText();
    }

    public void replaceSectionText(ObjectNode snapshot, String sectionKey, int start, int end,
                                   String expected, String replacement) {
        String current = sectionText(snapshot, sectionKey);
        if (start < 0 || end < start || end > current.length()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Suggestion anchor is outside the source text");
        }
        if (!Objects.equals(current.substring(start, end), expected)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Suggestion anchor no longer matches its source text");
        }
        String updated = current.substring(0, start) + replacement + current.substring(end);
        if (isIdentityAnchor(sectionKey)) {
            snapshot.set("document", ResumeAnchorResolver.applyReplacement(
                    objectMapper, requireDocument(snapshot), sectionKey, updated));
            syncMirrorFromDocument(snapshot);
            return;
        }
        setSectionNode(snapshot, sectionKey, updated);
    }

    private ObjectNode requireDocument(ObjectNode snapshot) {
        JsonNode document = snapshot.get("document");
        if (document == null || !document.isObject()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Suggestion anchor needs a structured resume document");
        }
        return (ObjectNode) document;
    }

    private static boolean isIdentityAnchor(String sectionKey) {
        return sectionKey != null && sectionKey.startsWith(IDENTITY_ANCHOR_PREFIX);
    }

    /**
     * Once an anchored suggestion lands, the document is canonical, so the snapshot's flat mirror is
     * rebuilt from it and older readers plus the ATS export keep seeing current text.
     */
    private void syncMirrorFromDocument(ObjectNode snapshot) {
        ObjectNode projected = ResumeDocumentProjector.project(objectMapper, snapshot.path("document"));
        MIRROR_FIELDS.forEach(field -> snapshot.put(field, projected.path(field).asText("")));

        if (snapshot.get("presentationConfig") instanceof ObjectNode config) {
            config.set("sectionOrder", projected.path("sectionOrder").deepCopy());
            config.set("hiddenSections", projected.path("hiddenSections").deepCopy());
            snapshot.set("presentationConfig",
                    ResumePresentationConfigNormalizer.normalize(objectMapper, config));
        }

        JsonNode rows = snapshot.path("projects");
        JsonNode projectedProjects = projected.path("projects");
        if (rows.isArray() && projectedProjects.isArray()) {
            int shared = Math.min(rows.size(), projectedProjects.size());
            for (int index = 0; index < shared; index++) {
                if (!(rows.get(index) instanceof ObjectNode row)) {
                    continue;
                }
                JsonNode source = projectedProjects.get(index);
                MIRROR_PROJECT_FIELDS.forEach((column, field) ->
                        row.put(column, source.path(field).asText("")));
            }
        }
    }

    public ResumeVersion insertAndApply(Resume resume, ObjectNode snapshot, String sourceType, Long sourceId,
                                        String versionName) {
        lockOwnedResume(resume);
        return insertAndApplyLocked(resume, snapshot, sourceType, sourceId, versionName);
    }

    public ResumeVersion ensureInitialVersion(
            Resume resume, String sourceType, Long sourceId, String versionName) {
        lockOwnedResume(resume);
        ResumeVersion current = versionMapper.selectCurrentForUpdate(
                resume.getUserId(), resume.getId());
        if (current != null) {
            return current;
        }
        List<ResumeProject> projects = projectMapper.selectActiveByResumeIdForUpdate(resume.getId());
        ObjectNode snapshot = liveSnapshot(
                resume, projects == null ? List.of() : projects);
        return insertSnapshotOnlyLocked(resume, snapshot, sourceType, sourceId, versionName);
    }

    public ResumeVersion insertAndApplyIfCurrent(Resume resume, Long expectedCurrentVersionId,
                                                 ObjectNode snapshot, String sourceType, Long sourceId,
                                                 String versionName) {
        lockOwnedResume(resume);
        Resume lockedResume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .eq(Resume::getUserId, resume.getUserId())
                .eq(Resume::getDeleted, CommonConstants.NO)
                .last("LIMIT 1 FOR UPDATE"));
        if (lockedResume == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Resume does not exist");
        }
        ResumeVersion current = versionMapper.selectCurrentForUpdate(
                lockedResume.getUserId(), lockedResume.getId());
        List<ResumeProject> liveProjects = projectMapper.selectActiveByResumeIdForUpdate(lockedResume.getId());
        if (liveProjects == null) {
            liveProjects = List.of();
        }
        if (current == null || !Objects.equals(expectedCurrentVersionId, current.getId())) {
            throw staleSourceVersion();
        }
        ObjectNode expectedSnapshot = canonicalSnapshot(readSnapshot(current));
        ObjectNode liveSnapshot = liveSnapshot(lockedResume, liveProjects);
        if (!Objects.equals(
                ResumeArtifactHashes.sha256(write(contentSnapshot(expectedSnapshot))),
                ResumeArtifactHashes.sha256(write(contentSnapshot(liveSnapshot))))) {
            throw staleSourceVersion();
        }
        return insertAndApplyLocked(lockedResume, snapshot, sourceType, sourceId, versionName);
    }

    private BusinessException staleSourceVersion() {
        return new BusinessException(
                ErrorCode.STALE_SOURCE_VERSION,
                "Resume content no longer matches the current version; refresh suggestions before retrying");
    }

    private ObjectNode liveSnapshot(Resume resume, List<ResumeProject> projects) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        putText(snapshot, "title", resume.getTitle());
        putText(snapshot, "realName", resume.getRealName());
        putText(snapshot, "email", resume.getEmail());
        putText(snapshot, "phone", resume.getPhone());
        putText(snapshot, "targetPosition", resume.getTargetPosition());
        putText(snapshot, "skillStack", resume.getSkillStack());
        putText(snapshot, "workExperience", resume.getWorkExperience());
        putText(snapshot, "educationExperience", resume.getEducationExperience());
        putText(snapshot, "summary", resume.getSummary());
        snapshot.set("presentationConfig", ResumePresentationConfigNormalizer.parseStored(
                objectMapper, resume.getPresentationConfigJson()));
        putDocument(snapshot, ResumeDocumentStore.readStored(objectMapper, resume.getDocumentJson()));
        List<ObjectNode> projectSnapshots = new ArrayList<>();
        for (ResumeProject project : projects) {
            ObjectNode projectSnapshot = objectMapper.createObjectNode();
            projectSnapshot.put("projectId", project.getId());
            putText(projectSnapshot, "projectName", project.getProjectName());
            putText(projectSnapshot, "projectPeriod", project.getProjectPeriod());
            putText(projectSnapshot, "projectBackground", project.getProjectBackground());
            putText(projectSnapshot, "role", project.getRole());
            putText(projectSnapshot, "techStack", project.getTechStack());
            putText(projectSnapshot, "responsibility", project.getResponsibility());
            putText(projectSnapshot, "coreFeatures", project.getCoreFeatures());
            putText(projectSnapshot, "technicalDifficulties", project.getTechnicalDifficulties());
            putText(projectSnapshot, "optimizationResults", project.getOptimizationResults());
            putText(projectSnapshot, "description", project.getDescription());
            putText(projectSnapshot, "highlights", project.getHighlights());
            putInteger(projectSnapshot, "sort", zeroIfNull(project.getSort()));
            putInteger(projectSnapshot, "sortOrder", zeroIfNull(project.getSortOrder()));
            projectSnapshots.add(projectSnapshot);
        }
        appendCanonicalProjects(snapshot, projectSnapshots);
        snapshot.put("projectSnapshotSource", PROJECT_SNAPSHOT_SOURCE);
        return snapshot;
    }

    private ObjectNode canonicalSnapshot(JsonNode source) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        copyText(snapshot, source, "title");
        copyText(snapshot, source, "realName");
        copyText(snapshot, source, "email");
        copyText(snapshot, source, "phone");
        copyText(snapshot, source, "targetPosition");
        copyText(snapshot, source, "skillStack");
        copyText(snapshot, source, "workExperience");
        copyText(snapshot, source, "educationExperience");
        copyText(snapshot, source, "summary");
        snapshot.set("presentationConfig", ResumePresentationConfigNormalizer.normalize(
                objectMapper, source == null ? null : source.get("presentationConfig")));
        putDocument(snapshot, ResumeDocumentNormalizer.normalize(
                objectMapper, source == null ? null : source.get("document")));
        List<ObjectNode> projectSnapshots = new ArrayList<>();
        JsonNode projects = source == null ? null : source.get("projects");
        if (projects != null && projects.isArray()) {
            for (JsonNode project : projects) {
                ObjectNode projectSnapshot = objectMapper.createObjectNode();
                copyLong(projectSnapshot, project, "projectId");
                copyText(projectSnapshot, project, "projectName");
                copyText(projectSnapshot, project, "projectPeriod");
                copyText(projectSnapshot, project, "projectBackground");
                copyText(projectSnapshot, project, "role");
                copyText(projectSnapshot, project, "techStack");
                copyText(projectSnapshot, project, "responsibility");
                copyText(projectSnapshot, project, "coreFeatures");
                copyText(projectSnapshot, project, "technicalDifficulties");
                copyText(projectSnapshot, project, "optimizationResults");
                copyText(projectSnapshot, project, "description");
                copyText(projectSnapshot, project, "highlights");
                copyInteger(projectSnapshot, project, "sort");
                copyInteger(projectSnapshot, project, "sortOrder");
                projectSnapshots.add(projectSnapshot);
            }
        }
        appendCanonicalProjects(snapshot, projectSnapshots);
        String projectSnapshotSource = text(source, "projectSnapshotSource");
        snapshot.put("projectSnapshotSource", StringUtils.hasText(projectSnapshotSource)
                ? projectSnapshotSource
                : PROJECT_SNAPSHOT_SOURCE);
        return snapshot;
    }

    private void putDocument(ObjectNode snapshot, JsonNode document) {
        if (document != null && document.isObject()) {
            snapshot.set("document", document);
        }
    }

    private void appendCanonicalProjects(ObjectNode snapshot, List<ObjectNode> projects) {
        projects.sort(Comparator
                .comparingInt((ObjectNode project) -> integer(project, "sortOrder", 0))
                .thenComparingInt(project -> integer(project, "sort", 0))
                .thenComparing(this::write));
        ArrayNode projectSnapshots = snapshot.putArray("projects");
        projects.forEach(projectSnapshots::add);
    }

    private void copyText(ObjectNode target, JsonNode source, String field) {
        putText(target, field, text(source, field));
    }

    private void copyInteger(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source == null ? null : source.get(field);
        putInteger(target, field, value == null || value.isNull() || !value.canConvertToInt() ? 0 : value.asInt());
    }

    private void copyLong(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source == null ? null : source.get(field);
        if (value != null && value.canConvertToLong() && value.asLong() > 0) {
            target.put(field, value.asLong());
        }
    }

    private void putText(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private void putInteger(ObjectNode target, String field, Integer value) {
        target.put(field, zeroIfNull(value));
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private ResumeVersion insertAndApplyLocked(Resume resume, ObjectNode snapshot, String sourceType, Long sourceId,
                                               String versionName) {
        ObjectNode normalizedSnapshot = canonicalSnapshot(snapshot);
        String snapshotJson = write(normalizedSnapshot);
        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < VERSION_INSERT_ATTEMPTS; attempt++) {
            int nextNo = nextVersionNo(resume.getId(), resume.getUserId());
            ResumeVersion version = new ResumeVersion();
            version.setUserId(resume.getUserId());
            version.setResumeId(resume.getId());
            version.setVersionNo(nextNo);
            version.setVersionName(StringUtils.hasText(versionName) ? versionName : "V" + nextNo);
            version.setSnapshotJson(snapshotJson);
            version.setSourceType(sourceType);
            version.setSourceId(sourceId);
            version.setCurrentFlag(1);
            try {
                versionMapper.update(null, new LambdaUpdateWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getUserId, resume.getUserId())
                        .eq(ResumeVersion::getResumeId, resume.getId())
                        .set(ResumeVersion::getCurrentFlag, 0));
                versionMapper.insert(version);
                applySnapshot(resume, normalizedSnapshot);
                persistDocument(resume, normalizedSnapshot);
                resumeMapper.updateById(resume);
                List<ResumeProject> restoredProjects = restoreProjects(resume.getId(), normalizedSnapshot);
                if (ResumeDocumentStore.writeProjects(objectMapper, resume, restoredProjects) != null) {
                    resumeMapper.updateById(resume);
                }
                return version;
            } catch (DuplicateKeyException ex) {
                lastConflict = ex;
            }
        }
        throw lastConflict == null
                ? new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to allocate resume version")
                : lastConflict;
    }

    private ResumeVersion insertSnapshotOnlyLocked(
            Resume resume, ObjectNode snapshot, String sourceType, Long sourceId, String versionName) {
        String snapshotJson = write(snapshot);
        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < VERSION_INSERT_ATTEMPTS; attempt++) {
            ResumeVersion existing = versionMapper.selectCurrentForUpdate(
                    resume.getUserId(), resume.getId());
            if (existing != null) {
                return existing;
            }
            int nextNo = nextVersionNo(resume.getId(), resume.getUserId());
            ResumeVersion version = new ResumeVersion();
            version.setUserId(resume.getUserId());
            version.setResumeId(resume.getId());
            version.setVersionNo(nextNo);
            version.setVersionName(StringUtils.hasText(versionName) ? versionName : "V" + nextNo);
            version.setSnapshotJson(snapshotJson);
            version.setSourceType(sourceType);
            version.setSourceId(sourceId);
            version.setCurrentFlag(CommonConstants.YES);
            try {
                versionMapper.insert(version);
                return version;
            } catch (DuplicateKeyException ex) {
                lastConflict = ex;
            }
        }
        ResumeVersion winner = versionMapper.selectCurrentForUpdate(
                resume.getUserId(), resume.getId());
        if (winner != null) {
            return winner;
        }
        throw lastConflict == null
                ? new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to allocate initial resume version")
                : lastConflict;
    }

    public String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Resume snapshot serialization failed");
        }
    }

    private JsonNode sectionNode(ObjectNode snapshot, String sectionKey) {
        Matcher matcher = PROJECT_SECTION.matcher(sectionKey);
        if (matcher.matches()) {
            JsonNode projects = snapshot.path("projects");
            int index = Integer.parseInt(matcher.group(1));
            return projects.isArray() && index < projects.size() ? projects.get(index).get(matcher.group(2)) : null;
        }
        return allowedTopLevel(sectionKey) ? snapshot.get(sectionKey) : null;
    }

    private void setSectionNode(ObjectNode snapshot, String sectionKey, String value) {
        Matcher matcher = PROJECT_SECTION.matcher(sectionKey);
        if (matcher.matches()) {
            JsonNode projects = snapshot.path("projects");
            int index = Integer.parseInt(matcher.group(1));
            if (!projects.isArray() || index >= projects.size() || !(projects.get(index) instanceof ObjectNode project)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Suggestion project anchor is invalid");
            }
            project.put(matcher.group(2), value);
            return;
        }
        if (!allowedTopLevel(sectionKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Suggestion section is not supported");
        }
        snapshot.put(sectionKey, value);
    }

    private boolean allowedTopLevel(String key) {
        return java.util.Set.of("title", "realName", "email", "phone", "targetPosition", "skillStack",
                "workExperience", "educationExperience", "summary").contains(key);
    }

    private int nextVersionNo(Long resumeId, Long userId) {
        ResumeVersion latest = versionMapper.selectLatestForUpdate(userId, resumeId);
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private void applySnapshot(Resume resume, ObjectNode snapshot) {
        resume.setTitle(text(snapshot, "title"));
        resume.setRealName(text(snapshot, "realName"));
        resume.setEmail(text(snapshot, "email"));
        resume.setPhone(text(snapshot, "phone"));
        resume.setTargetPosition(text(snapshot, "targetPosition"));
        resume.setSkillStack(text(snapshot, "skillStack"));
        resume.setWorkExperience(text(snapshot, "workExperience"));
        resume.setEducationExperience(text(snapshot, "educationExperience"));
        resume.setSummary(text(snapshot, "summary"));
        resume.setPresentationConfigJson(ResumePresentationConfigNormalizer.normalizeJson(
                objectMapper, snapshot.get("presentationConfig")));
    }

    /**
     * A snapshot without a document hands authority back to the flat mirror, and clearing the column
     * takes an explicit update because null-valued fields are skipped otherwise.
     */
    private void persistDocument(Resume resume, ObjectNode snapshot) {
        JsonNode document = snapshot.get("document");
        String json = document != null && document.isObject() ? write(document) : null;
        resume.setDocumentJson(json);
        if (json == null) {
            resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                    .eq(Resume::getId, resume.getId())
                    .set(Resume::getDocumentJson, null));
        }
    }

    private List<ResumeProject> restoreProjects(Long resumeId, ObjectNode snapshot) {
        if (!snapshot.has("projects")) {
            return List.of();
        }
        projectMapper.delete(new LambdaQueryWrapper<ResumeProject>().eq(ResumeProject::getResumeId, resumeId));
        JsonNode projects = snapshot.path("projects");
        if (!projects.isArray()) {
            return List.of();
        }
        List<ResumeProject> restored = new ArrayList<>();
        for (JsonNode value : projects) {
            if (!value.isObject()) {
                continue;
            }
            ResumeProject project = new ResumeProject();
            project.setId(positiveLong(value, "projectId"));
            project.setResumeId(resumeId);
            project.setProjectName(text(value, "projectName"));
            project.setProjectPeriod(text(value, "projectPeriod"));
            project.setProjectBackground(text(value, "projectBackground"));
            project.setRole(text(value, "role"));
            project.setTechStack(text(value, "techStack"));
            project.setResponsibility(text(value, "responsibility"));
            project.setCoreFeatures(text(value, "coreFeatures"));
            project.setTechnicalDifficulties(text(value, "technicalDifficulties"));
            project.setOptimizationResults(text(value, "optimizationResults"));
            project.setDescription(text(value, "description"));
            project.setHighlights(text(value, "highlights"));
            project.setSort(integer(value, "sort", 0));
            project.setSortOrder(integer(value, "sortOrder", project.getSort()));
            if (project.getId() == null || projectMapper.restoreSnapshotById(project) == 0) {
                projectMapper.insert(project);
            }
            restored.add(project);
        }
        return restored;
    }

    private ObjectNode contentSnapshot(ObjectNode snapshot) {
        ObjectNode content = snapshot.deepCopy();
        for (JsonNode project : content.path("projects")) {
            if (project instanceof ObjectNode object) {
                object.remove("projectId");
            }
        }
        return content;
    }

    private Long positiveLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.canConvertToInt() ? fallback : value.asInt();
    }
}
