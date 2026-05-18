package com.codecoachai.interview.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
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

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;

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
        return Result.success();
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
}
