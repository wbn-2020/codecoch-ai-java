package com.codecoachai.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Elasticsearch 客户端配置。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "codecoachai.elasticsearch")
public class ElasticsearchConfig {

    /** ES URI，逗号分隔多节点，例如 http://localhost:9200 */
    private String uris = "http://localhost:9200";

    /** 用户名（可空） */
    private String username = "";

    /** 密码（可空） */
    private String password = "";

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        HttpHost[] hosts = parseHosts(uris);
        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(hosts);

        if (StringUtils.hasText(username)) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credsProvider));
        }

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        log.info("Elasticsearch 客户端初始化完成 uris={}", uris);
        return new ElasticsearchClient(transport);
    }

    private HttpHost[] parseHosts(String uris) {
        String[] parts = uris.split(",");
        HttpHost[] hosts = new HttpHost[parts.length];
        for (int i = 0; i < parts.length; i++) {
            hosts[i] = HttpHost.create(parts[i].trim());
        }
        return hosts;
    }
}
