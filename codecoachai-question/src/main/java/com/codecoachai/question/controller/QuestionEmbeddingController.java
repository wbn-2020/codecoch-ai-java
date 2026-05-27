package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.codecoachai.question.feign.AiEmbeddingFeignClient;
import com.codecoachai.question.feign.dto.EmbeddingRequestDTO;
import com.codecoachai.question.feign.vo.EmbeddingResponseVO;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuestionEmbeddingController {

    private static final int EMBEDDING_BATCH_SIZE = 64;
    private static final String QUESTION_COLLECTION = "question_embedding";

    private final JdbcTemplate jdbcTemplate;
    private final AiEmbeddingFeignClient aiEmbeddingFeignClient;
    private final VectorStoreClient vectorStoreClient;

    @PostMapping("/admin/questions/embedding/rebuild")
    public Result<Map<String, Object>> rebuild(@RequestBody(required = false) RebuildDTO dto) {
        SecurityAssert.requireAdmin();
        int limit = dto == null || dto.getLimit() == null ? 500 : Math.max(1, Math.min(dto.getLimit(), 5000));
        List<Map<String, Object>> questions = jdbcTemplate.queryForList("""
                SELECT id, title, content, reference_answer, analysis, category_id, difficulty, question_type, status
                FROM question
                WHERE deleted = 0 AND status = 1
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """, limit);
        int updated = 0;
        List<VectorPoint> vectorPoints = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            Long id = ((Number) question.get("id")).longValue();
            String text = questionVectorText(question);
            String normalized = QuestionTextNormalizeUtils.normalizeTitle(text);
            jdbcTemplate.update("""
                    INSERT INTO question_embedding(question_id, text_hash, token_count, normalized_text, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, NOW(), NOW(), 0)
                    ON DUPLICATE KEY UPDATE text_hash = VALUES(text_hash), token_count = VALUES(token_count),
                                            normalized_text = VALUES(normalized_text), updated_at = NOW(), deleted = 0
                    """, id, sha256(normalized), normalized.length(), normalized);
            updated++;
        }
        if (vectorStoreClient.isEnabled() && !questions.isEmpty()) {
            vectorPoints = buildVectorPoints(questions);
            if (!vectorPoints.isEmpty()) {
                vectorStoreClient.ensureCollection(QUESTION_COLLECTION, vectorPoints.get(0).getVector().size());
                vectorStoreClient.upsert(QUESTION_COLLECTION, vectorPoints);
            }
        }
        return Result.success(Map.of(
                "updated", updated,
                "vectorEnabled", vectorStoreClient.isEnabled(),
                "vectorUpdated", vectorPoints.size()
        ));
    }

    @GetMapping("/questions/similar")
    public Result<List<Map<String, Object>>> similar(@RequestParam Long questionId,
                                                     @RequestParam(required = false) Long limit) {
        SecurityAssert.requireLoginUserId();
        int size = limit == null ? 10 : Math.max(1, Math.min(limit.intValue(), 50));
        List<Map<String, Object>> sourceQuestionRows = jdbcTemplate.queryForList("""
                SELECT id, title, content, reference_answer, analysis, category_id, difficulty, question_type, status
                FROM question
                WHERE deleted = 0 AND id = ?
                """, questionId);
        if (sourceQuestionRows.isEmpty()) {
            return Result.success(List.of());
        }
        List<Map<String, Object>> vectorResults = searchSimilarByVector(sourceQuestionRows.get(0), size);
        if (!vectorResults.isEmpty()) {
            return Result.success(vectorResults);
        }
        List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(
                "SELECT normalized_text FROM question_embedding WHERE deleted = 0 AND question_id = ?", questionId);
        if (sourceRows.isEmpty()) {
            return Result.success(List.of());
        }
        String source = stringValue(sourceRows.get(0).get("normalized_text"));
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT e.question_id, q.title, q.difficulty, e.normalized_text
                FROM question_embedding e
                JOIN question q ON q.id = e.question_id
                WHERE e.deleted = 0 AND q.deleted = 0 AND e.question_id <> ?
                ORDER BY e.updated_at DESC
                LIMIT 200
                """, questionId);
        return Result.success(candidates.stream()
                .map(row -> Map.<String, Object>of(
                        "questionId", row.get("question_id"),
                        "title", row.get("title"),
                        "difficulty", row.get("difficulty"),
                        "similarity", Math.round(QuestionTextNormalizeUtils.jaccard(source, stringValue(row.get("normalized_text"))) * 100)))
                .filter(row -> ((Number) row.get("similarity")).intValue() >= 30)
                .sorted((a, b) -> Integer.compare(((Number) b.get("similarity")).intValue(), ((Number) a.get("similarity")).intValue()))
                .limit(size)
                .toList());
    }

    private List<VectorPoint> buildVectorPoints(List<Map<String, Object>> questions) {
        List<VectorPoint> points = new ArrayList<>();
        for (int start = 0; start < questions.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(questions.size(), start + EMBEDDING_BATCH_SIZE);
            List<Map<String, Object>> batch = questions.subList(start, end);
            EmbeddingRequestDTO request = new EmbeddingRequestDTO();
            request.setTexts(batch.stream().map(this::questionVectorText).toList());
            Result<EmbeddingResponseVO> response = aiEmbeddingFeignClient.embeddings(request);
            if (response == null || !response.isSuccess() || response.getData() == null
                    || response.getData().getVectors() == null) {
                continue;
            }
            List<List<Float>> vectors = response.getData().getVectors();
            for (int i = 0; i < batch.size() && i < vectors.size(); i++) {
                Map<String, Object> question = batch.get(i);
                Long questionId = numberLong(question.get("id"));
                if (questionId == null) {
                    continue;
                }
                points.add(VectorPoint.builder()
                        .id(questionPointId(questionId))
                        .vector(vectors.get(i))
                        .payload(questionPayload(question))
                        .build());
            }
        }
        return points;
    }

    private List<Map<String, Object>> searchSimilarByVector(Map<String, Object> sourceQuestion, int size) {
        if (!vectorStoreClient.isEnabled()) {
            return List.of();
        }
        Long sourceQuestionId = numberLong(sourceQuestion.get("id"));
        if (sourceQuestionId == null) {
            return List.of();
        }
        List<VectorPoint> sourcePoints = buildVectorPoints(List.of(sourceQuestion));
        if (sourcePoints.isEmpty()) {
            return List.of();
        }
        vectorStoreClient.ensureCollection(QUESTION_COLLECTION, sourcePoints.get(0).getVector().size());
        vectorStoreClient.upsert(QUESTION_COLLECTION, sourcePoints);
        List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                .collectionName(QUESTION_COLLECTION)
                .vector(sourcePoints.get(0).getVector())
                .mustMatchPayload(questionFilter(sourceQuestion))
                .limit(Math.min(size + 5, 50))
                .build());
        if (hits.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = hits.stream()
                .map(hit -> payloadLong(hit.getPayload(), "questionId"))
                .filter(id -> id != null && !Objects.equals(id, sourceQuestionId))
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        for (VectorSearchResult hit : hits) {
            Long questionId = payloadLong(hit.getPayload(), "questionId");
            if (questionId != null && !Objects.equals(questionId, sourceQuestionId)) {
                scoreMap.putIfAbsent(questionId, hit.getScore());
            }
        }
        Map<Long, Map<String, Object>> questionMap = new LinkedHashMap<>();
        String placeholders = String.join(",", Collections.nCopies(questionIds.size(), "?"));
        Object[] args = questionIds.toArray();
        for (Map<String, Object> row : jdbcTemplate.queryForList(("""
                SELECT id, title, difficulty, question_type
                FROM question
                WHERE deleted = 0 AND status = 1 AND id IN (%s)
                """).formatted(placeholders), args)) {
            Long id = numberLong(row.get("id"));
            if (id != null) {
                questionMap.put(id, row);
            }
        }
        return questionIds.stream()
                .map(questionMap::get)
                .filter(Objects::nonNull)
                .map(row -> {
                    Long id = numberLong(row.get("id"));
                    return Map.<String, Object>of(
                            "questionId", id,
                            "title", firstText(stringValue(row.get("title")), ""),
                            "difficulty", firstText(stringValue(row.get("difficulty")), ""),
                            "questionType", firstText(stringValue(row.get("question_type")), ""),
                            "similarity", Math.round(scoreMap.getOrDefault(id, 0D) * 100),
                            "matchType", "VECTOR"
                    );
                })
                .limit(size)
                .toList();
    }

    private String questionVectorText(Map<String, Object> question) {
        return firstText(stringValue(question.get("title")), "") + "\n"
                + firstText(stringValue(question.get("content")), "") + "\n"
                + firstText(stringValue(question.get("reference_answer")), "") + "\n"
                + firstText(stringValue(question.get("analysis")), "");
    }

    private Map<String, Object> questionPayload(Map<String, Object> question) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", numberLong(question.get("id")));
        payload.put("status", 1);
        if (question.get("category_id") != null) {
            payload.put("categoryId", numberLong(question.get("category_id")));
        }
        if (StringUtils.hasText(stringValue(question.get("question_type")))) {
            payload.put("questionType", stringValue(question.get("question_type")));
        }
        if (StringUtils.hasText(stringValue(question.get("difficulty")))) {
            payload.put("difficulty", stringValue(question.get("difficulty")));
        }
        return payload;
    }

    private Map<String, Object> questionFilter(Map<String, Object> question) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("status", 1);
        if (question.get("category_id") != null) {
            filter.put("categoryId", numberLong(question.get("category_id")));
        }
        if (StringUtils.hasText(stringValue(question.get("question_type")))) {
            filter.put("questionType", stringValue(question.get("question_type")));
        }
        return filter;
    }

    private String questionPointId(Long questionId) {
        return "question-" + questionId;
    }

    private Long payloadLong(Map<String, Object> payload, String key) {
        return payload == null ? null : numberLong(payload.get(key));
    }

    private Long numberLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    @Data
    public static class RebuildDTO {
        private Integer limit;
    }
}
