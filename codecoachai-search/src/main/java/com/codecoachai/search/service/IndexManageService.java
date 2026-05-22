package com.codecoachai.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.codecoachai.search.constant.IndexNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper objectMapper;

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
        try {
            createIndexWithMapping(indexName, mappingJson);
        } catch (RuntimeException ex) {
            if (!isAnalyzerMissing(ex)) {
                throw ex;
            }
            log.warn("ES analyzer unavailable for {}, fallback to standard analyzer: {}", indexName, ex.getMessage());
            createIndexWithMapping(indexName, standardAnalyzerFallback(mappingJson));
        }
    }

    private void createIndexWithMapping(String indexName, String mappingJson) throws IOException {
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

    private boolean isAnalyzerMissing(RuntimeException ex) {
        String message = ex.getMessage();
        Throwable cause = ex.getCause();
        while ((message == null || message.isBlank()) && cause != null) {
            message = cause.getMessage();
            cause = cause.getCause();
        }
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("failed to find tokenizer")
                || lower.contains("ik_smart")
                || lower.contains("ik_max_word")
                || lower.contains("ik_smart_pinyin");
    }

    private String standardAnalyzerFallback(String mappingJson) throws IOException {
        JsonNode root = objectMapper.readTree(mappingJson);
        JsonNode settings = root.path("settings");
        if (settings instanceof ObjectNode settingsNode && settingsNode.has("analysis")) {
            settingsNode.remove("analysis");
        }
        replaceIkAnalyzer(root);
        return objectMapper.writeValueAsString(root);
    }

    private void replaceIkAnalyzer(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode analyzer = objectNode.get("analyzer");
            if (analyzer != null && analyzer.isTextual() && isIkAnalyzer(analyzer.asText())) {
                objectNode.put("analyzer", "standard");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                replaceIkAnalyzer(fields.next().getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                replaceIkAnalyzer(child);
            }
        }
    }

    private boolean isIkAnalyzer(String analyzer) {
        return "ik_smart".equals(analyzer)
                || "ik_max_word".equals(analyzer)
                || "ik_smart_pinyin".equals(analyzer);
    }
}
