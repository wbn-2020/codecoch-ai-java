package com.codecoachai.common.vector.service;

import com.codecoachai.common.vector.config.VectorStoreProperties;
import com.codecoachai.common.vector.domain.VectorCollectionInfo;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.exception.VectorStoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class QdrantVectorStoreClient implements VectorStoreClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final VectorStoreProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QdrantVectorStoreClient(VectorStoreProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled() && "qdrant".equalsIgnoreCase(properties.getProvider());
    }

    @Override
    public void ensureCollection(String collectionName, int dimension) {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", dimension);
        vectors.put("distance", "Cosine");
        send("PUT", "/collections/" + collectionName, Map.of("vectors", vectors));
    }

    @Override
    public VectorCollectionInfo collectionInfo(String collectionName) {
        if (!isEnabled()) {
            return VectorCollectionInfo.builder()
                    .collectionName(collectionName)
                    .exists(false)
                    .status("DISABLED")
                    .build();
        }
        try {
            JsonNode result = send("GET", "/collections/" + collectionName, null).path("result");
            JsonNode vectors = result.path("config").path("params").path("vectors");
            return VectorCollectionInfo.builder()
                    .collectionName(collectionName)
                    .exists(true)
                    .status(result.path("status").asText("UNKNOWN"))
                    .pointCount(result.path("points_count").isMissingNode() ? null : result.path("points_count").asLong())
                    .vectorSize(vectors.path("size").isMissingNode() ? null : vectors.path("size").asInt())
                    .distance(vectors.path("distance").asText(null))
                    .build();
        } catch (Exception ex) {
            return VectorCollectionInfo.builder()
                    .collectionName(collectionName)
                    .exists(false)
                    .status("ERROR")
                    .errorMessage(ex.getMessage())
                    .build();
        }
    }

    @Override
    public void upsert(String collectionName, List<VectorPoint> points) {
        if (CollectionUtils.isEmpty(points)) {
            return;
        }
        List<Map<String, Object>> payloadPoints = points.stream().map(point -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", point.getId());
            item.put("vector", point.getVector());
            item.put("payload", point.getPayload() == null ? Map.of() : point.getPayload());
            return item;
        }).toList();
        send("PUT", "/collections/" + collectionName + "/points?wait=true", Map.of("points", payloadPoints));
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", request.getVector());
        body.put("limit", request.getLimit() == null ? properties.getDefaultLimit() : request.getLimit());
        body.put("with_payload", true);
        if (request.getScoreThreshold() != null) {
            body.put("score_threshold", request.getScoreThreshold());
        }
        Map<String, Object> filter = buildFilter(request.getMustMatchPayload());
        if (!filter.isEmpty()) {
            body.put("filter", filter);
        }
        JsonNode root = send("POST", "/collections/" + request.getCollectionName() + "/points/search", body);
        List<VectorSearchResult> results = new ArrayList<>();
        for (JsonNode item : root.path("result")) {
            results.add(VectorSearchResult.builder()
                    .id(item.path("id").asText())
                    .score(item.path("score").asDouble())
                    .payload(objectMapper.convertValue(item.path("payload"), MAP_TYPE))
                    .build());
        }
        return results;
    }

    @Override
    public void delete(String collectionName, List<String> pointIds) {
        if (CollectionUtils.isEmpty(pointIds)) {
            return;
        }
        send("POST", "/collections/" + collectionName + "/points/delete?wait=true", Map.of("points", pointIds));
    }

    private Map<String, Object> buildFilter(Map<String, Object> mustMatchPayload) {
        if (CollectionUtils.isEmpty(mustMatchPayload)) {
            return Map.of();
        }
        List<Map<String, Object>> must = new ArrayList<>();
        for (Map.Entry<String, Object> entry : mustMatchPayload.entrySet()) {
            if (entry.getValue() != null) {
                must.add(Map.of("key", entry.getKey(), "match", Map.of("value", entry.getValue())));
            }
        }
        return must.isEmpty() ? Map.of() : Map.of("must", must);
    }

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(properties.getBaseUrl()) + path))
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "application/json");
            if (StringUtils.hasText(properties.getApiKey())) {
                builder.header("api-key", properties.getApiKey());
            }
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
            HttpRequest request = builder.method(method, publisher).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new VectorStoreException("Qdrant request failed status=" + response.statusCode()
                        + " path=" + path + " body=" + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (VectorStoreException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VectorStoreException("Qdrant request failed path=" + path, ex);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://127.0.0.1:6333";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
