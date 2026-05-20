package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.domain.StsTokenVO;
import com.codecoachai.common.oss.service.StsTokenService;
import com.codecoachai.common.oss.util.OssKeyBuilder;
import com.codecoachai.common.security.context.LoginUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
