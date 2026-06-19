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
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.domain.vo.InterviewReportAgentEvidenceVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.codecoachai.interview.service.impl.AgentBusinessActionNotifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试报告相关 inner 接口，供 codecoachai-task 异步消费者调用。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/interviews")
public class InnerInterviewReportController {

    private static final DateTimeFormatter ES_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewMqDispatcher interviewMqDispatcher;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;

    /**
     * 获取面试报告生成所需的上下文（session + messages）。
     */
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

    /**
     * 回写报告结果（task-service 调用）。
     */
    @PostMapping("/{sessionId}/complete-report")
    public Result<Void> completeReport(@PathVariable Long sessionId, @RequestBody CompleteReportDTO dto) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "session not found: " + sessionId);
        }

        String status = StringUtils.hasText(dto.getReportStatus()) ? dto.getReportStatus() : "SUCCESS";
        boolean success = "SUCCESS".equalsIgnoreCase(status);

        // 更新 session
        sessionMapper.update(null,
                new LambdaUpdateWrapper<InterviewSession>()
                        .eq(InterviewSession::getId, sessionId)
                        .set(InterviewSession::getReportStatus, success ? "GENERATED" : "FAILED")
                        .set(success, InterviewSession::getTotalScore, dto.getTotalScore())
                        .set(!success, InterviewSession::getFailureReason, dto.getErrorMessage())
                        .set(InterviewSession::getUpdatedAt, LocalDateTime.now()));

        // 更新或创建 report
        InterviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, sessionId)
                        .last("limit 1"));
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(sessionId);
            report.setUserId(session.getUserId());
        }
        report.setStatus(success ? "GENERATED" : "FAILED");
        if (success && StringUtils.hasText(dto.getReportJson())) {
            report.setReportContent(dto.getReportJson());
            report.setTotalScore(dto.getTotalScore());
            report.setGeneratedAt(LocalDateTime.now());
        }
        if (!success) {
            report.setFailureReason(dto.getErrorMessage());
        }
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }

        log.info("Interview report completed sessionId={} status={}", sessionId, status);
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

    private void completeAgentInterviewTask(InterviewSession session, InterviewReport report) {
        if (session == null || report == null || !ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            return;
        }
        agentBusinessActionNotifier.completeInterviewReport(session.getUserId(), session.getTargetJobId(),
                report.getId());
    }

    private String firstText(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
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
        private String reportJson;
        private Integer totalScore;
        private String reportStatus;
        private String errorMessage;
    }

    @GetMapping("/{id}/search-doc")
    public Result<Map<String, Object>> getSearchDoc(@PathVariable Long id) {
        InterviewSession s = sessionMapper.selectById(id);
        if (s == null) {
            return Result.success(null);
        }
        InterviewReport report = reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, id)
                .orderByDesc(InterviewReport::getUpdatedAt)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));

        Map<String, Object> doc = new HashMap<>();
        doc.put("docId", String.valueOf(s.getId()));
        doc.put("id", s.getId());
        doc.put("sessionId", s.getId());
        doc.put("reportId", report == null ? null : report.getId());
        doc.put("userId", String.valueOf(s.getUserId()));
        doc.put("mode", s.getMode());
        doc.put("title", s.getTitle());
        doc.put("targetPosition", s.getTargetPosition());
        doc.put("experienceLevel", s.getExperienceLevel());
        doc.put("industryDirection", s.getIndustryDirection());
        doc.put("difficulty", s.getDifficulty());
        doc.put("status", s.getStatus());
        doc.put("reportStatus", report == null ? s.getReportStatus() : firstText(report.getStatus(), s.getReportStatus()));
        doc.put("totalScore", report == null ? s.getTotalScore() : firstNonNull(report.getTotalScore(), s.getTotalScore()));
        doc.put("interviewerStyle", s.getInterviewerStyle());
        doc.put("summary", report == null ? null : report.getSummary());
        doc.put("weakPoints", report == null ? null : firstText(report.getWeakPoints(), report.getWeaknesses(), report.getMainProblems()));
        doc.put("strengths", report == null ? null : report.getStrengths());
        doc.put("mainProblems", report == null ? null : report.getMainProblems());
        doc.put("projectProblems", report == null ? null : report.getProjectProblems());
        doc.put("reviewSuggestions", report == null ? null : firstText(report.getReviewSuggestions(), report.getSuggestions()));
        doc.put("suggestions", report == null ? null : report.getSuggestions());
        doc.put("reportContent", report == null ? null : report.getReportContent());
        doc.put("startTime", formatDateTime(s.getStartTime()));
        doc.put("endTime", formatDateTime(s.getEndTime()));
        doc.put("createdAt", formatDateTime(s.getCreatedAt()));
        doc.put("generatedAt", report == null ? null : formatDateTime(report.getGeneratedAt()));
        doc.put("syncedAt", java.time.Instant.now().toString());
        return Result.success(doc);
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ES_DATE_FORMAT.format(value);
    }
}
