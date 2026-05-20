package com.codecoachai.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.codecoachai.search.constant.IndexNames;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * ES 索引管理服务：创建/删除/重建索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexManageService {

    private final ElasticsearchClient esClient;

    private static final List<String> ALL_INDICES = List.of(
            IndexNames.QUESTION,
            IndexNames.RESUME,
            IndexNames.INTERVIEW
    );

    /**
     * 应用启动时确保索引存在（不存在则创建）。
     */
    public void ensureIndices() {
        for (String index : ALL_INDICES) {
            try {
                boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
                if (!exists) {
                    createIndex(index);
                    log.info("ES 索引已创建: {}", index);
                }
            } catch (IOException ex) {
                log.warn("检查/创建索引失败: {}", index, ex);
            }
        }
    }

    /**
     * 重建指定索引（删除 + 重建）。
     */
    public void rebuild(String indexName) throws IOException {
        if (!ALL_INDICES.contains(indexName)) {
            throw new IllegalArgumentException("Unknown index: " + indexName);
        }
        boolean exists = esClient.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (exists) {
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            log.info("ES 索引已删除: {}", indexName);
        }
        createIndex(indexName);
        log.info("ES 索引已重建: {}", indexName);
    }

    /**
     * 重建所有索引。
     */
    public void rebuildAll() throws IOException {
        for (String index : ALL_INDICES) {
            rebuild(index);
        }
    }

    private void createIndex(String indexName) throws IOException {
        String mappingJson = loadMapping(indexName);
        esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(indexName)
                .withJson(new StringReader(mappingJson))
        ));
    }

    private String loadMapping(String indexName) throws IOException {
        String path = "es-mappings/" + indexName + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
