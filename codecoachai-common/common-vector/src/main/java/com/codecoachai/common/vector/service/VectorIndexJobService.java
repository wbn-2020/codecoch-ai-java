package com.codecoachai.common.vector.service;

import com.codecoachai.common.core.domain.PageResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class VectorIndexJobService {

    private static final DateTimeFormatter JOB_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final JdbcTemplate jdbcTemplate;

    public Long start(String jobType, String scopeType, String scopeId, Integer requestedCount) {
        String normalizedJobType = normalize(jobType, "UNKNOWN_JOB");
        String jobNo = normalizedJobType + "-" + JOB_NO_FORMATTER.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        try {
            jdbcTemplate.update("""
                    INSERT INTO vector_index_job(job_no, job_type, scope_type, scope_id, status, requested_count,
                                                 total_count, success_count, failed_count, vector_updated, vector_deleted,
                                                 started_at, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, 'RUNNING', ?, 0, 0, 0, 0, 0, NOW(), NOW(), NOW(), 0)
                    """, jobNo, normalizedJobType, normalizeNullable(scopeType), normalizeNullable(scopeId), requestedCount);
            return jdbcTemplate.queryForObject("SELECT id FROM vector_index_job WHERE job_no = ? AND deleted = 0",
                    Long.class, jobNo);
        } catch (DataAccessException ex) {
            log.warn("Vector index job start record failed jobType={}", normalizedJobType, ex);
            return null;
        }
    }

    public void finish(Long jobId, String status, Map<String, Object> result,
                       long totalCount, long successCount, long failedCount,
                       long vectorUpdated, long vectorDeleted, String lastError) {
        finish(jobId, status, totalCount, successCount, failedCount, vectorUpdated, vectorDeleted,
                firstText(lastError, firstError(result == null ? null : result.get("errors"))));
    }

    public void finish(Long jobId, String status, long totalCount, long successCount, long failedCount,
                       long vectorUpdated, long vectorDeleted, String lastError) {
        if (jobId == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE vector_index_job
                    SET status = ?, total_count = ?, success_count = ?, failed_count = ?,
                        vector_updated = ?, vector_deleted = ?, finished_at = NOW(),
                        duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000,
                        last_error = ?, updated_at = NOW()
                    WHERE id = ? AND deleted = 0
                    """, normalize(status, "UNKNOWN"), totalCount, successCount, failedCount,
                    vectorUpdated, vectorDeleted, truncateText(lastError, 1000), jobId);
        } catch (DataAccessException ex) {
            log.warn("Vector index job finish record failed jobId={}", jobId, ex);
        }
    }

    public void fail(Long jobId, Exception ex) {
        finish(jobId, "FAILED", 0L, 0L, 1L, 0L, 0L,
                firstText(ex == null ? null : ex.getMessage(), "unknown error"));
    }

    public void attach(Map<String, Object> result, Long jobId) {
        if (result != null && jobId != null) {
            result.put("jobId", jobId);
        }
    }

    public PageResult<Map<String, Object>> page(Long jobId, String jobType, String scopeType, String status,
                                                Long pageNo, Long pageSize) {
        long actualPageNo = pageNo == null || pageNo <= 0 ? 1L : pageNo;
        long actualPageSize = pageSize == null || pageSize <= 0 ? 10L : Math.min(pageSize, 100L);
        long offset = (actualPageNo - 1) * actualPageSize;
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        where.add("deleted = 0");
        if (jobId != null && jobId > 0) {
            where.add("id = ?");
            args.add(jobId);
        }
        if (jobType != null && !jobType.isBlank()) {
            where.add("job_type = ?");
            args.add(jobType.trim().toUpperCase());
        }
        if (scopeType != null && !scopeType.isBlank() && !"ALL".equalsIgnoreCase(scopeType)) {
            where.add("scope_type = ?");
            args.add(scopeType.trim().toUpperCase());
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            where.add("status = ?");
            args.add(status.trim().toUpperCase());
        }
        String whereSql = String.join(" AND ", where);
        try {
            Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM vector_index_job WHERE " + whereSql,
                    Long.class, args.toArray());
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(actualPageSize);
            pageArgs.add(offset);
            List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                    SELECT id,
                           job_no AS jobNo,
                           job_type AS jobType,
                           scope_type AS scopeType,
                           scope_id AS scopeId,
                           status,
                           requested_count AS requestedCount,
                           total_count AS totalCount,
                           success_count AS successCount,
                           failed_count AS failedCount,
                           vector_updated AS vectorUpdated,
                           vector_deleted AS vectorDeleted,
                           started_at AS startedAt,
                           finished_at AS finishedAt,
                           duration_ms AS durationMs,
                           last_error AS lastError,
                           created_at AS createdAt,
                           updated_at AS updatedAt
                    FROM vector_index_job
                    WHERE %s
                    ORDER BY created_at DESC, id DESC
                    LIMIT ? OFFSET ?
                    """.formatted(whereSql), pageArgs.toArray());
            return PageResult.of(records, total == null ? 0L : total, actualPageNo, actualPageSize);
        } catch (DataAccessException ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("errorMessage", "vector_index_job is not available: "
                    + firstText(ex.getMessage(), ex.getClass().getSimpleName()));
            return PageResult.of(List.of(error), 1L, actualPageNo, actualPageSize);
        }
    }

    private String firstError(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : String.valueOf(first);
        }
        return null;
    }

    private String normalize(String value, String fallback) {
        return firstText(value, fallback).trim().toUpperCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String truncateText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
