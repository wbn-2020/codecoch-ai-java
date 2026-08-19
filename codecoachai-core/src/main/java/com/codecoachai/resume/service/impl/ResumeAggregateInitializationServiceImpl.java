package com.codecoachai.resume.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.entity.Resume;
import com.codecoachai.resume.domain.entity.ResumeVersion;
import com.codecoachai.resume.mapper.ResumeMapper;
import com.codecoachai.resume.service.ResumeAggregateInitializationService;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import com.codecoachai.resume.service.support.ResumeVersionSnapshotManager;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResumeAggregateInitializationServiceImpl
        implements ResumeAggregateInitializationService {

    private static final String DEFAULT_SOURCE_TYPE = "RESUME_CREATE";

    private final ResumeMapper resumeMapper;
    private final ResumeVersionSnapshotManager snapshotManager;
    private final ResumeSearchSyncOutboxService searchSyncOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeVersion initializeCreatedResume(
            Long resumeId, Long userId, String sourceType, Long sourceId) {
        if (resumeId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "resume id and user id are required");
        }
        Resume resume = snapshotManager.ownedResume(resumeId, userId);
        ResumeVersion version = snapshotManager.ensureInitialVersion(
                resume,
                sourceType == null || sourceType.isBlank() ? DEFAULT_SOURCE_TYPE : sourceType.trim(),
                sourceId,
                "Initial version");
        boolean listVisible = resumeMapper.selectResumeList(userId, null, null, null)
                .stream()
                .anyMatch(item -> Objects.equals(resumeId, item.getId()));
        if (!listVisible) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "created resume is not visible in the owner resume list");
        }
        searchSyncOutboxService.enqueue(
                resumeId, userId, ResumeSearchSyncOutboxService.OP_UPSERT);
        return version;
    }
}
