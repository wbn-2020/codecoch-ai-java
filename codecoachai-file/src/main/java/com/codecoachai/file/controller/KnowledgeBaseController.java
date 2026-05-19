package com.codecoachai.file.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/knowledge/documents")
    public Result<Map<String, Object>> createDocument(@RequestBody KnowledgeDocumentCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        String content = dto == null ? null : dto.getContent();
        String title = dto == null ? null : dto.getTitle();
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new com.codecoachai.common.core.exception.BusinessException(
                    com.codecoachai.common.core.enums.ErrorCode.PARAM_ERROR, "title and content are required");
        }
        jdbcTemplate.update("""
                INSERT INTO knowledge_document(user_id, file_id, title, source_type, status, content_text, created_at, updated_at, deleted)
                VALUES (?, ?, ?, ?, 'INDEXED', ?, NOW(), NOW(), 0)
                """, userId, dto.getFileId(), title.trim(), firstText(dto.getSourceType(), "MANUAL"), content);
        Long documentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        indexChunks(userId, documentId, content);
        return Result.success(getDocument(userId, documentId));
    }

    @GetMapping("/knowledge/documents")
    public Result<PageResult<Map<String, Object>>> documents(@RequestParam(required = false) Long pageNo,
                                                             @RequestParam(required = false) Long pageSize) {
        Long userId = SecurityAssert.requireLoginUserId();
        long pn = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_document WHERE deleted = 0 AND user_id = ?", Long.class, userId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, file_id, title, source_type, status, created_at, updated_at
                FROM knowledge_document
                WHERE deleted = 0 AND user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, userId, ps, (pn - 1) * ps);
        return Result.success(PageResult.of(rows, total == null ? 0 : total, pn, ps));
    }

    @GetMapping("/knowledge/documents/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(getDocument(SecurityAssert.requireLoginUserId(), id));
    }

    @GetMapping("/knowledge/search")
    public Result<List<Map<String, Object>>> search(@RequestParam String keyword,
                                                    @RequestParam(required = false) Long limit) {
        Long userId = SecurityAssert.requireLoginUserId();
        int size = limit == null ? 10 : Math.max(1, Math.min(limit.intValue(), 50));
        if (!StringUtils.hasText(keyword)) {
            return Result.success(List.of());
        }
        return Result.success(jdbcTemplate.queryForList("""
                SELECT c.id, c.document_id, d.title document_title, c.chunk_index, c.content_text,
                       CASE WHEN c.content_text LIKE ? THEN 100 ELSE 60 END score
                FROM knowledge_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                WHERE c.deleted = 0 AND d.deleted = 0 AND c.user_id = ? AND c.content_text LIKE ?
                ORDER BY score DESC, c.updated_at DESC, c.id DESC
                LIMIT ?
                """, keyword + "%", userId, "%" + keyword + "%", size));
    }

    @PostMapping("/knowledge/ask")
    public Result<Map<String, Object>> ask(@RequestBody KnowledgeAskDTO dto) {
        String question = dto == null ? null : dto.getQuestion();
        if (!StringUtils.hasText(question)) {
            throw new com.codecoachai.common.core.exception.BusinessException(
                    com.codecoachai.common.core.enums.ErrorCode.PARAM_ERROR, "question is required");
        }
        String keyword = question.length() > 20 ? question.substring(0, 20) : question;
        List<Map<String, Object>> refs = search(keyword, 5L).getData();
        String answer = refs.isEmpty()
                ? "未在个人知识库中检索到直接相关内容，请先上传或录入学习资料。"
                : "已根据个人知识库检索到 " + refs.size() + " 条相关资料，请优先复习引用片段并结合题目训练。";
        return Result.success(Map.of("question", question, "answer", answer, "references", refs, "generatedAt", LocalDateTime.now()));
    }

    private void indexChunks(Long userId, Long documentId, String content) {
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE document_id = ? AND user_id = ?", documentId, userId);
        List<String> chunks = split(content);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_chunk(document_id, user_id, chunk_index, content_text, token_count, embedding_hash, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, ?, SHA2(?, 256), NOW(), NOW(), 0)
                    """, documentId, userId, i + 1, chunk, chunk.length(), chunk);
        }
    }

    private List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        String normalized = content.trim();
        for (int start = 0; start < normalized.length(); start += 1200) {
            chunks.add(normalized.substring(start, Math.min(start + 1200, normalized.length())));
        }
        return chunks;
    }

    private Map<String, Object> getDocument(Long userId, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, user_id, file_id, title, source_type, status, content_text, created_at, updated_at
                FROM knowledge_document
                WHERE deleted = 0 AND user_id = ? AND id = ?
                """, userId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    @Data
    public static class KnowledgeDocumentCreateDTO {
        private Long fileId;
        private String title;
        private String sourceType;
        private String content;
    }

    @Data
    public static class KnowledgeAskDTO {
        private String question;
    }
}
