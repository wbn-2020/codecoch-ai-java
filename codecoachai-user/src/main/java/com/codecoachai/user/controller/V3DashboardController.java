package com.codecoachai.user.controller;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.user.domain.vo.V3DashboardVO;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dashboard/v3")
public class V3DashboardController {

    private final JdbcTemplate jdbcTemplate;
    private final ThreadLocal<List<String>> governanceTips = ThreadLocal.withInitial(ArrayList::new);

    @GetMapping("/overview")
    public Result<V3DashboardVO> overview() {
        Long userId = SecurityAssert.requireLoginUserId();
        governanceTips.get().clear();
        V3DashboardVO vo = new V3DashboardVO();
        try {
            V3DashboardVO.TargetJobCardVO targetJob = currentTargetJob(userId);
            vo.setCurrentTargetJob(targetJob);
            Long targetJobId = targetJob == null ? null : targetJob.getId();
            vo.setLatestMatch(latestMatch(userId, targetJobId));
            vo.setSkillProfile(skillProfile(userId, targetJobId));
            vo.setStudyProgress(studyProgress(userId, targetJobId));
            vo.setRecommendedQuestions(recommendedQuestions(userId, targetJobId));
            vo.setRecentInterview(recentInterview(userId, targetJobId));
            vo.setRecentReport(recentReport(userId, targetJobId));
            vo.setTrainingTrend(trainingTrend(userId));
            vo.setNextActions(nextActions(vo));
            vo.setDegraded(!governanceTips.get().isEmpty());
            vo.setGovernanceTips(List.copyOf(governanceTips.get()));
            vo.setGeneratedAt(LocalDateTime.now());
            return Result.success(vo);
        } finally {
            governanceTips.remove();
        }
    }

    @GetMapping("/skill-radar")
    public Result<List<Map<String, Object>>> skillRadar() {
        Long userId = SecurityAssert.requireLoginUserId();
        V3DashboardVO.TargetJobCardVO targetJob = currentTargetJob(userId);
        V3DashboardVO.SkillProfileSummaryVO profile = skillProfile(userId, targetJob == null ? null : targetJob.getId());
        return Result.success(profile == null ? List.of() : profile.getRadar());
    }

    @GetMapping("/next-actions")
    public Result<List<V3DashboardVO.NextActionVO>> nextActions() {
        return Result.success(overview().getData().getNextActions());
    }

    @GetMapping("/training-trend")
    public Result<List<V3DashboardVO.TrendItemVO>> trainingTrend() {
        return Result.success(trainingTrend(SecurityAssert.requireLoginUserId()));
    }

