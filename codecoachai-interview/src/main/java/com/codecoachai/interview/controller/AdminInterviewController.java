package com.codecoachai.interview.controller;

import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminInterviewController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/admin/interviews")
    public Result<PageResult<Map<String, Object>>> interviews(@RequestParam(required = false) Long pageNo,
                                                              @RequestParam(required = false) Long pageSize,
                                                              @RequestParam(required = false) Long userId,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String reportStatus,
                                                              @RequestParam(required = false) String keyword) {
        SecurityAssert.requireAdmin();
        long pn = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        QueryParts parts = interviewWhere(userId, status, reportStatus, keyword);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM interview_session WHERE " + parts.where(), Long.class, parts.args());
        Object[] args = append(parts.args(), ps, (pn - 1) * ps);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, user_id, resume_id, target_job_id, skill_profile_id, match_report_id, title, mode,
                       target_position, difficulty, status, report_status, total_score, start_time, end_time, updated_at
                FROM interview_session
                WHERE %s
                ORDER BY updated_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(parts.where()), args);
        return Result.success(PageResult.of(rows, total == null ? 0 : total, pn, ps));
    }

    @GetMapping("/admin/interviews/{id}")
    public Result<Map<String, Object>> interviewDetail(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM interview_session WHERE deleted = 0 AND id = ?", id);
        return Result.success(rows.isEmpty() ? null : rows.get(0));
    }

    @GetMapping("/admin/interview-reports")
    public Result<PageResult<Map<String, Object>>> reports(@RequestParam(required = false) Long pageNo,
                                                           @RequestParam(required = false) Long pageSize,
                                                           @RequestParam(required = false) Long userId,
                                                           @RequestParam(required = false) String status) {
        SecurityAssert.requireAdmin();
        long pn = pageNo == null || pageNo < 1 ? 1 : pageNo;
        long ps = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        QueryParts parts = reportWhere(userId, status);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM interview_report r WHERE " + parts.where(), Long.class, parts.args());
        Object[] args = append(parts.args(), ps, (pn - 1) * ps);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, r.session_id, r.user_id, s.target_job_id, s.skill_profile_id, s.match_report_id,
                       r.status, r.total_score, r.summary, r.generated_at, r.created_at, r.updated_at, r.failure_reason
                FROM interview_report r
                LEFT JOIN interview_session s ON s.id = r.session_id
                WHERE %s
                ORDER BY r.updated_at DESC, r.id DESC
                LIMIT ? OFFSET ?
                """.formatted(parts.where()), args);
        return Result.success(PageResult.of(rows, total == null ? 0 : total, pn, ps));
    }

    @GetMapping("/admin/interview-reports/{id}")
    public Result<Map<String, Object>> reportDetail(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.*, s.target_job_id, s.skill_profile_id, s.match_report_id, s.title interview_title
                FROM interview_report r
                LEFT JOIN interview_session s ON s.id = r.session_id
                WHERE r.deleted = 0 AND r.id = ?
                """, id);
        return Result.success(rows.isEmpty() ? null : rows.get(0));
    }

    private QueryParts interviewWhere(Long userId, String status, String reportStatus, String keyword) {
        StringBuilder where = new StringBuilder("deleted = 0");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (userId != null) {
            where.append(" AND user_id = ?");
            args.add(userId);
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            args.add(status);
        }
        if (StringUtils.hasText(reportStatus)) {
            where.append(" AND report_status = ?");
            args.add(reportStatus);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (title LIKE ? OR target_position LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        return new QueryParts(where.toString(), args.toArray());
    }

    private QueryParts reportWhere(Long userId, String status) {
        StringBuilder where = new StringBuilder("r.deleted = 0");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (userId != null) {
            where.append(" AND r.user_id = ?");
            args.add(userId);
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        return new QueryParts(where.toString(), args.toArray());
    }

    private Object[] append(Object[] base, Object... tail) {
        Object[] result = java.util.Arrays.copyOf(base, base.length + tail.length);
        System.arraycopy(tail, 0, result, base.length, tail.length);
        return result;
    }

    private record QueryParts(String where, Object[] args) {
    }
}
