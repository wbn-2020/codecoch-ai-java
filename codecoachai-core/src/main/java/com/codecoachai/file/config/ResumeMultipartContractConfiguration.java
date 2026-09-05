package com.codecoachai.file.config;

import com.codecoachai.resume.config.ResumeTextExtractProperties;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration(proxyBeanMethods = false)
public class ResumeMultipartContractConfiguration {

    private static final long REQUEST_OVERHEAD_BYTES = 1024L * 1024L;

    @Bean
    @ConditionalOnMissingBean(MultipartConfigElement.class)
    MultipartConfigElement multipartConfigElement(
            MultipartProperties multipartProperties,
            ResumeTextExtractProperties resumeProperties) {
        long resumeMaxBytes = resumeProperties.maxSourceFileBytes();
        long maxFileBytes = Math.max(multipartProperties.getMaxFileSize().toBytes(), resumeMaxBytes);
        long maxRequestBytes = Math.max(
                multipartProperties.getMaxRequestSize().toBytes(),
                Math.addExact(resumeMaxBytes, REQUEST_OVERHEAD_BYTES));

        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(maxFileBytes));
        factory.setMaxRequestSize(DataSize.ofBytes(maxRequestBytes));
        factory.setFileSizeThreshold(multipartProperties.getFileSizeThreshold());
        if (multipartProperties.getLocation() != null
                && !multipartProperties.getLocation().isBlank()) {
            factory.setLocation(multipartProperties.getLocation());
        }
        return factory.createMultipartConfig();
    }
}
