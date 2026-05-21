package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.feign.util.FeignResultUtils;
import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.enums.InterviewStatusEnum;
import com.codecoachai.interview.domain.enums.ReportStatusEnum;
import com.codecoachai.interview.feign.AiFeignClient;
import com.codecoachai.interview.feign.QuestionFeignClient;
import com.codecoachai.interview.feign.ResumeFeignClient;
import com.codecoachai.interview.feign.dto.GenerateReportDTO;
import com.codecoachai.interview.feign.dto.InterviewWeakPointFeedbackDTO;
import com.codecoachai.interview.feign.dto.RecommendQuestionDTO;
import com.codecoachai.interview.feign.vo.GenerateReportVO;
import com.codecoachai.interview.feign.vo.InnerQuestionVO;
import com.codecoachai.interview.feign.vo.InnerResumeDetailVO;
import com.codecoachai.interview.feign.vo.InnerResumeProjectVO;
import com.codecoachai.interview.mapper.InterviewMessageMapper;
import com.codecoachai.interview.mapper.InterviewReportMapper;
import com.codecoachai.interview.mapper.InterviewSessionMapper;
import com.codecoachai.interview.mq.InterviewMqDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InterviewReportAsyncService {

    private static final int DEFAULT_REPORT_SCORE = 82;
    private static final String REPORT_AI_EMPTY_MESSAGE = "AI report response is empty or incomplete";
    private static final String DEFAULT_REPORT_SUMMARY = "本场 V1 模拟面试已完成，综合得分 82。总分由回答完整度、关键知识点覆盖、项目表达和工程权衡四个维度综合给出。";
    private static final String DEFAULT_REPORT_STRENGTHS = "[\"能围绕 Java 后端常见题目给出基本结论\",\"能结合 Spring、MySQL、Redis 说明常见处理思路\"]";
    private static final String DEFAULT_REPORT_WEAKNESSES = "部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足。";
    private static final String DEFAULT_REPORT_SUGGESTIONS = "[\"复盘集合、并发、事务、索引和缓存高频题\",\"准备 2-3 个带指标的项目优化案例\"]";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewMessageMapper messageMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;
    private final QuestionFeignClient questionFeignClient;
    private final ObjectMapper objectMapper;
    private final InterviewMqDispatcher interviewMqDispatcher;

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
            List<InterviewMessage> messages = messageEntities(session.getId());
            GenerateReportVO aiReport = FeignResultUtils.unwrap(aiFeignClient.report(buildReportDTO(session, messages)));
            report.setStatus(ReportStatusEnum.GENERATED.name());
            applyReportContent(report, aiReport);
            applyLearningFeedback(report, messages);
            report.setFailureReason(null);
            saveReport(report);

            session.setStatus(InterviewStatusEnum.COMPLETED.name());
            session.setReportStatus(ReportStatusEnum.GENERATED.name());
            session.setTotalScore(report.getTotalScore());
            session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
            session.setFailureReason(null);
            sessionMapper.updateById(session);
            syncInterviewSearchAfterCommit(session.getId(), session.getUserId());
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

    private GenerateReportDTO buildReportDTO(InterviewSession session, List<InterviewMessage> messages) {
        InnerResumeDetailVO resume = loadResume(session);
        GenerateReportDTO dto = new GenerateReportDTO();
        dto.setInterviewId(session.getId());
        dto.setUserId(session.getUserId());
        dto.setTargetJobId(session.getTargetJobId());
        dto.setSkillProfileId(session.getSkillProfileId());
        dto.setMatchReportId(session.getMatchReportId());
        dto.setSkillGapContext(skillGapContext(session));
        dto.setMode(session.getMode());
        dto.setTargetPosition(session.getTargetPosition());
        dto.setExperienceLevel(session.getExperienceLevel());
        dto.setIndustryDirection(session.getIndustryDirection());
        dto.setDifficulty(session.getDifficulty());
        dto.setResumeContent(resume == null ? null : resume.getSummary());
        dto.setProjectContent(buildProjectContent(resume));
        dto.setMessages(messages.stream()
                .map(message -> firstText(message.getQuestionContent(), message.getUserAnswer(), message.getAiComment(), message.getContent()))
                .filter(StringUtils::hasText)
                .toList());
        return dto;
    }

    private List<InterviewMessage> messageEntities(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId)
                .orderByAsc(InterviewMessage::getCreatedAt)
                .orderByAsc(InterviewMessage::getId));
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
            throw new IllegalStateException(REPORT_AI_EMPTY_MESSAGE);
        }
        validateAiReport(aiReport);
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

    private void applyLearningFeedback(InterviewReport report, List<InterviewMessage> messages) {
        List<String> weakPoints = extractWeakPoints(report, messages);
        if (!weakPoints.isEmpty()) {
            report.setWeakPoints(toJson(weakPoints));
            report.setMainProblems(firstText(report.getMainProblems(), toJson(weakPoints)));
        }
        List<String> recommendedQuestions = recommendQuestions(weakPoints);
        if (!recommendedQuestions.isEmpty()) {
            report.setRecommendedQuestions(toJson(recommendedQuestions));
        } else if (!StringUtils.hasText(report.getRecommendedQuestions())) {
            report.setRecommendedQuestions(toJson(defaultRecommendedQuestions(weakPoints)));
        }
        List<String> suggestions = buildLearningSuggestions(weakPoints, recommendedQuestions);
        report.setReviewSuggestions(toJson(suggestions));
        report.setSuggestions(toJson(suggestions));
        report.setReportContent(enhanceReportContent(report.getReportContent(), weakPoints, recommendedQuestions, suggestions));
        feedbackSkillProfile(report, weakPoints);
    }

    private void feedbackSkillProfile(InterviewReport report, List<String> weakPoints) {
        if (report == null || weakPoints == null || weakPoints.isEmpty()) {
            return;
        }
        InterviewSession session = sessionMapper.selectById(report.getSessionId());
        if (session == null || session.getTargetJobId() == null) {
            return;
        }
        try {
            InterviewWeakPointFeedbackDTO dto = new InterviewWeakPointFeedbackDTO();
            dto.setUserId(session.getUserId());
            dto.setTargetJobId(session.getTargetJobId());
            dto.setSkillProfileId(session.getSkillProfileId());
            dto.setMatchReportId(session.getMatchReportId());
            dto.setInterviewId(session.getId());
            dto.setReportId(report.getId());
            dto.setWeakPoints(weakPoints);
            resumeFeignClient.feedbackInterviewWeakPoints(dto);
        } catch (RuntimeException ignored) {
            // Report generation must not fail only because downstream profile feedback is temporarily unavailable.
        }
    }

    private String skillGapContext(InterviewSession session) {
        if (session.getTargetJobId() == null && session.getSkillProfileId() == null && session.getMatchReportId() == null) {
            return null;
        }
        return "targetJobId=" + nullToBlank(session.getTargetJobId())
                + ", skillProfileId=" + nullToBlank(session.getSkillProfileId())
                + ", matchReportId=" + nullToBlank(session.getMatchReportId());
    }

    private String nullToBlank(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> extractWeakPoints(InterviewReport report, List<InterviewMessage> messages) {
        Set<String> weakPoints = new LinkedHashSet<>();
        addStructuredWeakPoint(weakPoints, "Java 基础", "源码细节、集合、JVM 或 Spring 基础回答不够展开", report.getWeakPoints(), messages);
        addStructuredWeakPoint(weakPoints, "数据库", "SQL 优化、索引、事务或执行计划说明不够完整", report.getWeakPoints(), messages);
        addStructuredWeakPoint(weakPoints, "并发", "线程池、锁、并发容器或一致性边界需要补强", report.getWeakPoints(), messages);
        addStructuredWeakPoint(weakPoints, "缓存", "Redis 缓存一致性、穿透/击穿/雪崩和降级方案需要补强", report.getWeakPoints(), messages);
        addStructuredWeakPoint(weakPoints, "架构设计", "系统拆分、容量评估、容错和技术取舍说明不够清晰", report.getMainProblems(), messages);
        addStructuredWeakPoint(weakPoints, "项目表达", "项目背景、个人职责、技术难点、优化结果和量化指标表达不足", report.getProjectProblems(), messages);
        for (InterviewMessage message : messages) {
            if (message.getScore() != null && message.getScore() < 75) {
                weakPoints.add("低分回答：" + summarize(firstText(message.getKnowledgePoints(), message.getComment(), message.getContent()), 80));
            }
        }
        if (weakPoints.isEmpty()) {
            weakPoints.add("项目表达：补充背景、职责、方案、取舍和量化结果");
            weakPoints.add("技术深度：补充源码细节、数据库执行计划、缓存一致性和线上排查步骤");
        }
        return weakPoints.stream().limit(8).toList();
    }

    private void addStructuredWeakPoint(Set<String> weakPoints, String dimension, String description,
                                        String reportText, List<InterviewMessage> messages) {
        String allText = (firstText(reportText, "") + " " + messages.stream()
                .map(message -> firstText(message.getQuestionContent(), message.getUserAnswer(), message.getAiComment(),
                        message.getKnowledgePoints(), message.getContent()))
                .filter(StringUtils::hasText)
                .toList()).toLowerCase();
        if (containsAny(allText, dimension.toLowerCase(), keywordForDimension(dimension))) {
            weakPoints.add(dimension + "：" + description);
        }
    }

    private String keywordForDimension(String dimension) {
        return switch (dimension) {
            case "Java 基础" -> "java";
            case "数据库" -> "mysql";
            case "并发" -> "concurrent";
            case "缓存" -> "redis";
            case "架构设计" -> "architecture";
            case "项目表达" -> "project";
            default -> dimension;
        };
    }

    private List<String> recommendQuestions(List<String> weakPoints) {
        try {
            RecommendQuestionDTO dto = new RecommendQuestionDTO();
            dto.setWeakTags(toWeakTags(weakPoints));
            dto.setLimit(5L);
            List<InnerQuestionVO> questions = FeignResultUtils.unwrap(questionFeignClient.recommendForReport(dto));
            if (questions == null || questions.isEmpty()) {
                return List.of();
            }
            return questions.stream()
                    .map(question -> firstText(question.getTitle(), question.getContent()))
                    .filter(StringUtils::hasText)
                    .limit(5)
                    .toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<String> toWeakTags(List<String> weakPoints) {
        Set<String> tags = new LinkedHashSet<>();
        String text = String.join(" ", weakPoints).toLowerCase();
        if (containsAny(text, "java", "集合", "源码", "jvm", "spring")) {
            tags.add("Java");
        }
        if (containsAny(text, "mysql", "数据库", "索引", "事务", "sql")) {
            tags.add("MySQL");
            tags.add("索引");
        }
        if (containsAny(text, "redis", "缓存")) {
            tags.add("Redis");
            tags.add("缓存");
        }
        if (containsAny(text, "并发", "线程", "锁", "concurrent")) {
            tags.add("并发");
        }
        if (containsAny(text, "项目", "架构", "取舍", "故障")) {
            tags.add("项目");
            tags.add("架构");
        }
        if (tags.isEmpty()) {
            tags.add("Java");
            tags.add("MySQL");
            tags.add("Redis");
        }
        return new ArrayList<>(tags);
    }

    private List<String> defaultRecommendedQuestions(List<String> weakPoints) {
        List<String> tags = toWeakTags(weakPoints);
        List<String> questions = new ArrayList<>();
        if (tags.contains("Java")) {
            questions.add("HashMap 扩容机制和线程安全问题如何回答？");
        }
        if (tags.contains("MySQL")) {
            questions.add("MySQL 联合索引、事务隔离和慢 SQL 优化如何落地？");
        }
        if (tags.contains("Redis")) {
            questions.add("如何保证缓存和数据库一致性，并处理缓存穿透、击穿、雪崩？");
        }
        if (tags.contains("并发")) {
            questions.add("线程池参数如何设置，线上拒绝策略和任务堆积如何排查？");
        }
        if (tags.contains("项目")) {
            questions.add("请按背景、职责、方案、取舍、结果复盘一个项目难点。");
        }
        return questions.stream().limit(5).toList();
    }

    private List<String> buildLearningSuggestions(List<String> weakPoints, List<String> recommendedQuestions) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("薄弱点复盘：按 " + String.join("；", weakPoints.stream().limit(4).toList()) + " 建立复习清单。");
        suggestions.add("推荐复习方向：Java 基础、数据库、并发、缓存、架构设计按薄弱点优先级逐项补强。");
        suggestions.add("推荐练习题：" + String.join("；", recommendedQuestions.isEmpty()
                ? defaultRecommendedQuestions(weakPoints)
                : recommendedQuestions));
        suggestions.add("下一轮面试建议：优先选择项目深挖 + 数据库/缓存/并发组合训练，重点练习追问下的细节展开。");
        suggestions.add("项目表达改进：使用背景、职责、方案、取舍、指标、复盘的结构回答项目问题。");
        suggestions.add("技术深度提升：每个方案至少补充原理、边界、失败场景、线上排查和量化结果。");
        return suggestions;
    }

    private String enhanceReportContent(String originalContent, List<String> weakPoints,
                                        List<String> recommendedQuestions, List<String> suggestions) {
        StringBuilder builder = new StringBuilder(firstText(originalContent, DEFAULT_REPORT_SUMMARY));
        builder.append("\n\n## 学习反馈闭环");
        builder.append("\n### 当前主要短板");
        weakPoints.forEach(item -> builder.append("\n- ").append(item));
        builder.append("\n### 推荐练习题");
        (recommendedQuestions.isEmpty() ? defaultRecommendedQuestions(weakPoints) : recommendedQuestions)
                .forEach(item -> builder.append("\n- ").append(item));
        builder.append("\n### 复习与下一轮训练建议");
        suggestions.forEach(item -> builder.append("\n- ").append(item));
        return builder.toString();
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

    private void syncInterviewSearchAfterCommit(Long sessionId, Long userId) {
        Runnable action = () -> interviewMqDispatcher.dispatchInterviewSearchUpsert(sessionId, userId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void validateAiReport(GenerateReportVO aiReport) {
        if (aiReport.getTotalScore() == null
                || !StringUtils.hasText(aiReport.getSummary())
                || !StringUtils.hasText(aiReport.getReportContent())) {
            throw new IllegalStateException(REPORT_AI_EMPTY_MESSAGE);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && value.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String summarize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "需要补充回答细节";
        }
        String text = value.trim().replaceAll("\\s+", " ");
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
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
