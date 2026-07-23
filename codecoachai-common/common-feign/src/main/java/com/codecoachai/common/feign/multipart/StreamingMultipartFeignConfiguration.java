package com.codecoachai.common.feign.multipart;

import feign.Capability;
import feign.codec.Encoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;

/**
 * Client-specific Feign configuration for bounded streaming multipart uploads.
 *
 * <p>This class intentionally has no {@code @Configuration} annotation so that
 * applications scanning {@code com.codecoachai} do not install it globally.
 */
public class StreamingMultipartFeignConfiguration {

    @Bean
    public Encoder streamingMultipartEncoder(
            ObjectFactory<HttpMessageConverters> messageConverters,
            @Value("${codecoachai.feign.multipart.max-file-bytes:10485760}") long maxFileBytes) {
        return new StreamingMultipartEncoder(new SpringEncoder(messageConverters), maxFileBytes);
    }

    @Bean
    public Capability streamingMultipartCapability(
            ObjectProvider<LoadBalancerClient> loadBalancerClientProvider) {
        return new StreamingMultipartCapability(loadBalancerClientProvider.getIfAvailable());
    }
}
