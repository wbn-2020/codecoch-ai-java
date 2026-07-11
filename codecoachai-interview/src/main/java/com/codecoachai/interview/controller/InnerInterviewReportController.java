package com.codecoachai.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.enums.InterviewStatusEnum;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.domain.vo.InterviewReportAgentEvidenceVO;
import com.codecoachai.interview.domain.vo.InterviewWeaknessSummaryVO;
import com.codecoachai.interview.domain.vo.WeaknessInsightItemVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.service.impl.AgentBusinessActionNotifier;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/interviews")
public class InnerInterviewReportController {

    private static final DateTimeFormatter ES_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_SUMMARY_DAYS = 30;
    private static final int MAX_SUMMARY_DAYS = 90;
    private static final int TOP_WEAKNESS_LIMIT = 5;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewMqDispatcher interviewMqDispatcher;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final ObjectMapper objectMapper;

    @GetMapping("/{sessionId}/report-context")
    public Result<ReportContextVO> getReportContext(@PathVariable Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "session not found: " + sessionId);
        }

        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getCreatedAt)
                        .orderByAsc(InterviewMessage::getId));

        List<String> msgTexts = messages.stream()
                .map(m -> firstText(m.getQuestionContent(), m.getUserAnswer(), m.getAiComment(), m.getContent()))
                .filter(StringUtils::hasText)
                .toList();

        ReportContextVO vo = new ReportContextVO();
        vo.setSessionId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
        vo.setIndustryDirection(session.getIndustryDirection());
        vo.setIndustryContext(session.getIndustryContext());
        vo.setDifficulty(session.getDifficulty());
        vo.setMessages(msgTexts);
        return Result.success(vo);
    }

    @PostMapping("/{sessionId}/complete-report")
    public Result<Void> completeReport(@PathVariable Long sessionId, @RequestBody CompleteReportDTO dto) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "session not found: " + sessionId);
        }

        String status = StringUtils.hasText(dto.getReportStatus()) ? dto.getReportStatus() : "SUCCESS";
        boolean success = "SUCCESS".equalsIgnoreCase(status);
        LocalDateTime now = LocalDateTime.now();
        InterviewReport report = currentReport(sessionId);
        if (report == null && (dto.getReportId() != null || StringUtils.hasText(dto.getGenerationToken()))) {
            log.info("Ignore interview report callback without current report, sessionId={}, payloadReportId={}",
                    sessionId, dto.getReportId());
            return Result.success();
        }
        if (report != null && !matchesReportAttempt(report, dto.getReportId(), dto.getGenerationToken())) {
            log.info("Ignore stale interview report callback, sessionId={}, currentReportId={}, payloadReportId={}",
                    sessionId, report.getId(), dto.getReportId());
            return Result.success();
        }
        if (report != null && !ReportStatusEnum.GENERATING.name().equals(report.getStatus())) {
            log.info("Ignore duplicate interview report callback, sessionId={}, reportId={}, currentStatus={}",
                    sessionId, report.getId(), report.getStatus());
            return Result.success();
        }
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(sessionId);
            report.setUserId(session.getUserId());
        }

        report.setStatus(success ? ReportStatusEnum.GENERATED.name() : ReportStatusEnum.FAILED.name());
        if (StringUtils.hasText(dto.getGenerationToken())) {
            report.setGenerationToken(dto.getGenerationToken());
        }
        if (success) {
            applySuccessfulReportPayload(report, dto, now);
        }
        if (!success) {
            report.setTotalScore(null);
            report.setFailureReason(dto.getErrorMessage());
        }
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            LambdaUpdateWrapper<InterviewReport> reportUpdate = new LambdaUpdateWrapper<InterviewReport>()
                    .eq(InterviewReport::getId, report.getId())
                    .eq(InterviewReport::getSessionId, sessionId)
                    .eq(InterviewReport::getStatus, ReportStatusEnum.GENERATING.name())
                    .eq(InterviewReport::getDeleted, CommonConstants.NO);
            if (StringUtils.hasText(dto.getGenerationToken())) {
                reportUpdate.eq(InterviewReport::getGenerationToken, dto.getGenerationToken());
            } else {
                reportUpdate.isNull(InterviewReport::getGenerationToken);
            }
            if (reportMapper.update(report, reportUpdate) != 1) {
                log.info("Ignore stale interview report callback CAS, sessionId={}, reportId={}",
                        sessionId, report.getId());
                return Result.success();
            }
        }

        log.info("Interview report completed sessionId={} reportId={} status={}",
                sessionId, report.getId(), status);

        Integer completedScore = success ? firstNonNull(dto.getTotalScore(), report.getTotalScore()) : null;
        sessionMapper.update(null,
                new LambdaUpdateWrapper<InterviewSession>()
                        .eq(InterviewSession::getId, sessionId)
                        .set(InterviewSession::getStatus,
                                success ? InterviewStatusEnum.COMPLETED.name() : InterviewStatusEnum.FAILED.name())
                        .set(InterviewSession::getReportStatus,
                                success ? ReportStatusEnum.GENERATED.name() : ReportStatusEnum.FAILED.name())
                        .set(success, InterviewSession::getTotalScore, completedScore)
                        .set(!success, InterviewSession::getTotalScore, null)
                        .set(success, InterviewSession::getFailureReason, null)
                        .set(!success, InterviewSession::getFailureReason, dto.getErrorMessage())
                        .set(InterviewSession::getEndTime, now)
                        .set(InterviewSession::getUpdatedAt, now));

        if (success) {
            interviewMqDispatcher.dispatchInterviewSearchUpsert(sessionId, session.getUserId());
            completeAgentInterviewTask(session, report);
        }
        return Result.success();
    }

    @GetMapping("/reports/users/{userId}/{reportId}/agent-evidence")
    public Result<InterviewReportAgentEvidenceVO> getAgentEvidence(@PathVariable Long userId,
                                                                   @PathVariable Long reportId) {
        InterviewReport report = reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getId, reportId)
                .eq(InterviewReport::getUserId, userId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .last("limit 1"));
        if (report == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview report evidence not found");
        }
        if (!InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview report evidence is not trusted");
        }
        InterviewSession session = sessionMapper.selectById(report.getSessionId());
        if (session == null || !userId.equals(session.getUserId())
                || Integer.valueOf(CommonConstants.YES).equals(session.getDeleted())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "interview report session evidence not found");
        }
        InterviewReportAgentEvidenceVO vo = new InterviewReportAgentEvidenceVO();
        vo.setId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setSessionId(report.getSessionId());
        vo.setTargetJobId(session.getTargetJobId());
        vo.setStatus(report.getStatus());
        vo.setGeneratedAt(report.getGeneratedAt());
        vo.setCreatedAt(report.getCreatedAt());
        return Result.success(vo);
    }

    @GetMapping("/users/{userId}/weakness-summary")
    public Result<InterviewWeaknessSummaryVO> weaknessSummary(@PathVariable Long userId,
                                                              @RequestParam(required = false) Integer days) {
        int rangeDays = normalizeSummaryDays(days);
        LocalDateTime startTime = LocalDate.now().minusDays(rangeDays - 1L).atStartOfDay();

        Long interviewCount = sessionMapper.selectCount(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getDeleted, CommonConstants.NO)
                .ge(InterviewSession::getCreatedAt, startTime));
        List<InterviewReport> reports = reportMapper.selectList(summaryReportQuery(userId, startTime)
                .orderByDesc(InterviewReport::getGeneratedAt)
                .orderByDesc(InterviewReport::getId))
                .stream()
                .filter(InterviewReportTrustPolicy::isTrustedForFormalAction)
                .toList();

        InterviewWeaknessSummaryVO vo = new InterviewWeaknessSummaryVO();
        vo.setRangeDays(rangeDays);
        vo.setInterviewCount(safeCount(interviewCount));
        vo.setReportCount((long) reports.size());
        vo.setTopWeaknesses(buildTopWeaknesses(reports));
        return Result.success(vo);
    }

    private LambdaQueryWrapper<InterviewReport> summaryReportQuery(Long userId, LocalDateTime startTime) {
        return new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getUserId, userId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .eq(InterviewReport::getStatus, ReportStatusEnum.GENERATED.name())
                .and(wrapper -> wrapper
                        .ge(InterviewReport::getGeneratedAt, startTime)
                        .or(fallback -> fallback
                                .isNull(InterviewReport::getGeneratedAt)
                                .ge(InterviewReport::getCreatedAt, startTime)));
    }

    private List<WeaknessInsightItemVO> buildTopWeaknesses(List<InterviewReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, WeaknessCounter> counters = new LinkedHashMap<>();
        for (InterviewReport report : reports) {
            Set<String> reportWeaknesses = new LinkedHashSet<>(extractWeaknesses(report));
            for (String weakness : reportWeaknesses) {
                String key = normalizeWeaknessKey(weakness);
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                counters.computeIfAbsent(key, ignored -> new WeaknessCounter(weakness.trim()))
                        .increment(report);
            }
        }
        return counters.values().stream()
                .sorted(Comparator.comparingLong(WeaknessCounter::count).reversed())
                .limit(TOP_WEAKNESS_LIMIT)
                .map(WeaknessCounter::toVO)
                .toList();
    }

    private List<String> extractWeaknesses(InterviewReport report) {
        if (report == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        appendWeaknessValues(values, report.getWeakPoints());
        appendWeaknessValues(values, report.getMainProblems());
        appendWeaknessValues(values, report.getWeaknesses());
        appendReportContentWeaknessValues(values, report.getReportContent());
        return values.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void appendReportContentWeaknessValues(List<String> values, String reportContent) {
        if (!StringUtils.hasText(reportContent)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(reportContent);
            appendJsonNodeValues(values, root.get("weakKnowledgePoints"));
            appendJsonNodeValues(values, root.get("weakPoints"));
            appendJsonNodeValues(values, root.get("mainProblems"));
            appendJsonNodeValues(values, root.get("weaknesses"));
        } catch (Exception ex) {
            log.debug("Ignore non-json interview report content when extracting weakness summary");
        }
    }

    private void appendWeaknessValues(List<String> values, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(rawValue);
            appendJsonNodeValues(values, node);
            return;
        } catch (Exception ignored) {
            // Fall through to conservative delimiter splitting.
        }
        appendPlainTextValues(values, rawValue);
    }

    private void appendJsonNodeValues(List<String> values, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> appendJsonNodeValues(values, item));
            return;
        }
        if (node.isObject()) {
            appendJsonNodeValues(values, node.get("name"));
            appendJsonNodeValues(values, node.get("skillName"));
            appendJsonNodeValues(values, node.get("knowledgePoint"));
            appendJsonNodeValues(values, node.get("title"));
            return;
        }
        if (node.isTextual()) {
            appendPlainTextValues(values, node.asText());
        }
    }

    private void appendPlainTextValues(List<String> values, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String value : text.split("[,，;；、\\n\\r]+")) {
            String normalized = cleanWeaknessName(value);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
    }

    private String cleanWeaknessName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim()
                .replaceFirst("^[\\-\\*\\d\\.、\\)）\\s]+", "")
                .trim();
    }

    private String normalizeWeaknessKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private int normalizeSummaryDays(Integer days) {
        if (days == null) {
            return DEFAULT_SUMMARY_DAYS;
        }
        if (days <= 7) {
            return 7;
        }
        if (days <= DEFAULT_SUMMARY_DAYS) {
            return DEFAULT_SUMMARY_DAYS;
        }
        return MAX_SUMMARY_DAYS;
    }

    private Long safeCount(Long value) {
        return value == null ? 0L : value;
    }

    private void completeAgentInterviewTask(InterviewSession session, InterviewReport report) {
        if (session == null || !InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            return;
        }
        agentBusinessActionNotifier.completeInterviewReport(session.getUserId(), session.getTargetJobId(),
                report.getId());
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @Data
    public static class ReportContextVO {
        private Long sessionId;
        private Long userId;
        private String mode;
        private String targetPosition;
        private String experienceLevel;
        private String industryDirection;
        private String industryContext;
        private String difficulty;
        private String resumeContent;
        private String projectContent;
        private List<String> messages;
    }

    @Data
    public static class CompleteReportDTO {
        private Long reportId;
        private String generationToken;
        private String reportJson;
        private Integer totalScore;
        private String reportStatus;
        private String errorMessage;
    }

    @GetMapping("/{id}/search-doc")
    public Result<Map<String, Object>> getSearchDoc(@PathVariable Long id) {
        InterviewSession session = sessionMapper.selectById(id);
        if (session == null) {
            return Result.success(null);
        }
        InterviewReport report = currentReport(id);

        Map<String, Object> doc = new HashMap<>();
        doc.put("docId", String.valueOf(session.getId()));
        doc.put("id", session.getId());
        doc.put("sessionId", session.getId());
        doc.put("reportId", report == null ? null : report.getId());
        doc.put("userId", String.valueOf(session.getUserId()));
        doc.put("mode", session.getMode());
        doc.put("title", session.getTitle());
        doc.put("targetPosition", session.getTargetPosition());
        doc.put("experienceLevel", session.getExperienceLevel());
        doc.put("industryDirection", session.getIndustryDirection());
        doc.put("difficulty", session.getDifficulty());
        doc.put("status", session.getStatus());
        doc.put("reportStatus", report == null ? session.getReportStatus() : firstText(report.getStatus(), session.getReportStatus()));
        doc.put("totalScore", report == null ? session.getTotalScore() : firstNonNull(report.getTotalScore(), session.getTotalScore()));
        doc.put("interviewerStyle", session.getInterviewerStyle());
        doc.put("summary", report == null ? null : report.getSummary());
        doc.put("weakPoints", report == null ? null : firstText(report.getWeakPoints(), report.getWeaknesses(), report.getMainProblems()));
        doc.put("strengths", report == null ? null : report.getStrengths());
        doc.put("mainProblems", report == null ? null : report.getMainProblems());
        doc.put("projectProblems", report == null ? null : report.getProjectProblems());
        doc.put("reviewSuggestions", report == null ? null : firstText(report.getReviewSuggestions(), report.getSuggestions()));
        doc.put("suggestions", report == null ? null : report.getSuggestions());
        doc.put("reportContent", report == null ? null : report.getReportContent());
        doc.put("startTime", formatDateTime(session.getStartTime()));
        doc.put("endTime", formatDateTime(session.getEndTime()));
        doc.put("createdAt", formatDateTime(session.getCreatedAt()));
        doc.put("generatedAt", report == null ? null : formatDateTime(report.getGeneratedAt()));
        doc.put("syncedAt", java.time.Instant.now().toString());
        return Result.success(doc);
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private void applySuccessfulReportPayload(InterviewReport report, CompleteReportDTO dto, LocalDateTime now) {
        GenerateReportVO payload = parseCompletedReport(dto.getReportJson());
        report.setTotalScore(firstNonNull(dto.getTotalScore(), payload == null ? null : payload.getTotalScore()));
        report.setFailureReason(null);
        report.setGeneratedAt(now);
        clearStructuredReportFields(report);
        if (payload == null) {
            report.setReportContent(StringUtils.hasText(dto.getReportJson()) ? dto.getReportJson() : null);
            return;
        }
        report.setSummary(payload.getSummary());
        report.setStageScores(payload.getStageScores());
        report.setWeakPoints(payload.getWeakPoints());
        report.setStrengths(payload.getStrengths());
        report.setWeaknesses(payload.getWeaknesses());
        report.setMainProblems(firstText(payload.getMainProblems(), payload.getWeaknesses()));
        report.setProjectProblems(payload.getProjectProblems());
        report.setReviewSuggestions(firstText(payload.getReviewSuggestions(), payload.getSuggestions()));
        report.setRecommendedQuestions(payload.getRecommendedQuestions());
        report.setQaReview(payload.getQaReview());
        report.setRubricScores(payload.getRubricScores());
        report.setFollowUpTree(payload.getFollowUpTree());
        report.setAdviceEvidence(payload.getAdviceEvidence());
        report.setAbilityProfileUpdates(payload.getAbilityProfileUpdates());
        report.setSuggestions(payload.getSuggestions());
        report.setReportContent(firstText(payload.getReportContent(), payload.getSummary(), dto.getReportJson()));
    }

    private void clearStructuredReportFields(InterviewReport report) {
        report.setStageScores(null);
        report.setWeakPoints(null);
        report.setSummary(null);
        report.setStrengths(null);
        report.setWeaknesses(null);
        report.setMainProblems(null);
        report.setProjectProblems(null);
        report.setReviewSuggestions(null);
        report.setRecommendedQuestions(null);
        report.setQaReview(null);
        report.setRubricScores(null);
        report.setFollowUpTree(null);
        report.setAdviceEvidence(null);
        report.setAbilityProfileUpdates(null);
        report.setSuggestions(null);
        report.setReportContent(null);
    }

    private GenerateReportVO parseCompletedReport(String reportJson) {
        if (!StringUtils.hasText(reportJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(reportJson, GenerateReportVO.class);
        } catch (Exception ex) {
            log.warn("Failed to parse completed interview report payload", ex);
            return null;
        }
    }

    private InterviewReport currentReport(Long sessionId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId)
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));
    }

    private boolean matchesReportAttempt(InterviewReport report, Long reportId, String generationToken) {
        if (report == null) {
            return false;
        }
        if (reportId != null && !reportId.equals(report.getId())) {
            return false;
        }
        if (StringUtils.hasText(report.getGenerationToken())) {
            return report.getGenerationToken().equals(generationToken);
        }
        return !StringUtils.hasText(generationToken);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ES_DATE_FORMAT.format(value);
    }

    private static final class WeaknessCounter {

        private final String name;
        private long count;
        private String evidence;

        private WeaknessCounter(String name) {
            this.name = name;
        }

        private void increment(InterviewReport report) {
            count++;
            if (!StringUtils.hasText(evidence) && report != null && report.getId() != null) {
                evidence = "interviewReport#" + report.getId();
            }
        }

        private long count() {
            return count;
        }

        private String name() {
            return name;
        }

        private WeaknessInsightItemVO toVO() {
            WeaknessInsightItemVO vo = new WeaknessInsightItemVO();
            vo.setName(name);
            vo.setCategory("INTERVIEW");
            vo.setCount(count);
            vo.setEvidence(evidence);
            vo.setRecommendedActionType("WEAKNESS_ANALYSIS");
            vo.setActionPath("/weakness-analysis");
            return vo;
        }
    }
}
