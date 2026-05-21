package com.codecoachai.common.oss.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.oss.config.OssProperties;
import com.codecoachai.common.oss.domain.StsTokenVO;
import com.codecoachai.common.oss.service.StsTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 阿里云 STS 实现：通过 AssumeRole 获取临时凭证。
 * <p>
 * 注意：策略目前仅限制写到 dirPrefix 下，更细的限制（如文件大小、Content-Type）可在 Policy JSON 中扩展。
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunStsTokenService implements StsTokenService {

    private final OssProperties properties;

    @Override
    public StsTokenVO generate(String dirPrefix) {
        OssProperties.Sts sts = properties.getSts();
        if (sts == null || !StringUtils.hasText(sts.getRoleArn())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "STS 角色未配置");
        }
        try {
            IClientProfile profile = DefaultProfile.getProfile(
                    sts.getRegionId(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret());
            DefaultAcsClient client = new DefaultAcsClient(profile);

            AssumeRoleRequest request = new AssumeRoleRequest();
            request.setSysMethod(MethodType.POST);
            request.setRoleArn(sts.getRoleArn());
            request.setRoleSessionName(sts.getRoleSessionName());
            request.setPolicy(buildPolicy(dirPrefix));
            request.setDurationSeconds(Long.valueOf(sts.getDurationSeconds()));

            AssumeRoleResponse response = client.getAcsResponse(request);
            AssumeRoleResponse.Credentials cred = response.getCredentials();

            return StsTokenVO.builder()
                    .accessKeyId(cred.getAccessKeyId())
                    .accessKeySecret(cred.getAccessKeySecret())
                    .securityToken(cred.getSecurityToken())
                    .expiration(cred.getExpiration())
                    .endpoint(properties.getEndpoint())
                    .bucket(properties.getBucket())
                    .dir(dirPrefix)
                    .region(sts.getRegionId())
                    .build();
        } catch (ClientException ex) {
            log.error("STS 签发失败 errCode={} errMsg={}", ex.getErrCode(), sanitizeErrorMessage(ex.getErrMsg()));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "临时凭证获取失败");
        }
    }

    private String sanitizeErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return message
                .replaceAll("(?i)(AccessKeyId=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(Signature=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(SecurityToken=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(AccessKeySecret=)[^&\\s]+", "$1***");
    }

    /**
     * 构建 RAM Policy：仅允许 PutObject 到 dirPrefix 下。
     */
    private String buildPolicy(String dirPrefix) {
        String prefix = dirPrefix == null ? "" : dirPrefix.replaceAll("^/+", "");
        if (StringUtils.hasText(properties.getKeyPrefix())) {
            String kp = properties.getKeyPrefix().endsWith("/")
                    ? properties.getKeyPrefix()
                    : properties.getKeyPrefix() + "/";
            if (!prefix.startsWith(kp)) {
                prefix = kp + prefix;
            }
        }
        String resourcePath = properties.getBucket() + "/" + prefix + "*";
        return "{\n"
                + "  \"Version\": \"1\",\n"
                + "  \"Statement\": [\n"
                + "    {\n"
                + "      \"Effect\": \"Allow\",\n"
                + "      \"Action\": [\"oss:PutObject\", \"oss:GetObject\"],\n"
                + "      \"Resource\": [\"acs:oss:*:*:" + resourcePath + "\"]\n"
                + "    }\n"
                + "  ]\n"
                + "}";
    }
}
