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
import com.codecoachai.file.util.FileBizTypes;
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

    @Operation(summary = "上传文件", description = "正式上传入口；STS direct upload remains compatibility-only until complete/register validation is enabled")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<InnerFileUploadVO> upload(@RequestPart("file") MultipartFile file,
                                            @RequestParam(defaultValue = "RESUME") String bizType) {
        return Result.success(fileStorageService.upload(file, normalizeBizType(bizType), requireUserId()));
    }

    @Operation(summary = "获取 OSS 直传临时凭证",
            description = "Compatibility entry for direct upload credentials; caller must not treat uploaded objects as registered files")
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
        String normalizedBizType = normalizeBizType(bizType);
        String dir;
        switch (normalizedBizType) {
            case "RESUME":
                dir = OssKeyBuilder.resumeDir(userId);
                break;
            case "AVATAR":
                dir = "avatar/" + userId + "/";
                break;
            case "ATTACHMENT":
                dir = "attachment/" + userId + "/";
                break;
            case "INTERVIEW_VOICE":
                dir = "interview_voice/" + userId + "/";
                break;
            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "file bizType is not supported");
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
        String normalized = FileBizTypes.normalizeOrNull(bizType);
        return FileBizTypes.requireAllowed(normalized == null ? "RESUME" : normalized);
    }

    private String normalizeBizTypeOrNull(String bizType) {
        String normalized = FileBizTypes.normalizeOrNull(bizType);
        return normalized == null ? null : FileBizTypes.requireAllowed(normalized);
    }
}
