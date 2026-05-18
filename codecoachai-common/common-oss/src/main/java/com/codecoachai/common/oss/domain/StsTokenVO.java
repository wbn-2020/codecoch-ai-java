package com.codecoachai.common.oss.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * STS 临时凭证（用于前端直传 OSS）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StsTokenVO {

    /** 临时 AccessKey ID */
    private String accessKeyId;

    /** 临时 AccessKey Secret */
    private String accessKeySecret;

    /** 安全令牌 */
    private String securityToken;

    /** 过期时间（ISO8601 字符串） */
    private String expiration;

    /** OSS Endpoint */
    private String endpoint;

    /** OSS Bucket 名称 */
    private String bucket;

    /** 该次签发允许写入的目录前缀（前端必须把文件上传到此目录下） */
    private String dir;

    /** 区域 ID */
    private String region;
}