    private V3DashboardVO.TargetJobCardVO currentTargetJob(Long userId) {
        if (!tableExists("target_job")) {
            return null;
        }
        String currentColumn = columnExists("target_job", "is_current") ? "is_current" : "current_flag";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, job_title, company_name, job_level, status, %s AS is_current, updated_at
                FROM target_job
                WHERE deleted = 0 AND user_id = ?
                ORDER BY %s DESC, updated_at DESC, id DESC
                LIMIT 1
                """.formatted(currentColumn, currentColumn), userId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        V3DashboardVO.TargetJobCardVO vo = new V3DashboardVO.TargetJobCardVO();
        vo.setId(longValue(row.get("id")));
        vo.setJobTitle(stringValue(row.get("job_title")));
        vo.setCompanyName(stringValue(row.get("company_name")));
        vo.setJobLevel(stringValue(row.get("job_level")));
        vo.setStatus(stringValue(row.get("status")));
        vo.setCurrent(Boolean.TRUE.equals(row.get("is_current")) || Integer.valueOf(1).equals(row.get("is_current")));
        vo.setUpdatedAt(dateTime(row.get("updated_at")));
        return vo;
    }

    private V3DashboardVO.MatchSummaryVO latestMatch(Long userId, Long targetJobId) {
        if (!tableExists("resume_job_match_report")) {
            return null;
        }
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String targetFilter = "";
        if (targetJobId != null) {
            targetFilter = " AND target_job_id = ?";
            args.add(targetJobId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, resume_id, target_job_id, overall_score, status, summary, updated_at
                FROM resume_job_match_report
                WHERE deleted = 0 AND user_id = ?%s
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """.formatted(targetFilter), args.toArray());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        V3DashboardVO.MatchSummaryVO vo = new V3DashboardVO.MatchSummaryVO();
        vo.setReportId(longValue(row.get("id")));
        vo.setResumeId(longValue(row.get("resume_id")));
        vo.setTargetJobId(longValue(row.get("target_job_id")));
        vo.setOverallScore(intValue(row.get("overall_score")));
        vo.setStatus(stringValue(row.get("status")));
        vo.setSummary(stringValue(row.get("summary")));
        vo.setUpdatedAt(dateTime(row.get("updated_at")));
        return vo;
    }

    private V3DashboardVO.SkillProfileSummaryVO skillProfile(Long userId, Long targetJobId) {
        if (!tableExists("skill_profile")) {
            return null;
        }
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String targetFilter = "";
        if (targetJobId != null) {
            targetFilter = " AND target_job_id = ?";
            args.add(targetJobId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, target_job_id, overall_score, overall_level, summary
                FROM skill_profile
                WHERE deleted = 0 AND user_id = ?%s AND status = 'SUCCESS'
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """.formatted(targetFilter), args.toArray());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        V3DashboardVO.SkillProfileSummaryVO vo = new V3DashboardVO.SkillProfileSummaryVO();
        vo.setProfileId(longValue(row.get("id")));
        vo.setTargetJobId(longValue(row.get("target_job_id")));
        vo.setOverallScore(intValue(row.get("overall_score")));
        vo.setOverallLevel(intValue(row.get("overall_level")));
        vo.setSummary(stringValue(row.get("summary")));
        vo.setGaps(skillGaps(vo.getProfileId(), userId));
        vo.setRadar(toRadar(vo.getGaps()));
        return vo;
    }

    private List<Map<String, Object>> skillGaps(Long profileId, Long userId) {
        if (profileId == null || !tableExists("skill_gap_item")) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT id, skill_name, category, target_level, current_level, gap_level, severity, priority, source_type
                FROM skill_gap_item
                WHERE deleted = 0 AND profile_id = ? AND user_id = ?
                ORDER BY priority ASC, id ASC
                LIMIT 12
                """, profileId, userId);
    }

    private List<Map<String, Object>> toRadar(List<Map<String, Object>> gaps) {
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (Map<String, Object> gap : gaps) {
            String category = StringUtils.hasText(stringValue(gap.get("category"))) ? stringValue(gap.get("category")) : "GENERAL";
            long[] stat = grouped.computeIfAbsent(category, key -> new long[]{0, 0});
            stat[0] += Math.max(0, 5 - nullToZero(intValue(gap.get("gap_level"))));
            stat[1]++;
        }
        List<Map<String, Object>> radar = new ArrayList<>();
        grouped.forEach((category, stat) -> radar.add(Map.of("category", category, "score", stat[1] == 0 ? 0 : stat[0] / stat[1])));
        return radar;
    }

    private V3DashboardVO.StudyProgressVO studyProgress(Long userId, Long targetJobId) {
        V3DashboardVO.StudyProgressVO vo = new V3DashboardVO.StudyProgressVO();
        if (!tableExists("study_plan")) {
            vo.setTotalTasks(0L);
            vo.setCompletedTasks(0L);
            vo.setCompletionRate(0);
            return vo;
        }
        Long planId = queryLong("""
                SELECT id FROM study_plan
                WHERE deleted = 0 AND user_id = ? AND (? IS NULL OR target_job_id = ?)
                ORDER BY updated_at DESC, id DESC LIMIT 1
                """, userId, targetJobId, targetJobId);
        vo.setActivePlanId(planId);
        if (planId == null || !tableExists("study_task")) {
            vo.setTotalTasks(0L);
            vo.setCompletedTasks(0L);
            vo.setCompletionRate(0);
            return vo;
        }
        String planColumn = columnExists("study_task", "study_plan_id") ? "study_plan_id" : "plan_id";
        Long total = queryLong("SELECT COUNT(1) FROM study_task WHERE deleted = 0 AND " + planColumn + " = ? AND user_id = ?", planId, userId);
        Long completed = queryLong("""
                SELECT COUNT(1) FROM study_task
                WHERE deleted = 0 AND %s = ? AND user_id = ? AND task_status IN ('DONE','COMPLETED')
                """.formatted(planColumn), planId, userId);
        vo.setTotalTasks(total == null ? 0L : total);
        vo.setCompletedTasks(completed == null ? 0L : completed);
        vo.setCompletionRate(vo.getTotalTasks() == 0 ? 0 : (int) (vo.getCompletedTasks() * 100 / vo.getTotalTasks()));
        return vo;
    }

    private V3DashboardVO.RecommendedQuestionsVO recommendedQuestions(Long userId, Long targetJobId) {
        if (!tableExists("question_recommendation_batch")) {
            return null;
        }
        String targetColumn = columnExists("question_recommendation_batch", "job_target_id")
                ? "job_target_id"
                : (columnExists("question_recommendation_batch", "target_job_id") ? "target_job_id" : null);
        String targetFilter = "";
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (targetJobId != null && targetColumn != null) {
            targetFilter = " AND " + targetColumn + " = ?";
            args.add(targetJobId);
        }
        String targetSelect = targetColumn == null ? "NULL" : targetColumn;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, %s AS target_job_id, source_type, source_id, match_report_id,
                       skill_profile_id, study_plan_id, status, question_count, updated_at
                FROM question_recommendation_batch
                WHERE deleted = 0 AND user_id = ?%s
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """.formatted(targetSelect, targetFilter), args.toArray());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Long batchId = longValue(row.get("id"));
        Long itemCount = tableExists("question_recommendation_item")
                ? queryLong("""
                        SELECT COUNT(1)
                        FROM question_recommendation_item
                        WHERE deleted = 0 AND batch_id = ?
                        """, batchId)
                : longValue(row.get("question_count"));
        Long canPracticeCount = tableExists("question_recommendation_item")
                ? queryLong("""
                        SELECT COUNT(1)
                        FROM question_recommendation_item
                        WHERE deleted = 0 AND batch_id = ?
                          AND question_id IS NOT NULL
                          %s
                        """.formatted(columnExists("question_recommendation_item", "match_status")
                                ? "AND (match_status IS NULL OR match_status <> 'UNMATCHED_DRAFT')" : ""), batchId)
                : 0L;
        V3DashboardVO.RecommendedQuestionsVO vo = new V3DashboardVO.RecommendedQuestionsVO();
        vo.setBatchId(batchId);
        vo.setTargetJobId(longValue(row.get("target_job_id")));
        vo.setSourceType(stringValue(row.get("source_type")));
        vo.setSourceId(longValue(row.get("source_id")));
        vo.setMatchReportId(longValue(row.get("match_report_id")));
        vo.setSkillProfileId(longValue(row.get("skill_profile_id")));
        vo.setStudyPlanId(longValue(row.get("study_plan_id")));
        vo.setStatus(stringValue(row.get("status")));
        vo.setQuestionCount(itemCount == null || itemCount == 0 ? longValue(row.get("question_count")) : itemCount);
        vo.setCanPracticeCount(canPracticeCount == null ? 0L : canPracticeCount);
        vo.setPendingPracticeCount(Math.max(0L, (vo.getQuestionCount() == null ? 0L : vo.getQuestionCount()) - vo.getCanPracticeCount()));
        vo.setUpdatedAt(dateTime(row.get("updated_at")));
        return vo;
    }

    private V3DashboardVO.RecentInterviewVO recentInterview(Long userId, Long targetJobId) {
        if (!tableExists("interview_session")) {
            return null;
        }
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String targetFilter = "";
        if (targetJobId != null && columnExists("interview_session", "target_job_id")) {
            targetFilter = " AND target_job_id = ?";
            args.add(targetJobId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, title, status, report_status, updated_at
                FROM interview_session
                WHERE deleted = 0 AND user_id = ?%s
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """.formatted(targetFilter), args.toArray());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        V3DashboardVO.RecentInterviewVO vo = new V3DashboardVO.RecentInterviewVO();
        vo.setInterviewId(longValue(row.get("id")));
        vo.setTitle(stringValue(row.get("title")));
        vo.setStatus(stringValue(row.get("status")));
        vo.setReportStatus(stringValue(row.get("report_status")));
        vo.setUpdatedAt(dateTime(row.get("updated_at")));
        return vo;
    }

    private V3DashboardVO.RecentReportVO recentReport(Long userId, Long targetJobId) {
        if (!tableExists("interview_report") || !tableExists("interview_session")) {
            return null;
        }
        String weakPointsExpr = columnExists("interview_report", "weak_points") ? "r.weak_points" : "NULL";
        String suggestionsExpr = columnExists("interview_report", "suggestions")
                ? "r.suggestions"
                : (columnExists("interview_report", "review_suggestions") ? "r.review_suggestions" : "NULL");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        String targetFilter = "";
        if (targetJobId != null && columnExists("interview_session", "target_job_id")) {
            targetFilter = " AND s.target_job_id = ?";
            args.add(targetJobId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, r.session_id, r.status, r.total_score, %s AS weak_points, %s AS suggestions, r.generated_at, r.updated_at
                FROM interview_report r
                JOIN interview_session s ON s.id = r.session_id AND s.deleted = 0
                WHERE r.deleted = 0 AND s.user_id = ?%s
                ORDER BY COALESCE(r.generated_at, r.updated_at) DESC, r.id DESC
                LIMIT 1
                """.formatted(weakPointsExpr, suggestionsExpr, targetFilter), args.toArray());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        V3DashboardVO.RecentReportVO vo = new V3DashboardVO.RecentReportVO();
        vo.setReportId(longValue(row.get("id")));
        vo.setInterviewId(longValue(row.get("session_id")));
        vo.setStatus(stringValue(row.get("status")));
        vo.setTotalScore(intValue(row.get("total_score")));
        vo.setWeakPoints(splitLines(stringValue(row.get("weak_points"))));
        vo.setSuggestions(splitLines(stringValue(row.get("suggestions"))));
        vo.setGeneratedAt(dateTime(row.get("generated_at")) == null ? dateTime(row.get("updated_at")) : dateTime(row.get("generated_at")));
        return vo;
    }

    private List<V3DashboardVO.TrendItemVO> trainingTrend(Long userId) {
        Map<LocalDate, V3DashboardVO.TrendItemVO> map = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            V3DashboardVO.TrendItemVO item = new V3DashboardVO.TrendItemVO();
            item.setDate(date);
            item.setInterviewCount(0L);
            item.setCompletedCount(0L);
            item.setAverageScore(0L);
            map.put(date, item);
        }
        if (!tableExists("interview_session")) {
            return new ArrayList<>(map.values());
        }
        jdbcTemplate.query("""
                SELECT DATE(created_at) stat_date, COUNT(1) total,
                       SUM(CASE WHEN status IN ('COMPLETED','FINISHED') THEN 1 ELSE 0 END) completed,
                       AVG(CASE WHEN total_score > 0 THEN total_score ELSE NULL END) avg_score
                FROM interview_session
                WHERE deleted = 0 AND user_id = ? AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
                GROUP BY DATE(created_at)
                """, rs -> {
            LocalDate date = rs.getDate("stat_date").toLocalDate();
            V3DashboardVO.TrendItemVO item = map.get(date);
            if (item != null) {
                item.setInterviewCount(rs.getLong("total"));
                item.setCompletedCount(rs.getLong("completed"));
                item.setAverageScore(rs.getLong("avg_score"));
            }
        }, userId);
        return new ArrayList<>(map.values());
    }

    private List<V3DashboardVO.NextActionVO> nextActions(V3DashboardVO vo) {
        List<V3DashboardVO.NextActionVO> actions = new ArrayList<>();
        appendReportActions(actions, vo);
        if (vo.getCurrentTargetJob() == null) {
            actions.add(action("CREATE_TARGET_JOB", "创建目标岗位", "先录入目标岗位和 JD，才能开启 V3 闭环。", "/job-targets", 1));
        } else if (vo.getLatestMatch() == null) {
            actions.add(action("RUN_MATCH", "生成简历-JD 匹配报告", "用当前简历和目标 JD 生成匹配度与差距。", "/resume-job-match", 1));
        } else if (vo.getSkillProfile() == null) {
            actions.add(action("GENERATE_PROFILE", "生成能力画像", "把匹配报告转成可训练的能力短板。", "/skill-profiles", 1));
        } else if (vo.getStudyProgress() == null || vo.getStudyProgress().getActivePlanId() == null) {
            actions.add(action("GENERATE_PLAN", "生成学习计划", "基于能力短板安排每日训练任务。", "/study-plans", 1));
        } else {
            actions.add(action("START_INTERVIEW", "开始目标岗位模拟面试", "用当前 JD 和短板上下文进行针对性面试。", "/interviews/create?source=job-target", 1));
        }
        actions.add(action("REVIEW_GAPS", "复盘能力短板", "查看最新能力画像和面试报告回流弱项。", "/skill-profiles", 2));
        return actions;
    }

    private void appendReportActions(List<V3DashboardVO.NextActionVO> actions, V3DashboardVO vo) {
        V3DashboardVO.RecentReportVO report = vo.getRecentReport();
        if (report != null) {
            String status = StringUtils.hasText(report.getStatus()) ? report.getStatus().toUpperCase() : "";
            if ("FAILED".equals(status)) {
                actions.add(action("RETRY_INTERVIEW_REPORT", "重新生成面试报告", "最近一次面试报告生成失败，先恢复报告再进入复盘。",
                        "/interviews/" + report.getInterviewId() + "/report", 1));
                return;
            }
            if (report.getInterviewId() != null) {
                actions.add(action("REVIEW_INTERVIEW_REPORT", "查看最近面试报告", reportSummary(report),
                        "/interviews/" + report.getInterviewId() + "/report", 1));
            }
            if (!report.getWeakPoints().isEmpty() || !report.getSuggestions().isEmpty()) {
                actions.add(action("PRACTICE_REPORT_WEAKNESS", "按报告弱点继续练习", "围绕最近面试暴露的弱点进入推荐题训练。",
                        recommendationPath(vo), 2));
                actions.add(action("UPDATE_STUDY_PLAN_FROM_REPORT", "把报告建议纳入学习计划", "用报告弱点调整下一阶段学习任务。",
                        studyPlanPath(vo, report), 3));
            }
            return;
        }
        V3DashboardVO.RecentInterviewVO interview = vo.getRecentInterview();
        if (interview != null && StringUtils.hasText(interview.getReportStatus())) {
            String status = interview.getReportStatus().toUpperCase();
            if ("GENERATING".equals(status) || "REPORT_GENERATING".equals(status)) {
                actions.add(action("VIEW_REPORT_PROGRESS", "查看报告生成进度", "最近面试报告仍在生成中，可进入报告页查看状态。",
                        "/interviews/" + interview.getInterviewId() + "/report", 1));
            }
        }
    }

    private String reportSummary(V3DashboardVO.RecentReportVO report) {
        List<String> points = new ArrayList<>();
        points.addAll(report.getWeakPoints());
        points.addAll(report.getSuggestions());
        if (points.isEmpty()) {
            return "查看总分、薄弱点和后续建议。";
        }
        return String.join("；", points.stream().limit(2).toList());
    }

    private String recommendationPath(V3DashboardVO vo) {
        V3DashboardVO.RecommendedQuestionsVO recommendations = vo.getRecommendedQuestions();
        if (recommendations != null && recommendations.getBatchId() != null) {
            return "/questions/recommendations?batchId=" + recommendations.getBatchId()
                    + queryPart("sourceType", recommendations.getSourceType())
                    + queryPart("sourceId", recommendations.getSourceId());
        }
        if (vo.getStudyProgress() != null && vo.getStudyProgress().getActivePlanId() != null) {
            return "/questions/recommendations?source=studyPlan&sourceId=" + vo.getStudyProgress().getActivePlanId();
        }
        if (vo.getSkillProfile() != null && vo.getSkillProfile().getProfileId() != null) {
            return "/questions/recommendations?source=gap&sourceId=" + vo.getSkillProfile().getProfileId();
        }
        if (vo.getLatestMatch() != null && vo.getLatestMatch().getReportId() != null) {
            return "/questions/recommendations?source=matchReport&sourceId=" + vo.getLatestMatch().getReportId();
        }
        return "/questions/recommendations";
    }

    private String studyPlanPath(V3DashboardVO vo, V3DashboardVO.RecentReportVO report) {
        StringBuilder builder = new StringBuilder("/study-plans");
        List<String> params = new ArrayList<>();
        if (report.getReportId() != null) {
            params.add("fromReportId=" + report.getReportId());
        }
        if (vo.getCurrentTargetJob() != null && vo.getCurrentTargetJob().getId() != null) {
            params.add("targetJobId=" + vo.getCurrentTargetJob().getId());
        }
        if (params.isEmpty()) {
            return builder.toString();
        }
        return builder.append("?").append(String.join("&", params)).toString();
    }

    private String queryPart(String name, Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? "" : "&" + name + "=" + value;
    }

    private V3DashboardVO.NextActionVO action(String type, String title, String desc, String path, int priority) {
        V3DashboardVO.NextActionVO vo = new V3DashboardVO.NextActionVO();
        vo.setActionType(type);
        vo.setTitle(title);
        vo.setDescription(desc);
        vo.setTargetPath(path);
        vo.setPriority(priority);
        return vo;
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return values.isEmpty() ? null : values.get(0);
    }

    private boolean tableExists(String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Long.class, tableName);
            boolean exists = count != null && count > 0;
            if (!exists) {
                addGovernanceTip("degraded: missing table " + tableName + ", related dashboard block is unavailable");
            }
            return exists;
        } catch (DataAccessException ex) {
            addGovernanceTip("degraded: unable to inspect table " + tableName + ", " + ex.getClass().getSimpleName());
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Long.class, tableName, columnName);
            return count != null && count > 0;
        } catch (DataAccessException ex) {
            addGovernanceTip("degraded: unable to inspect column " + tableName + "." + columnName + ", " + ex.getClass().getSimpleName());
            return false;
        }
    }

    private void addGovernanceTip(String tip) {
        List<String> tips = governanceTips.get();
        if (!tips.contains(tip)) {
            tips.add(tip);
        }
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime dateTime(Object value) {
        return value instanceof Timestamp ts ? ts.toLocalDateTime() : null;
    }

    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(8)
                .toList();
    }
}
