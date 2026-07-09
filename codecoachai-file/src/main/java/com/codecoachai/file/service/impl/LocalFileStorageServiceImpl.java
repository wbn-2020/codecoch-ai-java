package com.codecoachai.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.domain.vo.FileInfoVO;
import com.codecoachai.file.domain.vo.FileResumeAnalysisStatusVO;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.mapper.FileInfoMapper;
import com.codecoachai.file.service.FileStorageService;
import com.codecoachai.file.util.FileBizTypes;
import com.codecoachai.file.util.FileUploadValidator;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "codecoachai.file.storage",
        name = "provider",
        havingValue = "LOCAL",
        matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSE_STATUS_FAILED = "FAILED";
    private static final String PARSE_STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";
    private static final String PROVIDER_LOCAL = "LOCAL";
    private static final int NOT_DELETED = 0;
    private static final String HEADER_ORIGINAL_FILENAME = "X-Original-Filename";
    private static final String HEADER_FILE_EXT = "X-File-Ext";
    private static final String HEADER_FILE_SIZE = "X-File-Size";
    private static final String HEADER_MIME_TYPE = "X-Mime-Type";
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileInfoMapper fileInfoMapper;
    private final FileStorageProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InnerFileUploadVO upload(MultipartFile file, String bizType, Long userId) {
        String normalizedBizType = FileBizTypes.requireAllowed(bizType);
        validateBasic(file, normalizedBizType, userId);
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String fileExt = extractExtension(originalFilename);
        validateExtension(normalizedBizType, fileExt);
        validateSize(file);
        FileUploadValidator.validateContent(file, fileExt);

        Path root = normalizeRoot();
        String storedFilename = UUID.randomUUID() + "." + fileExt;
        String relativePath = Path.of(FileBizTypes.directoryName(normalizedBizType),
                        LocalDate.now().format(DATE_PATH_FORMATTER), storedFilename)
                .toString()
                .replace('\\', '/');
        Path target = root.resolve(relativePath).normalize();
        ensureInsideRoot(root, target);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            registerRollbackCleanup(target);
            FileInfo fileInfo = buildFileInfo(file, normalizedBizType, userId, originalFilename, storedFilename, fileExt,
                    relativePath);
            fileInfoMapper.insert(fileInfo);
            return toVO(fileInfo);
        } catch (Exception ex) {
            deleteQuietly(target);
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserFile(Long fileId, Long userId, String bizType) {
        FileInfo fileInfo = getAvailableFile(fileId, userId, bizType);
        fileInfoMapper.deleteById(fileInfo.getId());
        deleteQuietly(resolveStoragePath(fileInfo.getStoragePath()));
        log.info("Local file compensation deleted fileId={} userId={} bizType={}",
                fileId, userId, fileInfo.getBizType());
    }

    @Override
    public ResponseEntity<Resource> download(Long fileId, Long userId, String bizType) {
        FileInfo fileInfo = getAvailableFile(fileId, userId, bizType);
        return downloadResource(fileInfo);
    }

    @Override
    public String downloadUrl(Long fileId, Long userId, String bizType) {
        FileInfo fileInfo = getAvailableFile(fileId, userId, bizType);
        StringBuilder url = new StringBuilder("/files/")
                .append(fileInfo.getId())
                .append("/download");
        if (StringUtils.hasText(fileInfo.getBizType())) {
            url.append("?bizType=").append(encodeHeaderValue(fileInfo.getBizType()));
        }
        return url.toString();
    }

    @Override
    public FileInfoVO getUserFile(Long fileId, Long userId) {
        return toFileInfoVO(getAvailableFile(fileId, userId, null));
    }

    @Override
    public ResponseEntity<Resource> adminDownload(Long fileId) {
        FileInfo fileInfo = getAvailableAdminFile(fileId);
        return downloadResource(fileInfo);
    }

    @Override
    public PageResult<FileInfoVO> pageAdminFiles(AdminFileQueryDTO query) {
        AdminFileQueryDTO actualQuery = query == null ? new AdminFileQueryDTO() : query;
        List<Long> parseStatusFileIds = resolveParseStatusFileIds(actualQuery);
        if (parseStatusFileIds != null && parseStatusFileIds.isEmpty()) {
            return PageResult.empty(actualQuery.effectivePageNo(), actualQuery.effectivePageSize());
        }
        Page<FileInfo> page = fileInfoMapper.selectPage(
                Page.of(actualQuery.effectivePageNo(), actualQuery.effectivePageSize()),
                new LambdaQueryWrapper<FileInfo>()
                        .eq(actualQuery.getUserId() != null, FileInfo::getUserId, actualQuery.getUserId())
                        .eq(StringUtils.hasText(actualQuery.getBizType()), FileInfo::getBizType, actualQuery.getBizType())
                        .eq(StringUtils.hasText(actualQuery.getStatus()), FileInfo::getStatus, actualQuery.getStatus())
                        .in(parseStatusFileIds != null, FileInfo::getId, parseStatusFileIds)
                        .orderByDesc(FileInfo::getCreatedAt));
        List<FileInfoVO> records = page.getRecords().stream().map(this::toFileInfoVO).toList();
        fillResumeAnalysisStatus(records);
        return PageResult.of(records,
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FileInfoVO getAdminFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不存在或已不可下载");
        }
        FileInfoVO vo = toFileInfoVO(fileInfo);
        try {
            fillResumeAnalysisStatus(vo, fileInfoMapper.selectLatestResumeAnalysisByFileId(fileId));
        } catch (RuntimeException ex) {
            log.warn("Failed to fill resume analysis status for fileId={}", fileId, ex);
        }
        return vo;
    }

    private MediaType resolveMediaType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String encodeHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private FileInfo getAvailableFile(Long fileId, Long userId, String bizType) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少文件编号");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少用户编号");
        }
        String normalizedBizType = FileBizTypes.normalizeOrNull(bizType);
        if (normalizedBizType != null) {
            normalizedBizType = FileBizTypes.requireAllowed(normalizedBizType);
        }
        FileInfo fileInfo = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, userId)
                .eq(StringUtils.hasText(normalizedBizType), FileInfo::getBizType, normalizedBizType)
                .eq(FileInfo::getStatus, STATUS_AVAILABLE)
                .eq(FileInfo::getDeleted, NOT_DELETED)
                .last("limit 1"));
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不存在或已不可下载");
        }
        return fileInfo;
    }

    private FileInfo getAvailableAdminFile(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少文件编号");
        }
        FileInfo fileInfo = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getStatus, STATUS_AVAILABLE)
                .eq(FileInfo::getDeleted, NOT_DELETED)
                .last("limit 1"));
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不存在或已不可下载");
        }
        return fileInfo;
    }

    private void validateBasic(MultipartFile file, String bizType, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少用户编号");
        }
        if (!StringUtils.hasText(bizType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少文件业务类型");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
    }

    private void validateSize(MultipartFile file) {
        long maxBytes = properties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件大小超过限制");
        }
    }

    private String safeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少文件名");
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(filename) || filename.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不合法");
        }
        return filename;
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少文件扩展名");
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String bizType, String fileExt) {
        if (!FileBizTypes.isExtensionAllowed(bizType, fileExt, properties.getAllowedExtensions())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件类型不支持");
        }
    }

    private Path normalizeRoot() {
        Path root = Path.of(properties.getRootPath()).toAbsolutePath().normalize();
        if (!StringUtils.hasText(root.toString())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件存储配置不完整");
        }
        return root;
    }

    private void ensureInsideRoot(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件存储路径不合法");
        }
    }

    private FileInfo buildFileInfo(MultipartFile file, String bizType, Long userId, String originalFilename,
                                   String storedFilename, String fileExt, String storagePath) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUserId(userId);
        fileInfo.setBizType(bizType);
        fileInfo.setOriginalFilename(originalFilename);
        fileInfo.setStoredFilename(storedFilename);
        fileInfo.setFileExt(fileExt);
        fileInfo.setMimeType(file.getContentType());
        fileInfo.setFileSize(file.getSize());
        fileInfo.setStoragePath(storagePath);
        fileInfo.setStorageProvider(StringUtils.hasText(properties.getProvider()) ? properties.getProvider() : PROVIDER_LOCAL);
        fileInfo.setStatus(STATUS_AVAILABLE);
        return fileInfo;
    }

    private InnerFileUploadVO toVO(FileInfo fileInfo) {
        InnerFileUploadVO vo = new InnerFileUploadVO();
        vo.setFileId(fileInfo.getId());
        vo.setUserId(fileInfo.getUserId());
        vo.setBizType(fileInfo.getBizType());
        vo.setOriginalFilename(fileInfo.getOriginalFilename());
        vo.setStoredFilename(fileInfo.getStoredFilename());
        vo.setFileSize(fileInfo.getFileSize());
        vo.setFileExt(fileInfo.getFileExt());
        vo.setMimeType(fileInfo.getMimeType());
        vo.setStoragePath(fileInfo.getStoragePath());
        vo.setStorageProvider(fileInfo.getStorageProvider());
        vo.setStatus(fileInfo.getStatus());
        vo.setCreatedAt(fileInfo.getCreatedAt());
        return vo;
    }

    private FileInfoVO toFileInfoVO(FileInfo fileInfo) {
        FileInfoVO vo = new FileInfoVO();
        vo.setId(fileInfo.getId());
        vo.setUserId(fileInfo.getUserId());
        vo.setBizType(fileInfo.getBizType());
        vo.setBusinessType(fileInfo.getBizType());
        vo.setOriginalFilename(fileInfo.getOriginalFilename());
        vo.setStoredFilename(fileInfo.getStoredFilename());
        vo.setFileExt(fileInfo.getFileExt());
        vo.setMimeType(fileInfo.getMimeType());
        vo.setFileSize(fileInfo.getFileSize());
        vo.setStorageProvider(fileInfo.getStorageProvider());
        vo.setStatus(fileInfo.getStatus());
        vo.setCreatedAt(fileInfo.getCreatedAt());
        vo.setUpdatedAt(fileInfo.getUpdatedAt());
        return vo;
    }

    private void fillResumeAnalysisStatus(List<FileInfoVO> records) {
        List<Long> resumeFileIds = records.stream()
                .filter(item -> item.getId() != null)
                .filter(item -> BIZ_TYPE_RESUME.equals(item.getBizType()))
                .map(FileInfoVO::getId)
                .distinct()
                .toList();
        if (resumeFileIds.isEmpty()) {
            return;
        }
        try {
            Map<Long, FileResumeAnalysisStatusVO> latestRecordMap = fileInfoMapper
                    .selectLatestResumeAnalysisByFileIds(resumeFileIds)
                    .stream()
                    .collect(Collectors.toMap(FileResumeAnalysisStatusVO::getFileId, Function.identity(), (left, right) -> left));
            for (FileInfoVO record : records) {
                fillResumeAnalysisStatus(record, latestRecordMap.get(record.getId()));
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to fill resume analysis status for fileIds={}", resumeFileIds, ex);
        }
    }

    private ResponseEntity<Resource> downloadResource(FileInfo fileInfo) {
        if (!StringUtils.hasText(fileInfo.getStoragePath())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File storage path is missing.");
        }

        Path target = resolveStoragePath(fileInfo.getStoragePath());
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "File is not available for download.");
        }

        try {
            return ResponseEntity.ok()
                    .contentType(resolveMediaType(fileInfo.getMimeType()))
                    .contentLength(Files.size(target))
                    .header(HEADER_ORIGINAL_FILENAME, encodeHeaderValue(fileInfo.getOriginalFilename()))
                    .header(HEADER_FILE_EXT, fileInfo.getFileExt())
                    .header(HEADER_FILE_SIZE, String.valueOf(fileInfo.getFileSize()))
                    .header(HEADER_MIME_TYPE, StringUtils.hasText(fileInfo.getMimeType())
                            ? fileInfo.getMimeType()
                            : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(new FileSystemResource(target));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "File read failed, please retry later.");
        }
    }

    private Path resolveStoragePath(String storagePath) {
        Path root = normalizeRoot();
        Path target = root.resolve(storagePath).normalize();
        ensureInsideRoot(root, target);
        return target;
    }

    private List<Long> resolveParseStatusFileIds(AdminFileQueryDTO query) {
        if (query == null || !StringUtils.hasText(query.getParseStatus())) {
            return null;
        }
        String parseStatus = query.getParseStatus().trim().toUpperCase(Locale.ROOT);
        try {
            return fileInfoMapper.selectLatestResumeFileIdsByParseStatus(parseStatus);
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve resume file ids by parseStatus={}", parseStatus, ex);
            return List.of();
        }
    }

    private void fillResumeAnalysisStatus(FileInfoVO vo, FileResumeAnalysisStatusVO analysisStatus) {
        if (vo == null || analysisStatus == null) {
            return;
        }
        vo.setResumeId(analysisStatus.getResumeId());
        vo.setBusinessId(analysisStatus.getResumeId());
        vo.setResumeAnalysisRecordId(analysisStatus.getResumeAnalysisRecordId());
        vo.setParseStatus(analysisStatus.getParseStatus());
        vo.setParseErrorMessage(analysisStatus.getParseErrorMessage());
        vo.setAnalysisConfirmed(PARSE_STATUS_SUCCESS.equals(analysisStatus.getParseStatus()));
        if (isTerminalParseStatus(analysisStatus.getParseStatus())) {
            vo.setParsedAt(analysisStatus.getUpdatedAt());
        }
        if (PARSE_STATUS_SUCCESS.equals(analysisStatus.getParseStatus())) {
            vo.setConfirmedAt(analysisStatus.getUpdatedAt());
        }
    }

    private boolean isTerminalParseStatus(String parseStatus) {
        return PARSE_STATUS_SUCCESS.equals(parseStatus)
                || PARSE_STATUS_FAILED.equals(parseStatus)
                || PARSE_STATUS_WAIT_CONFIRM.equals(parseStatus);
    }

    private void deleteQuietly(Path target) {
        try {
            if (target != null) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup after metadata persistence failure.
        }
    }

    private void registerRollbackCleanup(Path target) {
        if (target == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        AtomicBoolean cleaned = new AtomicBoolean(false);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED && cleaned.compareAndSet(false, true)) {
                    deleteQuietly(target);
                }
            }
        });
    }
}
