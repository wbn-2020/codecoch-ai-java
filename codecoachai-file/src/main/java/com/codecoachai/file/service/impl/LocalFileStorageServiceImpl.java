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
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.mapper.FileInfoMapper;
import com.codecoachai.file.service.FileStorageService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
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
        validateBasic(file, bizType, userId);
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String fileExt = extractExtension(originalFilename);
        validateExtension(fileExt);
        validateSize(file);

        Path root = normalizeRoot();
        String storedFilename = UUID.randomUUID() + "." + fileExt;
        String relativePath = Path.of("resume", LocalDate.now().format(DATE_PATH_FORMATTER), storedFilename)
                .toString()
                .replace('\\', '/');
        Path target = root.resolve(relativePath).normalize();
        ensureInsideRoot(root, target);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            FileInfo fileInfo = buildFileInfo(file, bizType, userId, originalFilename, storedFilename, fileExt,
                    relativePath);
            fileInfoMapper.insert(fileInfo);
            return toVO(fileInfo);
        } catch (Exception ex) {
            deleteQuietly(target);
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "File upload failed");
        }
    }

    @Override
    public ResponseEntity<byte[]> download(Long fileId, Long userId, String bizType) {
        FileInfo fileInfo = getAvailableFile(fileId, userId, bizType);
        if (!StringUtils.hasText(fileInfo.getStoragePath())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "storage path is empty");
        }

        Path root = normalizeRoot();
        Path target = root.resolve(fileInfo.getStoragePath()).normalize();
        ensureInsideRoot(root, target);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file not found");
        }

        try {
            byte[] bytes = Files.readAllBytes(target);
            MediaType mediaType = resolveMediaType(fileInfo.getMimeType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(bytes.length)
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
                    .body(bytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "File read failed");
        }
    }

    @Override
    public PageResult<FileInfoVO> pageAdminFiles(AdminFileQueryDTO query) {
        AdminFileQueryDTO actualQuery = query == null ? new AdminFileQueryDTO() : query;
        Page<FileInfo> page = fileInfoMapper.selectPage(
                Page.of(defaultPage(actualQuery.getPageNo()), defaultSize(actualQuery.getPageSize())),
                new LambdaQueryWrapper<FileInfo>()
                        .eq(actualQuery.getUserId() != null, FileInfo::getUserId, actualQuery.getUserId())
                        .eq(StringUtils.hasText(actualQuery.getBizType()), FileInfo::getBizType, actualQuery.getBizType())
                        .eq(StringUtils.hasText(actualQuery.getStatus()), FileInfo::getStatus, actualQuery.getStatus())
                        .orderByDesc(FileInfo::getCreatedAt));
        return PageResult.of(page.getRecords().stream().map(this::toFileInfoVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FileInfoVO getAdminFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file not found");
        }
        return toFileInfoVO(fileInfo);
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
            throw new BusinessException(ErrorCode.PARAM_ERROR, "fileId is required");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        if (!StringUtils.hasText(bizType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "bizType is required");
        }
        FileInfo fileInfo = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getBizType, bizType)
                .eq(FileInfo::getStatus, STATUS_AVAILABLE)
                .eq(FileInfo::getDeleted, NOT_DELETED)
                .last("limit 1"));
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file not found");
        }
        return fileInfo;
    }

    private void validateBasic(MultipartFile file, String bizType, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        if (!StringUtils.hasText(bizType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "bizType is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file is empty");
        }
    }

    private void validateSize(MultipartFile file) {
        long maxBytes = properties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file size exceeds limit");
        }
    }

    private String safeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "filename is required");
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(filename) || filename.contains("..")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid filename");
        }
        return filename;
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file extension is required");
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String fileExt) {
        boolean allowed = properties.getAllowedExtensions().stream()
                .filter(StringUtils::hasText)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(fileExt::equals);
        if (!allowed) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file type not allowed");
        }
    }

    private Path normalizeRoot() {
        Path root = Path.of(properties.getRootPath()).toAbsolutePath().normalize();
        if (!StringUtils.hasText(root.toString())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "storage root is required");
        }
        return root;
    }

    private void ensureInsideRoot(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid storage path");
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

    private long defaultPage(Long pageNo) {
        return pageNo == null ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null ? 10L : pageSize;
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
}
