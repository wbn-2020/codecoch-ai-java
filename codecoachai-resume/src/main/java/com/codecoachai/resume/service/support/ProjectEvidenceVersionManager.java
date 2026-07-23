package com.codecoachai.resume.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.ProjectEvidence;
import com.codecoachai.resume.domain.entity.ProjectEvidenceVersion;
import com.codecoachai.resume.domain.entity.ProjectSkillEvidence;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.ProjectEvidenceVersionMapper;
import com.codecoachai.resume.mapper.ProjectSkillEvidenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ProjectEvidenceVersionManager {

    private final ProjectEvidenceVersionMapper versionMapper;
    private final ProjectSkillEvidenceMapper skillEvidenceMapper;
    private final ObjectMapper objectMapper;

    public ProjectEvidenceVersion capture(ProjectEvidence project, String sourceType, Long sourceId) {
        if (project == null || project.getId() == null || project.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目证据版本缺少归属信息。");
        }
        List<ProjectSkillEvidence> skills = skillEvidenceMapper.selectList(new LambdaQueryWrapper<ProjectSkillEvidence>()
                .eq(ProjectSkillEvidence::getProjectEvidenceId, project.getId())
                .eq(ProjectSkillEvidence::getUserId, project.getUserId())
                .eq(ProjectSkillEvidence::getDeleted, CommonConstants.NO)
                .orderByAsc(ProjectSkillEvidence::getId));
        String snapshotJson = serialize(snapshot(project, skills));
        String contentHash = ResumeArtifactHashes.sha256(snapshotJson);
        ProjectEvidenceVersion existing = versionMapper.selectByContentHash(
                project.getId(), project.getUserId(), contentHash);
        if (existing != null) {
            return existing;
        }

        ProjectEvidenceVersion latest = versionMapper.selectLatestForUpdate(project.getId(), project.getUserId());
        int nextVersion = latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
        ProjectEvidenceVersion version = new ProjectEvidenceVersion();
        version.setProjectEvidenceId(project.getId());
        version.setUserId(project.getUserId());
        version.setVersionNo(nextVersion);
        version.setSnapshotJson(snapshotJson);
        version.setContentHash(contentHash);
        version.setSourceType(StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase(Locale.ROOT) : "MANUAL");
        version.setSourceId(sourceId);
        version.setConfirmedAt(LocalDateTime.now());
        try {
            versionMapper.insert(version);
            return version;
        } catch (DuplicateKeyException exception) {
            ProjectEvidenceVersion winner = versionMapper.selectByContentHash(
                    project.getId(), project.getUserId(), contentHash);
            if (winner != null) {
                return winner;
            }
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "项目证据版本正在被其他请求创建，请刷新后重试。");
        }
    }

    public ProjectEvidenceVersion current(Long userId, Long projectEvidenceId) {
        if (userId == null || projectEvidenceId == null) {
            return null;
        }
        return versionMapper.selectOne(new LambdaQueryWrapper<ProjectEvidenceVersion>()
                .eq(ProjectEvidenceVersion::getProjectEvidenceId, projectEvidenceId)
                .eq(ProjectEvidenceVersion::getUserId, userId)
                .eq(ProjectEvidenceVersion::getDeleted, CommonConstants.NO)
                .orderByDesc(ProjectEvidenceVersion::getVersionNo)
                .orderByDesc(ProjectEvidenceVersion::getId)
                .last("LIMIT 1"));
    }

    public ProjectEvidenceVersion ownedVersion(Long userId, Long projectEvidenceId, String assetVersion) {
        Integer versionNo = parseVersion(assetVersion);
        if (userId == null || projectEvidenceId == null || versionNo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "项目证据版本参数无效。");
        }
        ProjectEvidenceVersion version = versionMapper.selectOwnedVersion(projectEvidenceId, userId, versionNo);
        if (version == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目证据版本不存在或已失效。");
        }
        return version;
    }

    public Map<String, Object> snapshot(ProjectEvidence project, List<ProjectSkillEvidence> skills) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", project.getTitle());
        value.put("role", project.getRole());
        value.put("startDate", project.getStartDate());
        value.put("endDate", project.getEndDate());
        value.put("background", project.getBackground());
        value.put("responsibility", project.getResponsibility());
        value.put("techStack", project.getTechStack());
        value.put("difficulty", project.getDifficulty());
        value.put("solution", project.getSolution());
        value.put("result", project.getResult());
        value.put("reflection", project.getReflection());
        value.put("completenessScore", project.getCompletenessScore());
        value.put("completenessStatus", project.getCompletenessStatus());
        value.put("missingFields", project.getMissingFields());
        List<Map<String, Object>> skillSnapshots = new ArrayList<>();
        if (skills != null) {
            for (ProjectSkillEvidence skill : skills) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", skill.getId());
                item.put("skillName", skill.getSkillName());
                item.put("skillCategory", skill.getSkillCategory());
                item.put("evidenceText", skill.getEvidenceText());
                item.put("strengthLevel", skill.getStrengthLevel());
                item.put("jdKeyword", skill.getJdKeyword());
                item.put("riskPoints", skill.getRiskPoints());
                item.put("sourceType", skill.getSourceType());
                item.put("confirmed", skill.getConfirmed());
                skillSnapshots.add(item);
            }
        }
        value.put("skillEvidences", skillSnapshots);
        return value;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目证据版本快照序列化失败。");
        }
    }

    private Integer parseVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "V", 0, 1)) {
            normalized = normalized.substring(1);
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
