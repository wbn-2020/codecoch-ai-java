package com.codecoachai.file.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.mapper.FileInfoMapper;
import com.codecoachai.file.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String PROVIDER_LOCAL = "LOCAL";
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
