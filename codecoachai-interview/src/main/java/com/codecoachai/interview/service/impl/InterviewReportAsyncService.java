package com.codecoachai.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
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
import com.codecoachai.interview.scenario.InterviewScenarioBinding;
import com.codecoachai.interview.scenario.InterviewScenarioBindingMapper;
import com.codecoachai.interview.support.InterviewReportTrustPolicy;
import com.codecoachai.interview.support.InterviewReportScoringContract;
import com.codecoachai.interview.support.InterviewRubricVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewReportAsyncService {

    private static final String REPORT_AI_EMPTY_MESSAGE = "AI 报告内容暂时不完整";
    private static final String REPORT_AI_INCOMPLETE_MESSAGE = REPORT_AI_EMPTY_MESSAGE
            + "，未生成可核对的题目明细。答题记录已保留，请稍后重新生成报告。";
    private static final String REPORT_AI_INCOMPLETE_FALLBACK_REASON =
            "AI 报告结构不完整，已基于已保存题目、回答、评分和追问生成保底复盘。";
    private static final String REPORT_AI_INCOMPLETE_SUGGESTIONS = "[\"稍后重新生成面试报告\",\"继续补充回答后再生成报告\",\"如多次失败，请将诊断记录交给管理员排查\"]";
    private static final String DEFAULT_REPORT_SUMMARY = "本场模拟面试已完成，请结合题目明细复盘回答表现。";
    private static final String DEFAULT_REPORT_STRENGTHS = "[\"能围绕 Java 后端常见题目给出基本结论\",\"能结合 Spring、MySQL、Redis 说明常见处理思路\"]";
    private static final String DEFAULT_REPORT_WEAKNESSES = "部分回答停留在结论层，对源码细节、执行计划字段、缓存一致性边界和线上排查步骤展开不足。";
    private static final String DEFAULT_REPORT_SUGGESTIONS = "[\"复盘集合、并发、事务、索引和缓存高频题\",\"准备 2-3 个带指标的项目优化案例\"]";
    private static final String REPORT_SAMPLE_INSUFFICIENT_MESSAGE = "答题样本不足，无法生成评分报告。请至少提交 1 条有效回答后再结束面试。";
    private static final String REPORT_SAMPLE_INSUFFICIENT_SUGGESTIONS = "[\"至少提交 1 条有效回答后再结束面试\",\"如果只是想退出，可稍后重新开始面试训练\"]";
    private static final String REPORT_GENERATION_FAILED_MESSAGE =
            "面试报告生成失败，答题记录已保留，请稍后重新生成或联系管理员查看诊断。";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewScenarioBindingMapper scenarioBindingMapper;
    private final ResumeFeignClient resumeFeignClient;
    private final AiFeignClient aiFeignClient;
    private final QuestionFeignClient questionFeignClient;
    private final AgentBusinessActionNotifier agentBusinessActionNotifier;
    private final ObjectMapper objectMapper;
    private final InterviewMqDispatcher interviewMqDispatcher;
    private final InterviewReportTransactionService interviewReportTransactionService;

    @Async("interviewReportExecutor")
    public void generateReportAsync(Long sessionId, Long reportId, String generationToken) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        InterviewReport report = currentReport(sessionId, reportId, generationToken);
        if (report == null || !ReportStatusEnum.GENERATING.name().equals(report.getStatus())) {
            log.info("Skip stale async interview report task, sessionId={}, reportId={}", sessionId, reportId);
            return;
        }
        try {
            List<InterviewMessage> messages = messageEntities(session.getId());
            if (!hasScorableAnswers(messages)) {
                InterviewReport current = currentReport(sessionId, reportId, generationToken);
                if (current == null) {
                    log.info("Skip stale async insufficient-report write-back, sessionId={}, reportId={}",
                            sessionId, reportId);
                    return;
                }
                markReportSampleInsufficient(session, current, generationToken);
                return;
            }
            GenerateReportVO aiReport = FeignResultUtils.unwrap(aiFeignClient.report(buildReportDTO(session, messages)));
            interviewReportTransactionService.completeReportSuccess(
                    () -> doCompleteReportSuccess(session, reportId, generationToken, aiReport, messages));
        } catch (RuntimeException ex) {
            log.warn("Interview report generation failed, sessionId={}", session.getId(), ex);
            interviewReportTransactionService.completeReportFailed(
                    () -> doCompleteReportFailed(session, reportId, generationToken));
        }
    }

    private void doCompleteReportSuccess(InterviewSession session, Long reportId, String generationToken,
                                         GenerateReportVO aiReport, List<InterviewMessage> messages) {
        InterviewReport report = currentReport(session.getId(), reportId, generationToken);
        if (report == null) {
            log.info("Skip stale async report success write-back, sessionId={}, reportId={}",
                    session.getId(), reportId);
            return;
        }
        report.setStatus(ReportStatusEnum.GENERATED.name());
        applyReportContent(report, aiReport, messages);
        if (InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            applyLearningFeedback(report, messages);
        }
        if (!updateCurrentReportAttempt(report, generationToken)) {
            log.info("Skip stale async report success CAS, sessionId={}, reportId={}",
                    session.getId(), reportId);
            return;
        }

        session.setStatus(InterviewStatusEnum.COMPLETED.name());
        if (ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            session.setReportStatus(ReportStatusEnum.GENERATED.name());
            session.setTotalScore(report.getTotalScore());
            session.setFailureReason(null);
        } else {
            session.setReportStatus(ReportStatusEnum.FAILED.name());
            session.setTotalScore(null);
            session.setFailureReason(report.getFailureReason());
        }
        session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
        sessionMapper.updateById(session);
        if (ReportStatusEnum.GENERATED.name().equals(report.getStatus())) {
            syncInterviewSearchAfterCommit(session.getId(), session.getUserId());
            completeAgentInterviewTask(session, report);
        }
    }

    private void doCompleteReportFailed(InterviewSession session, Long reportId, String generationToken) {
        InterviewReport report = currentReport(session.getId(), reportId, generationToken);
        if (report == null) {
            log.info("Skip stale async report failure write-back, sessionId={}, reportId={}",
                    session.getId(), reportId);
            return;
        }
        try {
            report.setStatus(ReportStatusEnum.FAILED.name());
            report.setTotalScore(null);
            report.setFailureReason(REPORT_GENERATION_FAILED_MESSAGE);
            if (!updateCurrentReportAttempt(report, generationToken)) {
                log.info("Skip stale async report failure CAS, sessionId={}, reportId={}",
                        session.getId(), reportId);
                return;
            }
            session.setStatus(InterviewStatusEnum.FAILED.name());
            session.setReportStatus(ReportStatusEnum.FAILED.name());
            session.setTotalScore(null);
            session.setFailureReason(REPORT_GENERATION_FAILED_MESSAGE);
            sessionMapper.updateById(session);
        } catch (RuntimeException ex) {
            log.error("Critical: failed to persist interview report failure state, " +
                    "sessionId={}, reportId={}", session.getId(), report.getId(), ex);
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
        Map<Long, InterviewMessage> messagesById = messageById(messages);
        dto.setMessages(messages.stream()
                .map(message -> reportMessageText(message, messagesById))
                .filter(StringUtils::hasText)
                .toList());
        dto.setTrainingScene(session.getTrainingScene());
        dto.setTargetSkillDomain(session.getTargetSkillDomain());
        dto.setTargetSkillCodes(readStringList(session.getTargetSkillCodes()));
        dto.setTargetLevel(session.getTargetLevel());
        dto.setProjectEvidenceIds(readLongList(session.getProjectEvidenceIds()));
        dto.setFollowUpIntensity(session.getFollowUpIntensity());
        dto.setTrainingContextSummary(session.getTrainingContextSummary());
        return dto;
    }

    private String reportMessageText(InterviewMessage message, Map<Long, InterviewMessage> messagesById) {
        if (message == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "Role", message.getRole());
        appendLine(builder, "Type", message.getMessageType());
        appendLine(builder, "Question", firstText(message.getQuestionContent(), questionContentByParent(message, messagesById)));
        appendLine(builder, "CandidateAnswer", message.getUserAnswer());
        appendLine(builder, "AiComment", firstText(message.getAiComment(), message.getComment()));
        appendLine(builder, "Score", message.getScore() == null ? null : message.getScore().toString());
        appendLine(builder, "Content", message.getContent());
        return builder.toString().trim();
    }

    private Map<Long, InterviewMessage> messageById(List<InterviewMessage> messages) {
        Map<Long, InterviewMessage> result = new LinkedHashMap<>();
        if (messages == null) {
            return result;
        }
        for (InterviewMessage message : messages) {
            if (message != null && message.getId() != null) {
                result.putIfAbsent(message.getId(), message);
            }
        }
        return result;
    }

    private String questionContentByParent(InterviewMessage message, Map<Long, InterviewMessage> messagesById) {
        if (message == null || message.getParentMessageId() == null) {
            return null;
        }
        InterviewMessage parent = messagesById == null ? null : messagesById.get(message.getParentMessageId());
        return parent == null ? null : firstText(parent.getQuestionContent(), parent.getContent());
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
                .eq(InterviewReport::getDeleted, CommonConstants.NO)
                .orderByDesc(InterviewReport::getId)
                .last("limit 1"));
    }

    private InterviewReport currentReport(Long sessionId, Long reportId, String generationToken) {
        InterviewReport latest = currentReport(sessionId);
        if (latest == null) {
            return null;
        }
        if (reportId != null && !reportId.equals(latest.getId())) {
            return null;
        }
        if (StringUtils.hasText(generationToken) && !generationToken.equals(latest.getGenerationToken())) {
            return null;
        }
        return latest;
    }

    private String reportRubricVersion(Long sessionId) {
        if (sessionId == null) {
            return InterviewRubricVersion.CURRENT;
        }
        InterviewScenarioBinding binding = scenarioBindingMapper.selectOne(
                new LambdaQueryWrapper<InterviewScenarioBinding>()
                        .eq(InterviewScenarioBinding::getSessionId, sessionId)
                        .eq(InterviewScenarioBinding::getDeleted, CommonConstants.NO)
                        .last("limit 1"));
        if (binding == null) {
            return InterviewRubricVersion.CURRENT;
        }
        return "scenario:" + binding.getScenarioVersionId() + ":rubric:" + binding.getRubricVersionId();
    }

    private void applyReportContent(InterviewReport report, GenerateReportVO aiReport, List<InterviewMessage> messages) {
        int answerCount = countScorableAnswers(messages);
        if (aiReportMissingDisplayContent(aiReport) || !hasExpectedQaReviews(aiReport, answerCount)) {
            applyFallbackReportContent(report, aiReport, messages, answerCount);
            return;
        }
        report.setTotalScore(aiReport.getTotalScore());
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
        report.setRubricScores(aiReport.getRubricScores());
        report.setRubricVersion(reportRubricVersion(report.getSessionId()));
        report.setFollowUpTree(firstText(aiReport.getFollowUpTree(), buildFallbackFollowUpTree(messages)));
        report.setAdviceEvidence(firstText(aiReport.getAdviceEvidence(), buildFallbackAdviceEvidence(report, messages, answerCount)));
        report.setAbilityProfileUpdates(firstText(aiReport.getAbilityProfileUpdates(), buildFallbackAbilityProfileUpdates(messages, answerCount)));
        report.setReportContent(firstText(aiReport.getReportContent(), report.getSummary()));
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(firstText(aiReport.getSuggestions(), DEFAULT_REPORT_SUGGESTIONS));
        report.setFailureReason(null);
    }

    private void applyFallbackReportContent(InterviewReport report, GenerateReportVO aiReport,
                                            List<InterviewMessage> messages, int answerCount) {
        if (answerCount <= 0) {
            markReportAiIncomplete(report);
            return;
        }
        Integer totalScore = firstValidTotalScore(
                aiReport == null ? null : aiReport.getTotalScore(),
                averageAnswerScore(messages));
        String rubricVersion = reportRubricVersion(report.getSessionId());
        String rubricScores = aiReport == null ? null : aiReport.getRubricScores();
        InterviewReportScoringContract.Validation scoringContract =
                InterviewReportScoringContract.validate(
                        objectMapper, totalScore, rubricVersion, rubricScores);
        if (!scoringContract.valid()) {
            markReportAiIncomplete(report);
            report.setFailureReason(REPORT_AI_INCOMPLETE_MESSAGE
                    + " [" + scoringContract.reasonCode() + "]");
            return;
        }
        report.setStatus(ReportStatusEnum.GENERATED.name());
        report.setTotalScore(totalScore);
        report.setSummary(firstText(aiReport == null ? null : aiReport.getSummary(), DEFAULT_REPORT_SUMMARY));
        report.setStageScores(firstText(aiReport == null ? null : aiReport.getStageScores(), "{}"));
        report.setWeakPoints(firstText(aiReport == null ? null : aiReport.getWeakPoints(), "[]"));
        report.setStrengths(firstText(aiReport == null ? null : aiReport.getStrengths(), DEFAULT_REPORT_STRENGTHS));
        report.setWeaknesses(firstText(aiReport == null ? null : aiReport.getWeaknesses(), DEFAULT_REPORT_WEAKNESSES));
        report.setMainProblems(firstText(aiReport == null ? null : aiReport.getMainProblems(), report.getWeaknesses()));
        report.setProjectProblems(firstText(aiReport == null ? null : aiReport.getProjectProblems(), "[]"));
        report.setReviewSuggestions(firstText(
                aiReport == null ? null : aiReport.getReviewSuggestions(),
                aiReport == null ? null : aiReport.getSuggestions(),
                REPORT_AI_INCOMPLETE_SUGGESTIONS));
        report.setRecommendedQuestions(firstText(aiReport == null ? null : aiReport.getRecommendedQuestions(), "[]"));
        report.setQaReview(buildFallbackQaReview(messages));
        report.setRubricScores(rubricScores);
        report.setRubricVersion(rubricVersion);
        report.setFollowUpTree(firstText(aiReport == null ? null : aiReport.getFollowUpTree(),
                buildFallbackFollowUpTree(messages)));
        report.setAdviceEvidence(firstText(aiReport == null ? null : aiReport.getAdviceEvidence(),
                buildFallbackAdviceEvidence(report, messages, answerCount)));
        report.setAbilityProfileUpdates(firstText(aiReport == null ? null : aiReport.getAbilityProfileUpdates(),
                buildFallbackAbilityProfileUpdates(messages, answerCount)));
        report.setReportContent(firstText(aiReport == null ? null : aiReport.getReportContent(), report.getSummary()));
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(firstText(aiReport == null ? null : aiReport.getSuggestions(), DEFAULT_REPORT_SUGGESTIONS));
        report.setFailureReason(REPORT_AI_INCOMPLETE_FALLBACK_REASON);
    }

    private String buildFallbackQaReview(List<InterviewMessage> messages) {
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (InterviewMessage answer : messages == null ? List.<InterviewMessage>of() : messages) {
            if (!isUserAnswer(answer)) {
                continue;
            }
            InterviewMessage evaluation = firstMessage(messages, answer.getParentMessageId(), "AI", "EVALUATION");
            InterviewMessage followUp = firstMessage(messages, answer.getParentMessageId(), "AI", "FOLLOW_UP");
            Integer score = firstPositive(answer.getAiScore(), answer.getScore(),
                    evaluation == null ? null : evaluation.getAiScore(),
                    evaluation == null ? null : evaluation.getScore());
            String comment = firstText(answer.getAiComment(), answer.getComment(),
                    evaluation == null ? null : evaluation.getAiComment(),
                    evaluation == null ? null : evaluation.getComment(),
                    evaluation == null ? null : evaluation.getContent());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", answer.getId());
            item.put("role", answer.getRole());
            item.put("messageType", answer.getMessageType());
            item.put("questionId", answer.getQuestionId());
            item.put("questionContent", firstText(answer.getQuestionContent(), parentQuestionContent(messages, answer)));
            item.put("userAnswer", firstText(answer.getUserAnswer(), answer.getContent()));
            item.put("aiScore", score);
            item.put("score", score);
            item.put("aiComment", comment);
            item.put("comment", comment);
            item.put("knowledgePoints", firstText(answer.getKnowledgePoints(),
                    evaluation == null ? null : evaluation.getKnowledgePoints()));
            item.put("followUpQuestion", followUp == null ? null : followUp.getContent());
            item.put("followUpReason", followUp == null ? null : followUp.getFollowUpReason());
            item.put("fallback", true);
            item.put("createdAt", answer.getCreatedAt() == null ? null : answer.getCreatedAt().toString());
            reviews.add(item);
        }
        try {
            return objectMapper.writeValueAsString(reviews);
        } catch (Exception ex) {
            log.warn("Failed to build async fallback qaReview");
            return "[]";
        }
    }

    private String buildFallbackFollowUpTree(List<InterviewMessage> messages) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (InterviewMessage followUp : messages == null ? List.<InterviewMessage>of() : messages) {
            if (!"AI".equalsIgnoreCase(followUp.getRole()) || !"FOLLOW_UP".equalsIgnoreCase(followUp.getMessageType())) {
                continue;
            }
            InterviewMessage question = messageById(messages, followUp.getParentMessageId());
            InterviewMessage answer = firstUserAnswer(messages, followUp.getParentMessageId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionMessageId", followUp.getParentMessageId());
            item.put("answerMessageId", answer == null ? null : answer.getId());
            item.put("followUpMessageId", followUp.getId());
            item.put("questionSummary", truncate(firstText(question == null ? null : question.getQuestionContent(),
                    question == null ? null : question.getContent()), 160));
            item.put("answerSummary", truncate(firstText(answer == null ? null : answer.getUserAnswer(),
                    answer == null ? null : answer.getContent()), 160));
            item.put("followUpQuestion", truncate(followUp.getContent(), 160));
            item.put("followUpIntent", "CLARIFY_RISK");
            item.put("followUpReason", truncate(firstText(followUp.getFollowUpReason(), "Need to verify depth or risk boundary."), 160));
            item.put("exposedRisk", inferExposedRisk(followUp));
            item.put("evidenceSource", "INTERVIEW_MESSAGE");
            tree.add(item);
        }
        return jsonArray(tree);
    }

    private String buildFallbackAdviceEvidence(InterviewReport report, List<InterviewMessage> messages, int answerCount) {
        boolean sampleInsufficient = answerCount < 2;
        List<Map<String, Object>> advice = new ArrayList<>();
        advice.add(adviceItem("Replay weak interview answers", "PRACTICE_SKILL", "MEDIUM",
                "Use the report weak points and follow-up trace to run one targeted practice round.",
                "/interviews/create?source=interviewReport&reportId=" + (report == null ? "" : report.getId()),
                report == null ? null : report.getId(), firstAnswerEvidence(messages), sampleInsufficient));
        if (hasFollowUps(messages)) {
            advice.add(adviceItem("Close follow-up risk gaps", "FOLLOW_UP_REVIEW", "MEDIUM",
                    "Review every follow-up reason and prepare a stronger second answer.",
                    "/agent/today?source=interviewReport&reportId=" + (report == null ? "" : report.getId()),
                    report == null ? null : report.getId(), firstFollowUpEvidence(messages), sampleInsufficient));
        }
        return jsonArray(advice);
    }

    private Map<String, Object> adviceItem(String title, String type, String confidence, String content,
                                           String actionUrl, Long reportId, String evidence, boolean sampleInsufficient) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("adviceType", type);
        item.put("confidence", sampleInsufficient ? "LOW" : confidence);
        item.put("content", content);
        item.put("actionUrl", actionUrl);
        item.put("sampleInsufficient", sampleInsufficient);
        item.put("sampleWarning", sampleInsufficient
                ? "Sample is insufficient; advice is a candidate next step, not a strong conclusion."
                : null);
        item.put("feedbackStatus", "NONE");
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceType", "INTERVIEW_REPORT");
        source.put("sourceId", reportId);
        source.put("sourceSummary", truncate(firstText(evidence, "Generated from interview report summary."), 160));
        sources.add(source);
        item.put("evidenceSources", sources);
        return item;
    }

    private String buildFallbackAbilityProfileUpdates(List<InterviewMessage> messages, int answerCount) {
        boolean sampleInsufficient = answerCount < 2;
        List<Map<String, Object>> updates = new ArrayList<>();
        for (String skillCode : extractKnowledgePoints(messages).stream().limit(5).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillCode", skillCode);
            item.put("candidateStatus", normalizeFivePointScore(averageAnswerScore(messages)) >= 3 ? "BASIC" : "WEAK");
            item.put("confidence", sampleInsufficient ? "LOW" : "MEDIUM");
            item.put("evidenceCount", answerCount);
            item.put("sampleInsufficient", sampleInsufficient);
            item.put("sampleWarning", sampleInsufficient
                    ? "Need more interview samples before automatically updating the ability profile."
                    : null);
            updates.add(item);
        }
        return jsonArray(updates);
    }

    private int normalizeFivePointScore(Integer score) {
        if (score == null || score <= 0) {
            return 2;
        }
        if (score > 5) {
            return Math.max(1, Math.min(5, Math.round(score / 20.0f)));
        }
        return Math.max(1, Math.min(5, score));
    }

    private String firstAnswerEvidence(List<InterviewMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .filter(this::isUserAnswer)
                .map(message -> firstText(message.getAiComment(), message.getComment(), message.getUserAnswer(), message.getContent()))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String firstKnowledgeEvidence(List<InterviewMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .map(InterviewMessage::getKnowledgePoints)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(firstAnswerEvidence(messages));
    }

    private String firstFollowUpEvidence(List<InterviewMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> "AI".equalsIgnoreCase(message.getRole()))
                .filter(message -> "FOLLOW_UP".equalsIgnoreCase(message.getMessageType()))
                .map(message -> firstText(message.getFollowUpReason(), message.getContent()))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(firstAnswerEvidence(messages));
    }

    private InterviewMessage messageById(List<InterviewMessage> messages, Long messageId) {
        if (messages == null || messageId == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> messageId.equals(message.getId()))
                .findFirst()
                .orElse(null);
    }

    private InterviewMessage firstUserAnswer(List<InterviewMessage> messages, Long parentMessageId) {
        if (messages == null || parentMessageId == null) {
            return null;
        }
        return messages.stream()
                .filter(this::isUserAnswer)
                .filter(message -> parentMessageId.equals(message.getParentMessageId()))
                .findFirst()
                .orElse(null);
    }

    private String inferExposedRisk(InterviewMessage followUp) {
        String text = firstText(followUp == null ? null : followUp.getFollowUpReason(),
                followUp == null ? null : followUp.getContent());
        if (!StringUtils.hasText(text)) {
            return "Risk needs further verification.";
        }
        String lower = text.toLowerCase();
        if (lower.contains("risk") || lower.contains("fail") || lower.contains("rollback")) {
            return "Risk awareness may be insufficient.";
        }
        if (lower.contains("why") || lower.contains("how") || lower.contains("detail")) {
            return "Technical depth may need more evidence.";
        }
        return "Answer may need clearer evidence or boundary conditions.";
    }

    private boolean hasFollowUps(List<InterviewMessage> messages) {
        return messages != null && messages.stream()
                .anyMatch(message -> "AI".equalsIgnoreCase(message.getRole())
                        && "FOLLOW_UP".equalsIgnoreCase(message.getMessageType()));
    }

    private List<String> extractKnowledgePoints(List<InterviewMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .map(InterviewMessage::getKnowledgePoints)
                .filter(StringUtils::hasText)
                .flatMap(value -> List.of(value.split("[,，;；/\\s]+")).stream())
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String jsonArray(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private Integer averageAnswerScore(List<InterviewMessage> messages) {
        int total = 0;
        int count = 0;
        if (messages != null) {
            for (InterviewMessage message : messages) {
                Integer score = firstPositive(message.getAiScore(), message.getScore());
                if (score != null) {
                    total += score;
                    count++;
                }
            }
        }
        return count == 0 ? null : Math.round((float) total / count);
    }

    private Integer firstPositive(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private Integer firstValidTotalScore(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value >= 1 && value <= 100) {
                return value;
            }
        }
        return null;
    }

    private boolean isUserAnswer(InterviewMessage message) {
        return message != null
                && "USER".equalsIgnoreCase(message.getRole())
                && "ANSWER".equalsIgnoreCase(message.getMessageType())
                && StringUtils.hasText(firstText(message.getUserAnswer(), message.getContent()));
    }

    private InterviewMessage firstMessage(List<InterviewMessage> messages, Long parentMessageId, String role, String type) {
        if (messages == null || parentMessageId == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> parentMessageId.equals(message.getParentMessageId()))
                .filter(message -> role.equalsIgnoreCase(message.getRole()))
                .filter(message -> type.equalsIgnoreCase(message.getMessageType()))
                .findFirst()
                .orElse(null);
    }

    private String parentQuestionContent(List<InterviewMessage> messages, InterviewMessage answer) {
        if (messages == null || answer == null || answer.getParentMessageId() == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> answer.getParentMessageId().equals(message.getId()))
                .findFirst()
                .map(message -> firstText(message.getQuestionContent(), message.getContent()))
                .orElse(null);
    }

    private boolean aiReportMissingDisplayContent(GenerateReportVO aiReport) {
        return aiReport == null
                || !InterviewReportScoringContract.validate(
                        objectMapper,
                        aiReport.getTotalScore(),
                        InterviewRubricVersion.CURRENT,
                        aiReport.getRubricScores()).valid()
                || !StringUtils.hasText(aiReport.getSummary())
                || !StringUtils.hasText(aiReport.getReportContent());
    }

    private boolean hasExpectedQaReviews(GenerateReportVO aiReport, int answerCount) {
        if (answerCount <= 0 || aiReport == null) {
            return false;
        }
        int reviewCount = countJsonArrayItems(aiReport.getQaReview());
        return reviewCount == answerCount;
    }

    private int countScorableAnswers(List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return (int) messages.stream().filter(message ->
                "USER".equalsIgnoreCase(message.getRole())
                        && "ANSWER".equalsIgnoreCase(message.getMessageType())
                        && StringUtils.hasText(firstText(message.getUserAnswer(), message.getContent()))).count();
    }

    private int countJsonArrayItems(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isArray() ? node.size() : 0;
        } catch (Exception ex) {
            return 0;
        }
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
            dto.setAbilityProfileUpdatesJson(report.getAbilityProfileUpdates());
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

    private void markReportAiIncomplete(InterviewReport report) {
        report.setStatus(ReportStatusEnum.FAILED.name());
        report.setTotalScore(null);
        report.setSummary(REPORT_AI_INCOMPLETE_MESSAGE);
        report.setStageScores("{}");
        report.setWeakPoints("[]");
        report.setStrengths("[]");
        report.setWeaknesses(null);
        report.setMainProblems("[]");
        report.setProjectProblems("[]");
        report.setReviewSuggestions(REPORT_AI_INCOMPLETE_SUGGESTIONS);
        report.setRecommendedQuestions("[]");
        report.setQaReview("[]");
        report.setRubricScores("[]");
        report.setRubricVersion(null);
        report.setFollowUpTree("[]");
        report.setAdviceEvidence("[]");
        report.setAbilityProfileUpdates("[]");
        report.setReportContent(REPORT_AI_INCOMPLETE_MESSAGE);
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(REPORT_AI_INCOMPLETE_SUGGESTIONS);
        report.setFailureReason(REPORT_AI_INCOMPLETE_MESSAGE);
    }

    private void markReportSampleInsufficient(
            InterviewSession session, InterviewReport report, String generationToken) {
        report.setUserId(session.getUserId());
        report.setStatus(ReportStatusEnum.FAILED.name());
        report.setTotalScore(null);
        report.setSummary(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        report.setStageScores("{}");
        report.setWeakPoints("[]");
        report.setStrengths("[]");
        report.setWeaknesses(null);
        report.setMainProblems("[]");
        report.setProjectProblems("[]");
        report.setReviewSuggestions(REPORT_SAMPLE_INSUFFICIENT_SUGGESTIONS);
        report.setRecommendedQuestions("[]");
        report.setQaReview("[]");
        report.setRubricScores("[]");
        report.setRubricVersion(null);
        report.setFollowUpTree("[]");
        report.setAdviceEvidence("[]");
        report.setAbilityProfileUpdates("[]");
        report.setReportContent(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        report.setGeneratedAt(LocalDateTime.now());
        report.setSuggestions(REPORT_SAMPLE_INSUFFICIENT_SUGGESTIONS);
        report.setFailureReason(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        if (!updateCurrentReportAttempt(report, generationToken)) {
            return;
        }

        session.setStatus(InterviewStatusEnum.COMPLETED.name());
        session.setReportStatus(ReportStatusEnum.FAILED.name());
        session.setTotalScore(null);
        session.setEndTime(session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime());
        session.setFailureReason(REPORT_SAMPLE_INSUFFICIENT_MESSAGE);
        sessionMapper.updateById(session);
    }

    private boolean hasScorableAnswers(List<InterviewMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.stream().anyMatch(message ->
                "USER".equalsIgnoreCase(message.getRole())
                        && "ANSWER".equalsIgnoreCase(message.getMessageType())
                        && StringUtils.hasText(firstText(message.getUserAnswer(), message.getContent())));
    }

    private void saveReport(InterviewReport report) {
        if (report.getId() == null) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
    }

    private boolean updateCurrentReportAttempt(InterviewReport report, String generationToken) {
        if (report == null || report.getId() == null || report.getSessionId() == null) {
            return false;
        }
        LambdaUpdateWrapper<InterviewReport> wrapper = new LambdaUpdateWrapper<InterviewReport>()
                .eq(InterviewReport::getId, report.getId())
                .eq(InterviewReport::getSessionId, report.getSessionId())
                .eq(InterviewReport::getStatus, ReportStatusEnum.GENERATING.name())
                .eq(InterviewReport::getDeleted, CommonConstants.NO);
        if (StringUtils.hasText(generationToken)) {
            wrapper.eq(InterviewReport::getGenerationToken, generationToken);
        } else {
            wrapper.isNull(InterviewReport::getGenerationToken);
        }
        return reportMapper.update(report, wrapper) == 1;
    }

    private void completeAgentInterviewTask(InterviewSession session, InterviewReport report) {
        if (session == null || !InterviewReportTrustPolicy.isTrustedForFormalAction(report)) {
            return;
        }
        agentBusinessActionNotifier.completeInterviewReport(session.getUserId(), session.getTargetJobId(),
                report.getId());
    }

    private void syncInterviewSearchAfterCommit(Long sessionId, Long userId) {
        String op = "UPSERT";
        Runnable action = () -> {
            if (!interviewMqDispatcher.dispatchInterviewSearchUpsert(sessionId, userId)) {
                log.warn("Interview async after-commit sync returned false syncType=interview_search_sync sessionId={} op={}",
                        sessionId, op);
            }
        };
        Runnable safeAction = () -> runAfterCommitSafely("interview_search_sync", sessionId, op, action);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }

    private void runAfterCommitSafely(String syncType, Long sessionId, String op, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("Interview async after-commit sync failed syncType={} sessionId={} op={} reason={}",
                    syncType, sessionId, op, ex.getMessage(), ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private List<String> readStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(value, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> readLongList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            List<Long> list = objectMapper.readValue(value, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
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
