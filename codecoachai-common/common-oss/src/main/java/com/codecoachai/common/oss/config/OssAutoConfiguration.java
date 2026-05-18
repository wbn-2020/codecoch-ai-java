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

/**
 * OSS 自动配置。
 * 仅当 codecoachai.oss.enabled=true 时生效。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "codecoachai.oss", name = "enabled", havingValue = "true")
public class OssAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public OSS ossClient(OssProperties properties) {
        log.info("初始化阿里云 OSS 客户端 endpoint={} bucket={}",
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
}
