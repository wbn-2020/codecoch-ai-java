package com.codecoachai.common.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.codecoachai.common.oss.service.OssFileService;
import com.codecoachai.common.oss.service.StsTokenService;
import com.codecoachai.common.oss.service.impl.AliyunOssFileService;
import com.codecoachai.common.oss.service.impl.AliyunStsTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Aliyun OSS auto configuration. Active only when codecoachai.oss.enabled=true.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "codecoachai.oss", name = "enabled", havingValue = "true")
public class OssAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public OSS ossClient(OssProperties properties) {
        validateOssProperties(properties);
        log.info("Initializing Aliyun OSS client endpoint={} bucket={}",
                properties.getEndpoint(), properties.getBucket());
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public OssFileService ossFileService(OSS ossClient, OssProperties properties) {
        return new AliyunOssFileService(ossClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public StsTokenService stsTokenService(OssProperties properties) {
        return new AliyunStsTokenService(properties);
    }

    private void validateOssProperties(OssProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("codecoachai.oss.endpoint must be configured when OSS is enabled");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("codecoachai.oss.bucket must be configured when OSS is enabled");
        }
        if (!StringUtils.hasText(properties.getAccessKeyId())) {
            throw new IllegalStateException("codecoachai.oss.access-key-id must be configured when OSS is enabled");
        }
        if (!StringUtils.hasText(properties.getAccessKeySecret())) {
            throw new IllegalStateException("codecoachai.oss.access-key-secret must be configured when OSS is enabled");
        }
    }
}
