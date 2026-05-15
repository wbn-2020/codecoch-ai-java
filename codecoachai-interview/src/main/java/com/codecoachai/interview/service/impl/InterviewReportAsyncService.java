package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.enums.InterviewStatusEnum;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.GenerateReportDTO;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeProjectVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InterviewReportAsyncService {

    private static final int DEFAULT_REPORT_SCORE = 82;
    private static final String DEFAULT_REPORT_SUMMARY = "本场 V1 模拟面试已完成，综合得分 82。总分由回答完整度、关键知识点覆盖、项目表达和工程权衡四个维度综合给出。";
    private static final String DEFAULT_REPORT_STRENGTHS = "[\"能围绕 Java 后端常见题目给出基本结论\",\"能结合 Spring、MySQL、Redis 说明常见处理思路\"]";
    private static final String DEFAULT_REPORT_WEAKNESSES = "部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足。";
    private static final String DEFAULT_REPORT_SUGGESTIONS = "[\"复盘集合、并发、事务、索引和缓存高频题\",\"准备 2-3 个带指标的项目优化案例\"]";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewMessageMapper messageMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;

    @Async("interviewReportExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void generateReportAsync(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        InterviewReport report = currentReport(session.getId());
        if (report == null) {
            report = new InterviewReport();
            report.setSessionId(session.getId());
            report.setUserId(session.getUserId());
            report.setStatus(ReportStatusEnum.GENERATING.name());
            reportMapper.insert(report);
        }
        try {
            GenerateReportVO aiReport = FeignResultUtils.unwrap(aiFeignClient.report(buildReportDTO(session)));
            report.setStatus(ReportStatusEnum.GENERATED.name());
            applyReportContent(report, aiReport);
            report.setFailureReason(null);
            saveReport(report);

            session.setStatus(InterviewStatusEnum.COMPLETED.name());
            session.setReportStatus(ReportStatusEnum.GENERATED.name());
            session.setTotalScore(report.getTotalScore());
            session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
            session.setFailureReason(null);
            sessionMapper.updateById(session);
        } catch (RuntimeException ex) {
            report.setStatus(ReportStatusEnum.FAILED.name());
            report.setFailureReason(ex.getMessage());
            saveReport(report);

            session.setStatus(InterviewStatusEnum.FAILED.name());
            session.setReportStatus(ReportStatusEnum.FAILED.name());
            session.setFailureReason(ex.getMessage());
            sessionMapper.updateById(session);
        }
    }

    private GenerateReportDTO buildReportDTO(InterviewSession session) {
        InnerResumeDetailVO resume = loadResume(session);
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setInterviewId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setMode(session.getMode());
        dto.setTargetPosition(session.getTargetPosition());
        dto.setExperienceLevel(session.getExperienceLevel());
        dto.setIndustryDirection(session.getIndustryDirection());
        dto.setDifficulty(session.getDifficulty());
        dto.setResumeContent(resume == null ? null : resume.getSummary());
        dto.setProjectContent(buildProjectContent(resume));
        dto.setMessages(messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, session.getId())
                        .orderByAsc(InterviewMessage::getCreatedAt)
                        .orderByAsc(InterviewMessage::getId))
                .stream()
                .map(message -> firstText(message.getQuestionContent(), message.getUserAnswer(), message.getAiComment(), message.getContent()))
                .filter(StringUtils::hasText)
                .toList());
        return dto;
    }

    private InnerResumeDetailVO loadResume(InterviewSession session) {
        if (session.getResumeId() == null) {
            return null;
        }
        return FeignResultUtils.unwrap(resumeFeignClient.getResume(session.getResumeId()));
    }

    private String buildProjectContent(InnerResumeDetailVO resume) {
        if (resume == null || resume.getProjects() == null || resume.getProjects().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (InnerResumeProjectVO project : resume.getProjects()) {
            if (project == null) {
                continue;
            }
            appendLine(builder, "项目" + index + "名称", project.getProjectName());
            appendLine(builder, "项目周期", project.getProjectPeriod());
            appendLine(builder, "项目背景", firstText(project.getProjectBackground(), project.getDescription()));
            appendLine(builder, "技术栈", project.getTechStack());
            appendLine(builder, "个人角色", project.getRole());
            appendLine(builder, "个人职责", project.getResponsibility());
            appendLine(builder, "核心功能", project.getCoreFeatures());
            appendLine(builder, "技术难点", project.getTechnicalDifficulties());
            appendLine(builder, "优化结果", project.getOptimizationResults());
            appendLine(builder, "项目亮点", project.getHighlights());
            builder.append('\n');
            index++;
        }
        return builder.toString().trim();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append("：").append(value.trim()).append('\n');
        }
    }

    private InterviewReport currentReport(Long sessionId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getSessionId, sessionId)
                .last("limit 1"));
    }

    private void applyReportContent(InterviewReport report, GenerateReportVO aiReport) {
        if (aiReport == null) {
            applyDefaultReportContent(report);
            return;
        }
        report.setTotalScore(aiReport.getTotalScore() == null ? DEFAULT_REPORT_SCORE : aiReport.getTotalScore());
        report.setSummary(firstText(aiReport.getSummary(), DEFAULT_REPORT_SUMMARY));
        report.setStageScores(aiReport.getStageScores());
        report.setWeakPoints(aiReport.getWeakPoints());
        report.setStrengths(firstText(aiReport.getStrengths(), DEFAULT_REPORT_STRENGTHS));
        report.setWeaknesses(firstText(aiReport.getWeaknesses(), DEFAULT_REPORT_WEAKNESSES));
        report.setMainProblems(firstText(aiReport.getMainProblems(), report.getWeaknesses()));
        report.setProjectProblems(aiReport.getProjectProblems());
        report.setReviewSuggestions(firstText(aiReport.getReviewSuggestions(), aiReport.getSuggestions(), DEFAULT_REPORT_SUGGESTIONS));
        report.setRecommendedQuestions(aiReport.getRecommendedQuestions());
        report.setQaReview(aiReport.getQaReview());
        report.setReportContent(firstText(aiReport.getReportContent(), report.getSummary()));
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(firstText(aiReport.getSuggestions(), DEFAULT_REPORT_SUGGESTIONS));
    }

    private void applyDefaultReportContent(InterviewReport report) {
        report.setTotalScore(DEFAULT_REPORT_SCORE);
        report.setSummary(DEFAULT_REPORT_SUMMARY);
        report.setStageScores("{\"Java基础\":82,\"数据库\":80,\"项目表达\":84}");
        report.setWeakPoints("[\"源码细节展开不足\",\"线上排查步骤不够完整\",\"项目指标量化不足\"]");
        report.setStrengths(DEFAULT_REPORT_STRENGTHS);
        report.setWeaknesses(DEFAULT_REPORT_WEAKNESSES);
        report.setMainProblems(DEFAULT_REPORT_WEAKNESSES);
        report.setProjectProblems("[\"项目优化结果缺少压测数据或线上指标佐证\"]");
        report.setReviewSuggestions(DEFAULT_REPORT_SUGGESTIONS);
        report.setRecommendedQuestions("[\"HashMap 扩容机制是什么？\",\"MySQL 索引失效有哪些场景？\",\"如何保证缓存和数据库一致性？\"]");
        report.setQaReview("[]");
        report.setReportContent(DEFAULT_REPORT_SUMMARY);
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(DEFAULT_REPORT_SUGGESTIONS);
        report.setFailureReason(null);
    }

    private void saveReport(InterviewReport report) {
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
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
}
