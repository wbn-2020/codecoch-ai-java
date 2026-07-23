package com.codecoachai.resume.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.JobApplicationPackageSnapshot;
import com.codecoachai.resume.domain.vo.JobApplicationPackageVO;
import com.codecoachai.resume.export.ResumeArtifactHashes;
import com.codecoachai.resume.mapper.JobApplicationPackageSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobApplicationPackageSnapshotManager {

    private final JobApplicationPackageSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    public JobApplicationPackageSnapshot capture(Long userId, Long packageId,
                                                 JobApplicationPackageVO snapshot,
                                                 String captureSource) {
        if (userId == null || packageId == null || snapshot == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递包快照缺少归属信息。");
        }
        String snapshotJson = write(snapshot);
        String checklistJson = write(snapshot.getChecklist());
        String actionsJson = write(snapshot.getActions());
        String projectEvidenceIdsJson = write(snapshot.getProjectEvidenceIds());
        String contentHash = ResumeArtifactHashes.sha256(canonical(snapshotJson, checklistJson,
                actionsJson, projectEvidenceIdsJson, snapshot.getRecommendedResumeVersionId(),
                snapshot.getMatchReportId()));
        JobApplicationPackageSnapshot existing = snapshotMapper.selectByContentHash(
                packageId, userId, contentHash);
        if (existing != null) {
            return existing;
        }

        JobApplicationPackageSnapshot latest = snapshotMapper.selectLatestForUpdate(packageId, userId);
        int nextVersion = latest == null || latest.getSnapshotVersion() == null
                ? 1 : latest.getSnapshotVersion() + 1;
        JobApplicationPackageSnapshot row = new JobApplicationPackageSnapshot();
        row.setPackageId(packageId);
        row.setUserId(userId);
        row.setSnapshotVersion(nextVersion);
        row.setSnapshotJson(snapshotJson);
        row.setChecklistJson(checklistJson);
        row.setActionsJson(actionsJson);
        row.setProjectEvidenceIdsJson(projectEvidenceIdsJson);
        row.setResumeVersionId(snapshot.getRecommendedResumeVersionId());
        row.setMatchReportId(snapshot.getMatchReportId());
        row.setContentHash(contentHash);
        row.setCapturedAt(snapshot.getRefreshedAt() == null ? LocalDateTime.now() : snapshot.getRefreshedAt());
        row.setCaptureSource(captureSource == null ? "SAVE" : captureSource);
        try {
            snapshotMapper.insert(row);
            return row;
        } catch (DuplicateKeyException exception) {
            JobApplicationPackageSnapshot winner = snapshotMapper.selectByContentHash(
                    packageId, userId, contentHash);
            if (winner != null) {
                return winner;
            }
            throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "投递包快照正在被其他请求创建，请刷新后重试。");
        }
    }

    public JobApplicationPackageSnapshot owned(Long userId, Long snapshotId) {
        if (userId == null || snapshotId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "投递包快照参数无效。");
        }
        JobApplicationPackageSnapshot row = snapshotMapper.selectOwned(snapshotId, userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "投递包快照不存在或无权访问。");
        }
        return row;
    }

    public String canonical(String snapshotJson, String checklistJson, String actionsJson,
                            String projectEvidenceIdsJson, Long resumeVersionId, Long matchReportId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("snapshotJson", snapshotJson);
        value.put("checklistJson", checklistJson);
        value.put("actionsJson", actionsJson);
        value.put("projectEvidenceIdsJson", projectEvidenceIdsJson);
        value.put("resumeVersionId", resumeVersionId);
        value.put("matchReportId", matchReportId);
        return write(value);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "投递包快照序列化失败。");
        }
    }
}
