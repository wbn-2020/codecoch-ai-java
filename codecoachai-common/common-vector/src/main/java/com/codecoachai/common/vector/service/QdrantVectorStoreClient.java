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
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        VectorCollectionInfo info = collectionInfo(collectionName);
        if (Boolean.TRUE.equals(info.getExists())) {
            if (info.getVectorSize() != null && info.getVectorSize() != dimension) {
                throw new VectorStoreException("Qdrant collection dimension mismatch collection=" + collectionName
                        + " expected=" + dimension + " actual=" + info.getVectorSize());
            }
            return;
        }
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", dimension);
        vectors.put("distance", "Cosine");
        send("PUT", "/collections/" + collectionName, Map.of("vectors", vectors));
    }

    @Override
    public void ensurePayloadIndexes(String collectionName, Map<String, String> fieldSchemas) {
        if (!isEnabled() || CollectionUtils.isEmpty(fieldSchemas)) {
            return;
        }
        for (Map.Entry<String, String> entry : fieldSchemas.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            try {
                send("PUT", "/collections/" + collectionName + "/index", Map.of(
                        "field_name", entry.getKey(),
                        "field_schema", entry.getValue()
                ));
            } catch (VectorStoreException ex) {
                String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (!message.contains("already") && !message.contains("exists")) {
                    throw ex;
                }
            }
        }
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
            item.put("id", qdrantPointIdValue(point.getId()));
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
            // 调用方阈值按归一化后的口径（[0,1]），传给 Qdrant 前需还原为原始分数口径，二者保持一致
            body.put("score_threshold", denormalizeScore(request.getScoreThreshold()));
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
                    .score(normalizeScore(item.path("score").asDouble()))
                    .payload(objectMapper.convertValue(item.path("payload"), MAP_TYPE))
                    .build());
        }
        return results;
    }

    /**
     * 将向量库返回的原始分数按配置归一化。COSINE_TO_UNIT 时把余弦 [-1,1] 映射到 [0,1]，并裁剪到合法区间。
     */
    private double normalizeScore(double rawScore) {
        if (properties.getScoreNormalization() == VectorStoreProperties.ScoreNormalization.COSINE_TO_UNIT) {
            double unit = (rawScore + 1.0d) / 2.0d;
            return Math.max(0.0d, Math.min(1.0d, unit));
        }
        return rawScore;
    }

    /**
     * 将归一化口径的阈值还原为原始分数口径，用于下发给向量库的 score_threshold。
     */
    private double denormalizeScore(double normalizedThreshold) {
        if (properties.getScoreNormalization() == VectorStoreProperties.ScoreNormalization.COSINE_TO_UNIT) {
            return normalizedThreshold * 2.0d - 1.0d;
        }
        return normalizedThreshold;
    }

    @Override
    public void delete(String collectionName, List<String> pointIds) {
        if (CollectionUtils.isEmpty(pointIds)) {
            return;
        }
        send("POST", "/collections/" + collectionName + "/points/delete?wait=true",
                Map.of("points", pointIds.stream().map(this::qdrantPointIdValue).toList()));
    }

    private Object qdrantPointIdValue(String pointId) {
        if (!StringUtils.hasText(pointId)) {
            throw new VectorStoreException("Qdrant point id must not be blank");
        }
        if (isUnsignedIntegerPointId(pointId)) {
            return new BigInteger(pointId);
        }
        if (isUuidPointId(pointId)) {
            return pointId;
        }
        throw new VectorStoreException("Qdrant point id must be an unsigned integer or UUID: " + pointId);
    }

    private boolean isUnsignedIntegerPointId(String pointId) {
        return pointId.chars().allMatch(Character::isDigit);
    }

    private boolean isUuidPointId(String pointId) {
        try {
            UUID.fromString(pointId);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
