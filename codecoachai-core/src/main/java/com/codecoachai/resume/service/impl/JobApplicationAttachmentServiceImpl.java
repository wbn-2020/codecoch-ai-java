package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.resume.domain.entity.JobApplication;
import com.codecoachai.resume.domain.entity.JobApplicationAttachment;
import com.codecoachai.resume.domain.entity.JobApplicationPackage;
import com.codecoachai.resume.domain.vo.JobApplicationAttachmentVO;
import com.codecoachai.resume.feign.FileFeignClient;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import com.codecoachai.resume.mapper.JobApplicationAttachmentMapper;
import com.codecoachai.resume.mapper.JobApplicationMapper;
import com.codecoachai.resume.mapper.JobApplicationPackageMapper;
import com.codecoachai.resume.service.JobApplicationAttachmentService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationAttachmentServiceImpl implements JobApplicationAttachmentService {

    private static final String FILE_BIZ_TYPE = "ATTACHMENT";
    private static final String DEFAULT_ATTACHMENT_TYPE = "OTHER";
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_DISPLAY_NAME_LENGTH = 255;
    private static final Set<String> ATTACHMENT_TYPES = Set.of(
            "RESUME", "COVER_LETTER", "PORTFOLIO", "CERTIFICATE", DEFAULT_ATTACHMENT_TYPE);

    private final JobApplicationMapper applicationMapper;
    private final JobApplicationPackageMapper packageMapper;
    private final JobApplicationAttachmentMapper attachmentMapper;
    private final FileFeignClient fileFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationAttachmentVO upload(
            Long packageId, MultipartFile file, String attachmentType, String displayName) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationPackage applicationPackage = ownedPackage(packageId, userId);
        return createAttachment(
                userId,
                applicationPackage.getId(),
                applicationPackage.getApplicationId(),
                file,
                attachmentType,
                displayName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationAttachmentVO uploadForApplication(
            Long applicationId, MultipartFile file, String attachmentType, String displayName) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplication application = ownedApplication(applicationId, userId);
        return createAttachment(
                userId,
                null,
                application.getId(),
                file,
                attachmentType,
                displayName);
    }

    private JobApplicationAttachmentVO createAttachment(
            Long userId,
            Long packageId,
            Long applicationId,
            MultipartFile file,
            String attachmentType,
            String displayName) {
        validateFile(file);
        String normalizedType = normalizeAttachmentType(attachmentType);
        String normalizedDisplayName = normalizeDisplayName(displayName, file.getOriginalFilename());
        InnerFileUploadVO uploaded = uploadFile(file, userId);
        boolean rollbackCleanupRegistered = deleteFileAfterRollback(
                uploaded.getFileId(), userId, "attachment transaction rolled back");
        try {
            JobApplicationAttachment attachment = new JobApplicationAttachment();
            attachment.setUserId(userId);
            attachment.setPackageId(packageId);
            attachment.setApplicationId(applicationId);
            applyUpload(attachment, uploaded, normalizedType, normalizedDisplayName);
            attachment.setSortOrder(nextSortOrder(packageId, applicationId, userId));
            attachmentMapper.insert(attachment);
            return toVO(requirePersisted(attachment.getId(), userId));
        } catch (RuntimeException ex) {
            if (!rollbackCleanupRegistered) {
                deleteFileQuietly(uploaded.getFileId(), userId, "attachment association insert failed");
            }
            throw ex;
        }
    }

    @Override
    public List<JobApplicationAttachmentVO> list(Long packageId) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationPackage applicationPackage = ownedPackage(packageId, userId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<JobApplicationAttachment>()
                        .eq(JobApplicationAttachment::getUserId, userId)
                        .eq(JobApplicationAttachment::getPackageId, packageId)
                        .eq(JobApplicationAttachment::getDeleted, CommonConstants.NO)
                        .orderByAsc(JobApplicationAttachment::getSortOrder)
                        .orderByAsc(JobApplicationAttachment::getId))
                .stream()
                .peek(attachment -> requirePackageAttachmentRelation(applicationPackage, attachment))
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<JobApplicationAttachmentVO> listForApplication(Long applicationId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(applicationId, userId);
        Set<Long> packageIds = packageMapper.selectList(new LambdaQueryWrapper<JobApplicationPackage>()
                        .select(JobApplicationPackage::getId)
                        .eq(JobApplicationPackage::getUserId, userId)
                        .eq(JobApplicationPackage::getApplicationId, applicationId)
                        .eq(JobApplicationPackage::getDeleted, CommonConstants.NO))
                .stream()
                .map(JobApplicationPackage::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LambdaQueryWrapper<JobApplicationAttachment> query =
                new LambdaQueryWrapper<JobApplicationAttachment>()
                        .eq(JobApplicationAttachment::getUserId, userId)
                        .eq(JobApplicationAttachment::getDeleted, CommonConstants.NO)
                        .and(scope -> {
                            scope.eq(JobApplicationAttachment::getApplicationId, applicationId);
                            if (!packageIds.isEmpty()) {
                                scope.or().in(JobApplicationAttachment::getPackageId, packageIds);
                            }
                        })
                        .orderByAsc(JobApplicationAttachment::getSortOrder)
                        .orderByAsc(JobApplicationAttachment::getId);
        return attachmentMapper.selectList(query)
                .stream()
                .filter(attachment -> Objects.equals(applicationId, attachment.getApplicationId())
                        || (attachment.getApplicationId() == null
                        && packageIds.contains(attachment.getPackageId())))
                .map(this::toVO)
                .toList();
    }

    @Override
    public JobApplicationAttachmentVO downloadMetadata(Long packageId, Long attachmentId) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationPackage applicationPackage = ownedPackage(packageId, userId);
        return toVO(ownedAttachment(applicationPackage, attachmentId, userId));
    }

    @Override
    public ResponseEntity<Resource> download(Long packageId, Long attachmentId) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationPackage applicationPackage = ownedPackage(packageId, userId);
        JobApplicationAttachment attachment =
                ownedAttachment(applicationPackage, attachmentId, userId);
        return downloadFile(attachment, userId);
    }

    @Override
    public ResponseEntity<Resource> downloadForApplication(Long applicationId, Long attachmentId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(applicationId, userId);
        JobApplicationAttachment attachment =
                ownedApplicationAttachment(applicationId, attachmentId, userId);
        return downloadFile(attachment, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationAttachmentVO replace(
            Long packageId, Long attachmentId, MultipartFile file,
            String attachmentType, String displayName) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationPackage applicationPackage = ownedPackage(packageId, userId);
        JobApplicationAttachment attachment =
                ownedAttachment(applicationPackage, attachmentId, userId);
        return replaceAttachment(attachment, file, attachmentType, displayName, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplicationAttachmentVO replaceForApplication(
            Long applicationId, Long attachmentId, MultipartFile file,
            String attachmentType, String displayName) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(applicationId, userId);
        JobApplicationAttachment attachment =
                ownedApplicationAttachment(applicationId, attachmentId, userId);
        return replaceAttachment(attachment, file, attachmentType, displayName, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long packageId, Long attachmentId) {
        Long userId = SecurityAssert.requireLoginUserId();
        JobApplicationPackage applicationPackage = ownedPackage(packageId, userId);
        JobApplicationAttachment attachment =
                ownedAttachment(applicationPackage, attachmentId, userId);
        deleteAttachment(attachment, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForApplication(Long applicationId, Long attachmentId) {
        Long userId = SecurityAssert.requireLoginUserId();
        ownedApplication(applicationId, userId);
        JobApplicationAttachment attachment =
                ownedApplicationAttachment(applicationId, attachmentId, userId);
        deleteAttachment(attachment, userId);
    }

    private JobApplication ownedApplication(Long applicationId, Long userId) {
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null || CommonConstants.YES.equals(application.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "job application does not exist");
        }
        if (!Objects.equals(userId, application.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "job application belongs to another user");
        }
        return application;
    }

    private JobApplicationPackage ownedPackage(Long packageId, Long userId) {
        JobApplicationPackage applicationPackage = packageMapper.selectById(packageId);
        if (applicationPackage == null || CommonConstants.YES.equals(applicationPackage.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "application package does not exist");
        }
        if (!Objects.equals(userId, applicationPackage.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "application package belongs to another user");
        }
        if (applicationPackage.getApplicationId() != null) {
            ownedApplication(applicationPackage.getApplicationId(), userId);
        }
        return applicationPackage;
    }

    private JobApplicationAttachment ownedAttachment(
            JobApplicationPackage applicationPackage, Long attachmentId, Long userId) {
        JobApplicationAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || CommonConstants.YES.equals(attachment.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "application attachment does not exist");
        }
        if (!Objects.equals(userId, attachment.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "application attachment belongs to another user");
        }
        if (!Objects.equals(applicationPackage.getId(), attachment.getPackageId())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "application attachment does not belong to this package");
        }
        requirePackageAttachmentRelation(applicationPackage, attachment);
        return attachment;
    }

    private JobApplicationAttachment ownedApplicationAttachment(
            Long applicationId, Long attachmentId, Long userId) {
        JobApplicationAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || CommonConstants.YES.equals(attachment.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "application attachment does not exist");
        }
        if (!Objects.equals(userId, attachment.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "application attachment belongs to another user");
        }
        if (Objects.equals(applicationId, attachment.getApplicationId())) {
            return attachment;
        }
        if (attachment.getApplicationId() == null && attachment.getPackageId() != null) {
            JobApplicationPackage applicationPackage = ownedPackage(attachment.getPackageId(), userId);
            if (Objects.equals(applicationId, applicationPackage.getApplicationId())) {
                return attachment;
            }
        }
        throw new BusinessException(
                ErrorCode.RESOURCE_RELATION_CONFLICT,
                "application attachment does not belong to this job application");
    }

    private void requirePackageAttachmentRelation(
            JobApplicationPackage applicationPackage, JobApplicationAttachment attachment) {
        if (attachment.getApplicationId() != null
                && !Objects.equals(applicationPackage.getApplicationId(), attachment.getApplicationId())) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "application attachment conflicts with the package application");
        }
    }

    private JobApplicationAttachment requirePersisted(Long attachmentId, Long userId) {
        JobApplicationAttachment persisted = attachmentMapper.selectById(attachmentId);
        if (persisted == null || CommonConstants.YES.equals(persisted.getDeleted())
                || !Objects.equals(userId, persisted.getUserId())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "application attachment write could not be read back");
        }
        return persisted;
    }

    private InnerFileUploadVO uploadFile(MultipartFile file, Long userId) {
        InnerFileUploadVO uploaded = FeignResultUtils.unwrap(
                fileFeignClient.upload(file, FILE_BIZ_TYPE, userId));
        if (uploaded == null || uploaded.getFileId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "attachment upload returned no file id");
        }
        if (uploaded.getUserId() != null && !Objects.equals(userId, uploaded.getUserId())) {
            deleteFileQuietly(uploaded.getFileId(), userId, "attachment upload owner mismatch");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "attachment upload owner mismatch");
        }
        return uploaded;
    }

    private void applyUpload(
            JobApplicationAttachment attachment,
            InnerFileUploadVO uploaded,
            String normalizedAttachmentType,
            String normalizedDisplayName) {
        attachment.setFileId(uploaded.getFileId());
        attachment.setAttachmentType(normalizedAttachmentType);
        attachment.setOriginalFilename(uploaded.getOriginalFilename());
        attachment.setDisplayName(normalizedDisplayName);
        attachment.setMimeType(uploaded.getMimeType());
        attachment.setFileSize(uploaded.getFileSize());
    }

    private int nextSortOrder(Long packageId, Long applicationId, Long userId) {
        LambdaQueryWrapper<JobApplicationAttachment> query =
                new LambdaQueryWrapper<JobApplicationAttachment>()
                        .eq(JobApplicationAttachment::getUserId, userId)
                        .eq(JobApplicationAttachment::getDeleted, CommonConstants.NO);
        if (packageId != null) {
            query.eq(JobApplicationAttachment::getPackageId, packageId);
        } else {
            query.eq(JobApplicationAttachment::getApplicationId, applicationId);
        }
        JobApplicationAttachment latest = attachmentMapper.selectOne(query
                .orderByDesc(JobApplicationAttachment::getSortOrder)
                .orderByDesc(JobApplicationAttachment::getId)
                .last("limit 1"));
        return latest == null || latest.getSortOrder() == null ? 0 : latest.getSortOrder() + 1;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "attachment file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "attachment file exceeds 20 MB");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "attachment filename is required");
        }
    }

    private String normalizeAttachmentType(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_ATTACHMENT_TYPE;
        if (!ATTACHMENT_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "unsupported attachment type");
        }
        return normalized;
    }

    private String normalizeDisplayName(String value, String originalFilename) {
        String normalized = firstText(value, originalFilename, "附件");
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "attachment display name is too long");
        }
        return normalized;
    }

    private JobApplicationAttachmentVO toVO(JobApplicationAttachment attachment) {
        JobApplicationAttachmentVO vo = new JobApplicationAttachmentVO();
        vo.setId(attachment.getId());
        vo.setPackageId(attachment.getPackageId());
        vo.setApplicationId(attachment.getApplicationId());
        vo.setFileId(attachment.getFileId());
        vo.setAttachmentType(attachment.getAttachmentType());
        vo.setDisplayName(attachment.getDisplayName());
        vo.setOriginalFilename(attachment.getOriginalFilename());
        vo.setMimeType(attachment.getMimeType());
        vo.setFileSize(attachment.getFileSize());
        vo.setSortOrder(attachment.getSortOrder());
        if (attachment.getApplicationId() != null) {
            vo.setDownloadUrl("/applications/" + attachment.getApplicationId()
                    + "/attachments/" + attachment.getId() + "/download");
        } else {
            vo.setDownloadUrl("/application-packages/" + attachment.getPackageId()
                    + "/attachments/" + attachment.getId() + "/download");
        }
        vo.setCreatedAt(attachment.getCreatedAt());
        vo.setUpdatedAt(attachment.getUpdatedAt());
        return vo;
    }

    private JobApplicationAttachmentVO replaceAttachment(
            JobApplicationAttachment attachment,
            MultipartFile file,
            String attachmentType,
            String displayName,
            Long userId) {
        validateFile(file);
        String normalizedType = normalizeAttachmentType(attachmentType);
        String normalizedDisplayName = normalizeDisplayName(displayName, file.getOriginalFilename());
        InnerFileUploadVO uploaded = uploadFile(file, userId);
        boolean rollbackCleanupRegistered = deleteFileAfterRollback(
                uploaded.getFileId(), userId, "attachment replacement rolled back");
        try {
            Long previousFileId = attachment.getFileId();
            applyUpload(attachment, uploaded, normalizedType, normalizedDisplayName);
            if (attachmentMapper.updateById(attachment) != 1) {
                throw new BusinessException(
                        ErrorCode.RESOURCE_RELATION_CONFLICT,
                        "application attachment changed while it was being replaced");
            }
            deleteFileAfterCommit(previousFileId, userId, "attachment replaced");
            return toVO(requirePersisted(attachment.getId(), userId));
        } catch (RuntimeException ex) {
            if (!rollbackCleanupRegistered) {
                deleteFileQuietly(uploaded.getFileId(), userId, "attachment replacement update failed");
            }
            throw ex;
        }
    }

    private void deleteAttachment(JobApplicationAttachment attachment, Long userId) {
        attachment.setDeleted(CommonConstants.YES);
        if (attachmentMapper.updateById(attachment) != 1) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_RELATION_CONFLICT,
                    "application attachment changed while it was being deleted");
        }
        deleteFileAfterCommit(attachment.getFileId(), userId, "attachment deleted");
    }

    private ResponseEntity<Resource> downloadFile(
            JobApplicationAttachment attachment, Long userId) {
        ResponseEntity<Resource> response =
                fileFeignClient.download(attachment.getFileId(), userId, FILE_BIZ_TYPE);
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "attachment download returned no file");
        }
        return response;
    }

    private void deleteFileAfterCommit(Long fileId, Long userId, String reason) {
        if (fileId == null) {
            return;
        }
        Runnable action = () -> deleteFileQuietly(fileId, userId, reason);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private boolean deleteFileAfterRollback(Long fileId, Long userId, String reason) {
        if (fileId == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteFileQuietly(fileId, userId, reason);
                }
            }
        });
        return true;
    }

    private void deleteFileQuietly(Long fileId, Long userId, String reason) {
        if (fileId == null) {
            return;
        }
        try {
            FeignResultUtils.unwrap(fileFeignClient.delete(fileId, userId, FILE_BIZ_TYPE));
        } catch (RuntimeException ex) {
            log.warn("Application attachment file cleanup failed fileId={} userId={} reason={}",
                    fileId, userId, reason, ex);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
