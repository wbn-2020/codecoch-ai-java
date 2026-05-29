package com.codecoachai.common.vector.config;

import com.codecoachai.common.vector.service.NoopVectorStoreClient;
import com.codecoachai.common.vector.service.QdrantVectorStoreClient;
import com.codecoachai.common.vector.service.VectorIndexJobService;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(VectorStoreProperties.class)
public class VectorStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "codecoachai.vector", name = "enabled", havingValue = "true")
    public VectorStoreClient qdrantVectorStoreClient(VectorStoreProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        return new QdrantVectorStoreClient(properties, objectMapper, httpClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorStoreClient noopVectorStoreClient() {
        return new NoopVectorStoreClient();
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public VectorIndexJobService vectorIndexJobService(JdbcTemplate jdbcTemplate) {
        return new VectorIndexJobService(jdbcTemplate);
    }
}
