package com.codecoachai.common.oss.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置。
 * 对应 Nacos 配置：codecoachai.oss.*
 */
@Data
@ConfigurationProperties(prefix = "codecoachai.oss")
public class OssProperties {

    /**
     * 是否启用 OSS。false 时回退到本地存储（兼容期）。
     */
    private Boolean enabled = false;

    /**
     * OSS Endpoint，例如：oss-cn-hangzhou.aliyuncs.com
     */
    private String endpoint = "";

    /**
     * OSS Bucket 名称
     */
    private String bucket = "";

    /**
     * AccessKey ID
     */
    private String accessKeyId = "";

    /**
     * AccessKey Secret
     */
    private String accessKeySecret = "";

    /**
     * 自定义域名（CDN 加速域名），用于生成公开 URL；为空时使用 OSS 默认域名
     */
    private String domain = "";

    /**
     * 签名 URL 的默认有效期（秒），默认 900s
     */
    private Long signUrlExpireSeconds = 900L;

    /**
     * STS 子配置
     */
    private Sts sts = new Sts();

    /**
     * OSS Key 前缀目录（多环境隔离时可设置，例如 dev/, prod/）
     */
    private String keyPrefix = "";

    public Duration signUrlExpire() {
        return Duration.ofSeconds(signUrlExpireSeconds == null || signUrlExpireSeconds <= 0
                ? 900L
                : signUrlExpireSeconds);
    }

    @Data
    public static class Sts {
        /** STS Endpoint，例如 sts.cn-hangzhou.aliyuncs.com */
        private String endpoint = "sts.cn-hangzhou.aliyuncs.com";
        /** 区域 ID */
        private String regionId = "cn-hangzhou";
        /** RAM 角色 ARN：acs:ram::xxx:role/xxx */
        private String roleArn = "";
        /** 会话名称（仅日志识别用） */
        private String roleSessionName = "codecoachai";
        /** 临时凭证有效期，秒（最小 900，最大 3600） */
        private Integer durationSeconds = 3600;
    }
}
