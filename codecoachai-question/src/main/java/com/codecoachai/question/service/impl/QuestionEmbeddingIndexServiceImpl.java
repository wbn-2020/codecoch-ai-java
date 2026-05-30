package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.codecoachai.question.config.QuestionDuplicateProperties;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.feign.AiEmbeddingFeignClient;
import com.codecoachai.question.feign.dto.EmbeddingRequestDTO;
import com.codecoachai.question.feign.vo.EmbeddingResponseVO;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
import com.codecoachai.question.service.QuestionEmbeddingIndexService.SemanticHit;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionEmbeddingIndexServiceImpl implements QuestionEmbeddingIndexService {

    private static final String VECTOR_DELETE_COLLECTION_QUESTION = "question_embedding";
    private static final Map<String, String> QUESTION_PAYLOAD_INDEXES = Map.of(
            "questionId", "integer",
            "status", "integer",
            "categoryId", "integer",
            "questionType", "keyword",
            "difficulty", "keyword"
    );

    private final QuestionMapper questionMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AiEmbeddingFeignClient aiEmbeddingFeignClient;
    private final VectorStoreClient vectorStoreClient;
    private final QuestionDuplicateProperties duplicateProperties;

    @Override
    public Map<String, Object> rebuild(Integer limit) {
        int size = limit == null ? 500 : Math.max(1, Math.min(limit, 5000));
        InactiveCleanupResult inactiveCleanup = cleanupInactiveQuestionVectors(size);
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, CommonConstants.YES)
                .orderByDesc(Question::getUpdatedAt)
                .last("LIMIT " + size));
        int metadataUpdated = upsertMetadata(questions);
        IndexResult indexResult = indexQuestionEntities(questions);
        return Map.of(
                "updated", metadataUpdated,
                "vectorEnabled", vectorStoreClient.isEnabled(),
                "vectorUpdated", indexResult.vectorUpdated(),
                "vectorDeleted", inactiveCleanup.vectorDeleted(),
                "inactiveDeleted", inactiveCleanup.metadataDeleted(),
                "failedBatches", indexResult.failedBatches(),
                "errors", indexResult.errors()
        );
    }

    @Override
    public Map<String, Object> stats() {
        List<Map<String, Object>> statusRows = jdbcTemplate.queryForList("""
                SELECT COALESCE(index_status, 'PENDING') AS status, COUNT(1) AS count
                FROM question_embedding
                WHERE deleted = 0
                GROUP BY COALESCE(index_status, 'PENDING')
                """);
        List<Map<String, Object>> dimensionRows = jdbcTemplate.queryForList("""
                SELECT embedding_dimension AS dimension, COUNT(1) AS count
                FROM question_embedding
                WHERE deleted = 0 AND embedding_dimension IS NOT NULL
                GROUP BY embedding_dimension
                ORDER BY count DESC
                """);
        List<Map<String, Object>> modelRows = jdbcTemplate.queryForList("""
                SELECT COALESCE(embedding_model, 'UNKNOWN') AS model, COUNT(1) AS count
                FROM question_embedding
                WHERE deleted = 0
                GROUP BY COALESCE(embedding_model, 'UNKNOWN')
                ORDER BY count DESC
                """);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM question_embedding
                WHERE deleted = 0
                """, Long.class);
        Long failed = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM question_embedding
                WHERE deleted = 0 AND index_status = 'FAILED'
                """, Long.class);
        Long stalePending = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM question_embedding
                WHERE deleted = 0
                  AND index_status = 'PENDING'
                  AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
                """, Long.class);
        java.sql.Timestamp lastIndexedAt = jdbcTemplate.queryForObject("""
                SELECT MAX(indexed_at)
                FROM question_embedding
                WHERE deleted = 0 AND indexed_at IS NOT NULL
                """, java.sql.Timestamp.class);
        java.sql.Timestamp lastFailedAt = jdbcTemplate.queryForObject("""
                SELECT MAX(updated_at)
                FROM question_embedding
                WHERE deleted = 0 AND index_status = 'FAILED'
                """, java.sql.Timestamp.class);
        Double averageTextChars = jdbcTemplate.queryForObject("""
                SELECT AVG(token_count)
                FROM question_embedding
                WHERE deleted = 0 AND token_count IS NOT NULL
                """, Double.class);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("vectorEnabled", vectorStoreClient.isEnabled());
        stats.put("total", total == null ? 0L : total);
        stats.put("failed", failed == null ? 0L : failed);
        stats.put("stalePending", stalePending == null ? 0L : stalePending);
        stats.put("statusCounts", statusRows);
        stats.put("dimensionCounts", dimensionRows);
        stats.put("modelCounts", modelRows);
        stats.put("lastIndexedAt", lastIndexedAt == null ? null : lastIndexedAt.toLocalDateTime());
        stats.put("lastFailedAt", lastFailedAt == null ? null : lastFailedAt.toLocalDateTime());
        stats.put("averageTextChars", averageTextChars == null ? null : Math.round(averageTextChars));
        stats.put("collection", vectorStoreClient.collectionInfo(QUESTION_COLLECTION));
        return stats;
    }

    @Override
    public Map<String, Object> retryFailed(Integer limit) {
        int size = limit == null ? 200 : Math.max(1, Math.min(limit, 1000));
        int vectorDeleted = retryPendingQuestionVectorDeletes(size);
        List<Long> questionIds = jdbcTemplate.queryForList("""
                SELECT question_id
                FROM question_embedding
                WHERE deleted = 0
                  AND (
                    index_status = 'FAILED'
                    OR (index_status = 'PENDING' AND updated_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
                  )
                ORDER BY updated_at ASC
                LIMIT ?
                """, Long.class, size);
        int retried = 0;
        List<String> errors = new ArrayList<>();
        for (Long questionId : questionIds) {
            try {
                indexQuestion(questionId);
                retried++;
            } catch (Exception ex) {
                errors.add("questionId=" + questionId + ": " + trimError(ex.getMessage()));
            }
        }
        return Map.of(
                "vectorEnabled", vectorStoreClient.isEnabled(),
                "requested", size,
                "matched", questionIds.size(),
                "retried", retried,
                "vectorDeleted", vectorDeleted,
                "errors", errors
        );
    }

    @Override
    public void indexQuestion(Long questionId) {
        if (questionId == null) {
            return;
        }
        Question question = questionMapper.selectById(questionId);
        if (question == null || !CommonConstants.YES.equals(question.getStatus())) {
            deleteQuestion(questionId);
            return;
        }
        upsertMetadata(List.of(question));
        indexQuestionEntities(List.of(question));
    }

    @Override
    public void indexQuestions(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return;
        }
        List<Long> ids = questionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, ids)
                .eq(Question::getStatus, CommonConstants.YES));
        if (questions.isEmpty()) {
            return;
        }
        upsertMetadata(questions);
        indexQuestionEntities(questions);
    }

    @Override
    public void deleteQuestion(Long questionId) {
        if (questionId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE question_embedding
                SET deleted = 1, index_status = 'DELETED', updated_at = NOW()
                WHERE question_id = ?
                """,
                questionId);
        if (vectorStoreClient.isEnabled()) {
            String pointId = questionPointId(questionId);
            recordVectorDeleteOutbox(List.of(pointId));
            deleteVectorPointsFromOutbox(List.of(pointId));
        }
    }

    @Override
    public List<Map<String, Object>> searchSimilar(Long questionId, Integer limit) {
        int size = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        Question sourceQuestion = questionMapper.selectById(questionId);
        if (sourceQuestion == null) {
            return List.of();
        }
        List<Map<String, Object>> vectorResults = searchSimilarByVector(sourceQuestion, size);
        if (!vectorResults.isEmpty()) {
            return vectorResults;
        }
        return searchSimilarByTextHash(questionId, size);
    }

    @Override
    public List<SemanticHit> searchSimilarIndexed(Long questionId, Integer limit, Double scoreThreshold) {
        int size = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        Question sourceQuestion = questionMapper.selectById(questionId);
        if (!vectorStoreClient.isEnabled() || sourceQuestion == null || sourceQuestion.getId() == null) {
            return List.of();
        }
        upsertMetadata(List.of(sourceQuestion));
        List<VectorPoint> sourcePoints = buildVectorPoints(List.of(sourceQuestion), new ArrayList<>());
        if (sourcePoints.isEmpty()) {
            return List.of();
        }
        VectorPoint sourcePoint = sourcePoints.get(0);
        ensureQuestionCollection(sourcePoint.getVector().size());
        List<VectorSearchResult> hits = vectorStoreClient.search(VectorSearchRequest.builder()
                .collectionName(QUESTION_COLLECTION)
                .vector(sourcePoint.getVector())
                .mustMatchPayload(questionFilter(sourceQuestion))
                .scoreThreshold(scoreThreshold)
                .limit(Math.min(size + 5, 50))
                .build());
        return hits.stream()
                .map(hit -> new SemanticHit(payloadLong(hit.getPayload(), "questionId"), hit.getScore()))
                .filter(hit -> hit.questionId() != null && !Objects.equals(hit.questionId(), questionId))
                .distinct()
                .limit(size)
                .toList();
    }

    private void recordVectorDeleteOutbox(List<String> pointIds) {
        for (String pointId : pointIds) {
            jdbcTemplate.update("""
                    INSERT INTO vector_delete_outbox(collection_name, point_id, biz_type, status, retry_count, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, 'PENDING', 0, NOW(), NOW(), 0)
                    ON DUPLICATE KEY UPDATE status = CASE WHEN status = 'DONE' THEN 'DONE' ELSE 'PENDING' END,
                                            updated_at = NOW(),
                                            deleted = 0
                    """, QUESTION_COLLECTION, pointId, VECTOR_DELETE_COLLECTION_QUESTION);
        }
    }

    private int retryPendingQuestionVectorDeletes(int limit) {
        if (!vectorStoreClient.isEnabled()) {
            return 0;
        }
        List<String> pointIds = jdbcTemplate.queryForList("""
                SELECT point_id
                FROM vector_delete_outbox
                WHERE deleted = 0
                  AND collection_name = ?
                  AND biz_type = ?
                  AND status IN ('PENDING', 'FAILED')
                ORDER BY updated_at ASC
                LIMIT ?
                """, String.class, QUESTION_COLLECTION, VECTOR_DELETE_COLLECTION_QUESTION, limit);
        return deleteVectorPointsFromOutbox(pointIds);
    }

    private InactiveCleanupResult cleanupInactiveQuestionVectors(int limit) {
        int size = Math.max(1, Math.min(limit, 5000));
        List<Long> questionIds = jdbcTemplate.queryForList("""
                SELECT e.question_id
                FROM question_embedding e
                LEFT JOIN question q ON q.id = e.question_id AND q.deleted = 0
                WHERE e.deleted = 0
                  AND COALESCE(e.index_status, 'PENDING') <> 'DELETED'
                  AND (q.id IS NULL OR q.status <> ?)
                ORDER BY e.updated_at ASC
                LIMIT ?
                """, Long.class, CommonConstants.YES, size);
        if (questionIds.isEmpty()) {
            return new InactiveCleanupResult(0, 0);
        }
        jdbcTemplate.update("""
                UPDATE question_embedding
                SET deleted = 1, index_status = 'DELETED', updated_at = NOW()
                WHERE question_id IN (%s)
                """.formatted(sqlPlaceholders(questionIds.size())), questionIds.toArray());
        if (!vectorStoreClient.isEnabled()) {
            return new InactiveCleanupResult(questionIds.size(), 0);
        }
        List<String> pointIds = questionIds.stream().map(this::questionPointId).toList();
        recordVectorDeleteOutbox(pointIds);
        return new InactiveCleanupResult(questionIds.size(), deleteVectorPointsFromOutbox(pointIds));
    }

    private int deleteVectorPointsFromOutbox(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return 0;
        }
        try {
            vectorStoreClient.delete(QUESTION_COLLECTION, pointIds);
            jdbcTemplate.update("""
                    UPDATE vector_delete_outbox
                    SET status = 'DONE', last_error = NULL, updated_at = NOW()
                    WHERE collection_name = ? AND point_id IN (%s)
                    """.formatted(sqlPlaceholders(pointIds.size())),
                    vectorDeleteSqlArgs(pointIds).toArray());
            return pointIds.size();
        } catch (Exception ex) {
            jdbcTemplate.update("""
                    UPDATE vector_delete_outbox
                    SET status = 'FAILED', retry_count = retry_count + 1, last_error = ?, updated_at = NOW()
                    WHERE collection_name = ? AND point_id IN (%s)
                    """.formatted(sqlPlaceholders(pointIds.size())),
                    vectorDeleteSqlArgs(pointIds, trimError(ex.getMessage())).toArray());
            log.warn("Question vector delete failed pointCount={}", pointIds.size(), ex);
            return 0;
        }
    }

    private String sqlPlaceholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private List<Object> vectorDeleteSqlArgs(List<String> pointIds) {
        List<Object> args = new ArrayList<>();
        args.add(QUESTION_COLLECTION);
        args.addAll(pointIds);
        return args;
    }

    private List<Object> vectorDeleteSqlArgs(List<String> pointIds, String error) {
        List<Object> args = new ArrayList<>();
        args.add(error);
        args.add(QUESTION_COLLECTION);
        args.addAll(pointIds);
        return args;
    }

    private int upsertMetadata(List<Question> questions) {
        int updated = 0;
        for (Question question : questions) {
            String normalized = QuestionTextNormalizeUtils.normalizeTitle(questionVectorText(question));
            jdbcTemplate.update("""
                    INSERT INTO question_embedding(question_id, text_hash, token_count, normalized_text, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, NOW(), NOW(), 0)
                    ON DUPLICATE KEY UPDATE index_status = CASE
                                                WHEN text_hash <> VALUES(text_hash) THEN 'PENDING'
                                                ELSE index_status
                                            END,
                                            last_error = CASE
                                                WHEN text_hash <> VALUES(text_hash) THEN NULL
                                                ELSE last_error
                                            END,
                                            text_hash = VALUES(text_hash), token_count = VALUES(token_count),
                                            normalized_text = VALUES(normalized_text),
                                            updated_at = NOW(), deleted = 0
                    """, question.getId(), sha256(normalized), normalized.length(), normalized);
            updated++;
        }
        return updated;
    }

    private IndexResult indexQuestionEntities(List<Question> questions) {
        List<String> errors = new ArrayList<>();
        if (!vectorStoreClient.isEnabled() || questions.isEmpty()) {
            return new IndexResult(0, 0, errors);
        }
        List<VectorPoint> vectorPoints = buildVectorPoints(questions, errors);
        if (vectorPoints.isEmpty()) {
            return new IndexResult(0, errors.size(), errors);
        }
        try {
            ensureQuestionCollection(vectorPoints.get(0).getVector().size());
            vectorStoreClient.upsert(QUESTION_COLLECTION, vectorPoints);
            for (VectorPoint point : vectorPoints) {
                Long questionId = payloadLong(point.getPayload(), "questionId");
                if (questionId != null) {
                    markIndexed(questionId, point.getVector().size(), stringValue(point.getPayload().get("embeddingModel")));
                }
            }
        } catch (Exception ex) {
            log.warn("Question vector upsert failed pointCount={}", vectorPoints.size(), ex);
            errors.add("vector upsert failed: " + trimError(ex.getMessage()));
            markFailed(vectorPoints, ex);
            return new IndexResult(0, errors.size(), errors);
        }
        return new IndexResult(vectorPoints.size(), errors.size(), errors);
    }

    private List<VectorPoint> buildVectorPoints(List<Question> questions, List<String> errors) {
        List<VectorPoint> points = new ArrayList<>();
        int batchSize = Math.max(1, duplicateProperties.getEmbeddingBatchSize());
        for (int start = 0; start < questions.size(); start += batchSize) {
            int end = Math.min(questions.size(), start + batchSize);
            List<Question> batch = questions.subList(start, end);
            EmbeddingRequestDTO request = new EmbeddingRequestDTO();
            request.setTexts(batch.stream().map(this::questionVectorText).toList());
            Result<EmbeddingResponseVO> response;
            try {
                response = aiEmbeddingFeignClient.embeddings(request);
            } catch (Exception ex) {
                log.warn("Question embedding request failed batchStart={} batchSize={}", start, batch.size(), ex);
                String error = "embedding batch " + start + " failed: " + trimError(ex.getMessage());
                errors.add(error);
                markFailedQuestions(batch, error);
                continue;
            }
            if (response == null || !response.isSuccess() || response.getData() == null
                    || response.getData().getVectors() == null) {
                log.warn("Question embedding response empty batchSize={}", batch.size());
                String error = "embedding batch " + start + " returned empty response";
                errors.add(error);
                markFailedQuestions(batch, error);
                continue;
            }
            List<List<Float>> vectors = response.getData().getVectors();
            if (vectors.size() != batch.size()) {
                String error = "embedding batch " + start + " vector count mismatch: expected "
                        + batch.size() + ", actual " + vectors.size();
                errors.add(error);
                if (vectors.size() < batch.size()) {
                    markFailedQuestions(batch.subList(vectors.size(), batch.size()), error);
                }
            }
            for (int i = 0; i < batch.size() && i < vectors.size(); i++) {
                Question question = batch.get(i);
                points.add(VectorPoint.builder()
                        .id(questionPointId(question.getId()))
                        .vector(vectors.get(i))
                        .payload(questionPayload(question, response.getData()))
                        .build());
            }
        }
        return points;
    }

    private List<Map<String, Object>> searchSimilarByVector(Question sourceQuestion, int size) {
        if (!vectorStoreClient.isEnabled() || sourceQuestion.getId() == null) {
            return List.of();
        }
        upsertMetadata(List.of(sourceQuestion));
        List<VectorPoint> sourcePoints = buildVectorPoints(List.of(sourceQuestion), new ArrayList<>());
        if (sourcePoints.isEmpty()) {
            return List.of();
        }
        ensureQuestionCollection(sourcePoints.get(0).getVector().size());
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
                .filter(id -> id != null && !Objects.equals(id, sourceQuestion.getId()))
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        for (VectorSearchResult hit : hits) {
            Long id = payloadLong(hit.getPayload(), "questionId");
            if (id != null && !Objects.equals(id, sourceQuestion.getId())) {
                scoreMap.putIfAbsent(id, hit.getScore());
            }
        }
        Map<Long, Question> questionMap = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .in(Question::getId, questionIds)
                        .eq(Question::getStatus, CommonConstants.YES))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Question::getId, item -> item));
        return questionIds.stream()
                .map(questionMap::get)
                .filter(Objects::nonNull)
                .map(question -> Map.<String, Object>of(
                        "questionId", question.getId(),
                        "title", firstText(question.getTitle(), ""),
                        "difficulty", firstText(question.getDifficulty(), ""),
                        "questionType", firstText(question.getQuestionType(), ""),
                        "similarity", Math.round(scoreMap.getOrDefault(question.getId(), 0D) * 100),
                        "matchType", "VECTOR"
                ))
                .limit(size)
                .toList();
    }

    private List<Map<String, Object>> searchSimilarByTextHash(Long questionId, int size) {
        List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(
                "SELECT normalized_text FROM question_embedding WHERE deleted = 0 AND question_id = ?", questionId);
        if (sourceRows.isEmpty()) {
            return List.of();
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
        return candidates.stream()
                .map(row -> Map.<String, Object>of(
                        "questionId", row.get("question_id"),
                        "title", row.get("title"),
                        "difficulty", firstText(stringValue(row.get("difficulty")), ""),
                        "similarity", Math.round(QuestionTextNormalizeUtils.jaccard(source, stringValue(row.get("normalized_text"))) * 100),
                        "matchType", "TEXT_HASH"
                ))
                .filter(row -> ((Number) row.get("similarity")).intValue() >= 30)
                .sorted((a, b) -> Integer.compare(((Number) b.get("similarity")).intValue(),
                        ((Number) a.get("similarity")).intValue()))
                .limit(size)
                .toList();
    }

    private String questionVectorText(Question question) {
        return firstText(question.getTitle(), "") + "\n"
                + firstText(question.getContent(), "") + "\n"
                + firstText(question.getReferenceAnswer(), "") + "\n"
                + firstText(question.getAnalysis(), "");
    }

    private void ensureQuestionCollection(int dimension) {
        vectorStoreClient.ensureCollection(QUESTION_COLLECTION, dimension);
        vectorStoreClient.ensurePayloadIndexes(QUESTION_COLLECTION, QUESTION_PAYLOAD_INDEXES);
    }

    private Map<String, Object> questionPayload(Question question, EmbeddingResponseVO embedding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", question.getId());
        payload.put("status", CommonConstants.YES);
        if (embedding != null && StringUtils.hasText(embedding.getModel())) {
            payload.put("embeddingModel", embedding.getModel());
        }
        if (question.getCategoryId() != null) {
            payload.put("categoryId", question.getCategoryId());
        }
        if (StringUtils.hasText(question.getQuestionType())) {
            payload.put("questionType", question.getQuestionType());
        }
        if (StringUtils.hasText(question.getDifficulty())) {
            payload.put("difficulty", question.getDifficulty());
        }
        return payload;
    }

    private void markIndexed(Question question, int dimension, String model) {
        markIndexed(question.getId(), dimension, model);
    }

    private void markIndexed(Long questionId, int dimension, String model) {
        jdbcTemplate.update("""
                UPDATE question_embedding
                SET embedding_model = ?, embedding_dimension = ?, indexed_at = NOW(),
                    index_status = 'INDEXED', last_error = NULL, updated_at = NOW(), deleted = 0
                WHERE question_id = ?
                """, model, dimension, questionId);
    }

    private void markFailed(List<VectorPoint> points, Exception ex) {
        String error = trimError(ex == null ? null : ex.getMessage());
        for (VectorPoint point : points) {
            Long questionId = payloadLong(point.getPayload(), "questionId");
            if (questionId != null) {
                jdbcTemplate.update("""
                        UPDATE question_embedding
                        SET index_status = 'FAILED', last_error = ?, updated_at = NOW()
                        WHERE question_id = ?
                        """, error, questionId);
            }
        }
    }

    private void markFailedQuestions(List<Question> questions, String error) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        String value = trimError(error);
        for (Question question : questions) {
            if (question.getId() == null) {
                continue;
            }
            jdbcTemplate.update("""
                    UPDATE question_embedding
                    SET index_status = 'FAILED', last_error = ?, updated_at = NOW()
                    WHERE question_id = ?
                    """, value, question.getId());
        }
    }

    private Map<String, Object> questionFilter(Question question) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("status", CommonConstants.YES);
        return filter;
    }

    private String questionPointId(Long questionId) {
        return UUID.nameUUIDFromBytes(("question:" + questionId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Long payloadLong(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
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

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown error";
        }
        return message.length() <= 180 ? message : message.substring(0, 180);
    }

    private record InactiveCleanupResult(int metadataDeleted, int vectorDeleted) {
    }

    private record IndexResult(int vectorUpdated, int failedBatches, List<String> errors) {
    }
}
