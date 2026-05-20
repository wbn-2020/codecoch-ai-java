package com.codecoachai.question.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.question.util.QuestionTextNormalizeUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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

    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/admin/questions/embedding/rebuild")
    public Result<Map<String, Object>> rebuild(@RequestBody(required = false) RebuildDTO dto) {
        SecurityAssert.requireAdmin();
        int limit = dto == null || dto.getLimit() == null ? 500 : Math.max(1, Math.min(dto.getLimit(), 5000));
        List<Map<String, Object>> questions = jdbcTemplate.queryForList("""
                SELECT id, title, content FROM question
                WHERE deleted = 0
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """, limit);
        int updated = 0;
        for (Map<String, Object> question : questions) {
            Long id = ((Number) question.get("id")).longValue();
            String text = firstText(stringValue(question.get("title")), "") + "\n" + firstText(stringValue(question.get("content")), "");
            String normalized = QuestionTextNormalizeUtils.normalizeTitle(text);
            jdbcTemplate.update("""
                    INSERT INTO question_embedding(question_id, text_hash, token_count, normalized_text, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, NOW(), NOW(), 0)
                    ON DUPLICATE KEY UPDATE text_hash = VALUES(text_hash), token_count = VALUES(token_count),
                                            normalized_text = VALUES(normalized_text), updated_at = NOW(), deleted = 0
                    """, id, sha256(normalized), normalized.length(), normalized);
            updated++;
        }
        return Result.success(Map.of("updated", updated));
    }

    @GetMapping("/questions/similar")
    public Result<List<Map<String, Object>>> similar(@RequestParam Long questionId,
                                                     @RequestParam(required = false) Long limit) {
        SecurityAssert.requireLoginUserId();
        int size = limit == null ? 10 : Math.max(1, Math.min(limit.intValue(), 50));
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
