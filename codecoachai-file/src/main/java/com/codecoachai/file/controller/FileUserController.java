package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.domain.StsTokenVO;
import com.codecoachai.common.oss.service.StsTokenService;
import com.codecoachai.common.oss.util.OssKeyBuilder;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.file.domain.vo.FileInfoVO;
import com.codecoachai.file.domain.vo.InnerFileUploadVO;
import com.codecoachai.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端文件接口：
 *  - 获取 STS 临时凭证（用于前端直传 OSS）
 *  - 获取私有文件签名 URL
 *
 * 仅当 codecoachai.oss.enabled=true 时，StsTokenService Bean 存在；否则 STS 接口返回错误。
 */
@Tag(name = "文件-用户端")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileUserController {

    /** ObjectProvider 允许 Bean 缺席（OSS 未启用时）不报错 */
    private final ObjectProvider<StsTokenService> stsTokenServiceProvider;
    private final FileStorageService fileStorageService;

    @Operation(summary = "上传文件", description = "兼容文档中的 /files/upload；真实 OSS 场景仍推荐 STS 直传")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<InnerFileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(defaultValue = "RESUME") String bizType) {
        return Result.success(fileStorageService.upload(file, normalizeBizType(bizType), requireUserId()));
    }

    @Operation(summary = "获取 OSS 直传临时凭证",
            description = "返回当前登录用户在指定业务下的 STS 凭证，前端使用 ali-oss SDK 直传")
    @GetMapping("/sts-token")
    public Result<StsTokenVO> stsToken(@RequestParam(defaultValue = "resume") String bizType) {
        StsTokenService stsTokenService = stsTokenServiceProvider.getIfAvailable();
        if (stsTokenService == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 未启用，无法签发 STS 凭证");
        }
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        String dir;
        switch (bizType) {
            case "resume":
                dir = OssKeyBuilder.resumeDir(userId);
                break;
            case "avatar":
                dir = "avatar/" + userId + "/";
                break;
            case "attachment":
                dir = "attachment/" + userId + "/";
                break;
            default:
                dir = "tmp/" + userId + "/";
        }
        return Result.success(stsTokenService.generate(dir));
    }

    @Operation(summary = "获取我的文件详情")
    @GetMapping("/{id}")
    public Result<FileInfoVO> detail(@PathVariable Long id) {
        return Result.success(fileStorageService.getUserFile(id, requireUserId()));
    }

    @Operation(summary = "下载我的文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam(required = false) String bizType) {
        return fileStorageService.download(id, requireUserId(), normalizeBizTypeOrNull(bizType));
    }

    @Operation(summary = "获取我的文件下载 URL")
    @GetMapping("/{id}/download-url")
    public Result<Map<String, String>> downloadUrl(@PathVariable Long id,
                                                   @RequestParam(required = false) String bizType) {
        return Result.success(Map.of("url", fileStorageService.downloadUrl(id, requireUserId(), normalizeBizTypeOrNull(bizType))));
    }

    private Long requireUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    private String normalizeBizType(String bizType) {
        String normalized = normalizeBizTypeOrNull(bizType);
        return normalized == null ? "RESUME" : normalized;
    }

    private String normalizeBizTypeOrNull(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return null;
        }
        return bizType.trim().toUpperCase();
    }
}
