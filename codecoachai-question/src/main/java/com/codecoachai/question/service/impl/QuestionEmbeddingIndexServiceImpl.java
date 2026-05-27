package com.codecoachai.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.codecoachai.common.vector.domain.VectorSearchRequest;
import com.codecoachai.common.vector.domain.VectorSearchResult;
import com.codecoachai.common.vector.service.VectorStoreClient;
import com.codecoachai.question.domain.entity.Question;
import com.codecoachai.question.feign.AiEmbeddingFeignClient;
import com.codecoachai.question.feign.dto.EmbeddingRequestDTO;
import com.codecoachai.question.feign.vo.EmbeddingResponseVO;
import com.codecoachai.question.mapper.QuestionMapper;
import com.codecoachai.question.service.QuestionEmbeddingIndexService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionEmbeddingIndexServiceImpl implements QuestionEmbeddingIndexService {

    private static final int EMBEDDING_BATCH_SIZE = 64;
    private static final String QUESTION_COLLECTION = "question_embedding";

    private final QuestionMapper questionMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AiEmbeddingFeignClient aiEmbeddingFeignClient;
    private final VectorStoreClient vectorStoreClient;

    @Override
    public Map<String, Object> rebuild(Integer limit) {
        int size = limit == null ? 500 : Math.max(1, Math.min(limit, 5000));
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, CommonConstants.YES)
                .orderByDesc(Question::getUpdatedAt)
                .last("LIMIT " + size));
        int metadataUpdated = upsertMetadata(questions);
        int vectorUpdated = indexQuestions(questions);
        return Map.of(
                "updated", metadataUpdated,
                "vectorEnabled", vectorStoreClient.isEnabled(),
                "vectorUpdated", vectorUpdated
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
        indexQuestions(List.of(question));
    }

    @Override
    public void deleteQuestion(Long questionId) {
        if (questionId == null) {
            return;
        }
        jdbcTemplate.update("UPDATE question_embedding SET deleted = 1, updated_at = NOW() WHERE question_id = ?",
                questionId);
        if (vectorStoreClient.isEnabled()) {
            vectorStoreClient.delete(QUESTION_COLLECTION, List.of(questionPointId(questionId)));
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

    private int upsertMetadata(List<Question> questions) {
        int updated = 0;
        for (Question question : questions) {
            String normalized = QuestionTextNormalizeUtils.normalizeTitle(questionVectorText(question));
            jdbcTemplate.update("""
                    INSERT INTO question_embedding(question_id, text_hash, token_count, normalized_text, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, NOW(), NOW(), 0)
                    ON DUPLICATE KEY UPDATE text_hash = VALUES(text_hash), token_count = VALUES(token_count),
                                            normalized_text = VALUES(normalized_text), updated_at = NOW(), deleted = 0
                    """, question.getId(), sha256(normalized), normalized.length(), normalized);
            updated++;
        }
        return updated;
    }

    private int indexQuestions(List<Question> questions) {
        if (!vectorStoreClient.isEnabled() || questions.isEmpty()) {
            return 0;
        }
        List<VectorPoint> vectorPoints = buildVectorPoints(questions);
        if (vectorPoints.isEmpty()) {
            return 0;
        }
        vectorStoreClient.ensureCollection(QUESTION_COLLECTION, vectorPoints.get(0).getVector().size());
        vectorStoreClient.upsert(QUESTION_COLLECTION, vectorPoints);
        return vectorPoints.size();
    }

    private List<VectorPoint> buildVectorPoints(List<Question> questions) {
        List<VectorPoint> points = new ArrayList<>();
        for (int start = 0; start < questions.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(questions.size(), start + EMBEDDING_BATCH_SIZE);
            List<Question> batch = questions.subList(start, end);
            EmbeddingRequestDTO request = new EmbeddingRequestDTO();
            request.setTexts(batch.stream().map(this::questionVectorText).toList());
            Result<EmbeddingResponseVO> response = aiEmbeddingFeignClient.embeddings(request);
            if (response == null || !response.isSuccess() || response.getData() == null
                    || response.getData().getVectors() == null) {
                log.warn("Question embedding response empty batchSize={}", batch.size());
                continue;
            }
            List<List<Float>> vectors = response.getData().getVectors();
            for (int i = 0; i < batch.size() && i < vectors.size(); i++) {
                Question question = batch.get(i);
                points.add(VectorPoint.builder()
                        .id(questionPointId(question.getId()))
                        .vector(vectors.get(i))
                        .payload(questionPayload(question))
                        .build());
            }
        }
        return points;
    }

    private List<Map<String, Object>> searchSimilarByVector(Question sourceQuestion, int size) {
        if (!vectorStoreClient.isEnabled() || sourceQuestion.getId() == null) {
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

    private Map<String, Object> questionPayload(Question question) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", question.getId());
        payload.put("status", CommonConstants.YES);
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

    private Map<String, Object> questionFilter(Question question) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("status", CommonConstants.YES);
        if (question.getCategoryId() != null) {
            filter.put("categoryId", question.getCategoryId());
        }
        if (StringUtils.hasText(question.getQuestionType())) {
            filter.put("questionType", question.getQuestionType());
        }
        return filter;
    }

    private String questionPointId(Long questionId) {
        return "question-" + questionId;
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
}
