package com.codecoachai.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.config.OssProperties;
import com.codecoachai.common.oss.domain.OssUploadResult;
import com.codecoachai.common.oss.service.OssFileService;
import com.codecoachai.file.config.FileStorageProperties;
import com.codecoachai.file.domain.dto.AdminFileQueryDTO;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.domain.vo.FileInfoVO;
import com.codecoachai.file.domain.vo.FileResumeAnalysisStatusVO;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.mapper.FileInfoMapper;
import com.codecoachai.file.service.FileStorageService;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云 OSS 实现 FileStorageService。
 * 仅在 codecoachai.file.storage.provider=ALIYUN_OSS 时启用，与 LocalFileStorageServiceImpl 互斥。
 *
 * 下载路径：使用 OSS 私有签名 URL 重定向（生产推荐）；当前 download() 简化为读取字节流再返回。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "codecoachai.file.storage",
        name = "provider",
        havingValue = "ALIYUN_OSS")
public class AliyunOssFileStorageServiceImpl implements FileStorageService {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSE_STATUS_FAILED = "FAILED";
    private static final String PARSE_STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";
    private static final String PROVIDER_OSS = "ALIYUN_OSS";
    private static final int NOT_DELETED = 0;
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    private final FileInfoMapper fileInfoMapper;
    private final FileStorageProperties properties;
    private final OssFileService ossFileService;
    private final OssProperties ossProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InnerFileUploadVO upload(MultipartFile file, String bizType, Long userId) {
        validateBasic(file, bizType, userId);
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String fileExt = extractExtension(originalFilename);
        validateExtension(fileExt);
        validateSize(file);

        // OSS Key：{bizType}/{userId}/yyyy/MM/{uuid}.{ext}
        String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + fileExt;
        String ossKey = bizType.toLowerCase(Locale.ROOT) + "/"
                + userId + "/"
                + LocalDate.now().format(DATE_PATH_FORMATTER) + "/"
                + storedFilename;

        try {
            byte[] bytes = file.getBytes();
            String md5 = md5Hex(bytes);

            OssUploadResult uploaded = ossFileService.upload(ossKey, bytes,
                    StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream");

            FileInfo fileInfo = new FileInfo();
            fileInfo.setUserId(userId);
            fileInfo.setBizType(bizType);
            fileInfo.setOriginalFilename(originalFilename);
            fileInfo.setStoredFilename(storedFilename);
            fileInfo.setFileExt(fileExt);
            fileInfo.setMimeType(file.getContentType());
            fileInfo.setFileSize((long) bytes.length);
            fileInfo.setStoragePath(uploaded.getOssKey());   // 兼容老字段
            fileInfo.setOssKey(uploaded.getOssKey());
            fileInfo.setBucket(ossProperties.getBucket());
            fileInfo.setEtag(uploaded.getEtag());
            fileInfo.setMd5(md5);
            fileInfo.setStorageProvider(PROVIDER_OSS);
            fileInfo.setStatus(STATUS_AVAILABLE);
            fileInfoMapper.insert(fileInfo);

            log.info("OSS 上传成功 fileId={} ossKey={} size={}", fileInfo.getId(), ossKey, bytes.length);
            return toVO(fileInfo);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "File read failed");
        } catch (BusinessException be) {
            // OSS 上传失败 → 透传
            throw be;
        } catch (Exception ex) {
            log.error("OSS 上传失败", ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "File upload failed");
        }
    }

    @Override
    public ResponseEntity<byte[]> download(Long fileId, Long userId, String bizType) {
        FileInfo fileInfo = getAvailableFile(fileId, userId, bizType);
        String key = StringUtils.hasText(fileInfo.getOssKey()) ? fileInfo.getOssKey() : fileInfo.getStoragePath();
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "oss key is empty");
        }

        byte[] bytes = ossFileService.download(key);
        MediaType mediaType = resolveMediaType(fileInfo.getMimeType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileInfo.getOriginalFilename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(bytes);
    }

    /**
     * 推荐方式：返回签名 URL 让前端直接访问 OSS。本接口被 InnerFileController 调用时可选。
     */
    public String signUrl(Long fileId, Long userId, String bizType, Duration expire) {
        FileInfo fileInfo = getAvailableFile(fileId, userId, bizType);
        String key = StringUtils.hasText(fileInfo.getOssKey()) ? fileInfo.getOssKey() : fileInfo.getStoragePath();
        return ossFileService.signUrl(key, expire);
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
        List<FileInfoVO> records = page.getRecords().stream().map(this::toFileInfoVO).toList();
        fillResumeAnalysisStatus(records);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FileInfoVO getAdminFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file not found");
        }
        FileInfoVO vo = toFileInfoVO(fileInfo);
        try {
            fillResumeAnalysisStatus(vo, fileInfoMapper.selectLatestResumeAnalysisByFileId(fileId));
        } catch (RuntimeException ex) {
            log.warn("Failed to fill resume analysis status for fileId={}", fileId, ex);
        }
        return vo;
    }

    // ============== 工具方法 ==============

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
                    .collect(Collectors.toMap(FileResumeAnalysisStatusVO::getFileId, Function.identity(),
                            (left, right) -> left));
            for (FileInfoVO record : records) {
                fillResumeAnalysisStatus(record, latestRecordMap.get(record.getId()));
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to fill resume analysis status for fileIds={}", resumeFileIds, ex);
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

    private long defaultPage(Long pageNo) {
        return pageNo == null ? 1L : pageNo;
    }

    private long defaultSize(Long pageSize) {
        return pageSize == null ? 10L : pageSize;
    }

    private String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception ex) {
            return "";
        }
    }
}
