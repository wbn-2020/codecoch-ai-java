package com.codecoachai.interview.convert;

import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewStage;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.InterviewMessageVO;
import com.codecoachai.interview.domain.vo.InterviewReportNextActionVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.InterviewStageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class InterviewConvert {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPORT_SOURCE_TYPE = "INTERVIEW_REPORT";
    private static final String TRUST_VERIFIED = "VERIFIED";
    private static final String TRUST_PARTIAL = "PARTIAL";
    private static final String TRUST_FALLBACK = "FALLBACK";
    private static final String BIZ_INTERVIEW_REPORT = "INTERVIEW_REPORT";
    private static final String BIZ_INTERVIEW_SESSION = "INTERVIEW_SESSION";
    private static final String FALLBACK_REPORT_REASON_KEYWORD = "保底复盘";

    private InterviewConvert() {
    }

    public static InterviewStageVO toStageVO(InterviewStage stage) {
        if (stage == null) {
            return null;
        }
        InterviewStageVO vo = new InterviewStageVO();
        vo.setId(stage.getId());
        vo.setStageType(stage.getStageType());
        vo.setStageName(stage.getStageName());
        vo.setSort(stage.getSort());
        vo.setStageOrder(stage.getStageOrder());
        vo.setExpectedQuestionCount(stage.getExpectedQuestionCount());
        vo.setAskedQuestionCount(stage.getAskedQuestionCount());
        vo.setFocusPoints(stage.getFocusPoints());
        vo.setBasedOnResume(stage.getBasedOnResume());
        vo.setAllowFollowUp(stage.getAllowFollowUp());
        vo.setMaxFollowUpCount(stage.getMaxFollowUpCount());
        vo.setStatus(stage.getStatus());
        vo.setScore(stage.getScore());
        return vo;
    }

    public static InterviewListVO toListVO(InterviewSession session) {
        InterviewListVO vo = new InterviewListVO();
        vo.setId(session.getId());
        vo.setApplicationId(session.getApplicationId());
        vo.setTargetJobId(session.getTargetJobId());
        vo.setSkillProfileId(session.getSkillProfileId());
        vo.setMatchReportId(session.getMatchReportId());
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
        vo.setIndustryTemplateId(session.getIndustryTemplateId());
        vo.setIndustryDirection(session.getIndustryDirection());
        vo.setDifficulty(session.getDifficulty());
        vo.setInterviewerStyle(session.getInterviewerStyle());
        vo.setBasedOnResume(session.getBasedOnResume());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setAnsweredQuestionCount(session.getAnsweredQuestionCount());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    public static InterviewMessageVO toMessageVO(InterviewMessage message) {
        InterviewMessageVO vo = new InterviewMessageVO();
        vo.setId(message.getId());
        vo.setStageId(message.getStageId());
        vo.setQuestionId(message.getQuestionId());
        vo.setQuestionGroupId(message.getQuestionGroupId());
        vo.setParentMessageId(message.getParentMessageId());
        vo.setRole(message.getRole());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setQuestionContent(message.getQuestionContent());
        vo.setUserAnswer(message.getUserAnswer());
        vo.setAiComment(message.getAiComment());
        vo.setAiScore(message.getAiScore());
        vo.setIsFollowUp(message.getIsFollowUp());
        vo.setFollowUpCount(message.getFollowUpCount());
        vo.setFollowUpReason(message.getFollowUpReason());
        vo.setKnowledgePoints(message.getKnowledgePoints());
        vo.setScore(message.getScore());
        vo.setComment(message.getComment());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    public static InterviewReportVO toReportVO(InterviewReport report) {
        if (report == null) {
            return null;
        }
        InterviewReportVO vo = new InterviewReportVO();
        vo.setId(report.getId());
        vo.setSessionId(report.getSessionId());
        vo.setUserId(report.getUserId());
        vo.setStatus(report.getStatus());
        vo.setTotalScore(report.getTotalScore());
        vo.setSummary(report.getSummary());
        Map<String, Object> stageScores = parseMap(report.getStageScores());
        vo.setStageScores(stageScores);
        vo.setStageReports(stageScores);
        vo.setWeakPoints(parseStringList(report.getWeakPoints()));
        vo.setStrengths(parseStringListOrSingle(report.getStrengths()));
        List<String> mainProblems = parseStringListOrSingle(firstText(report.getMainProblems(), report.getWeaknesses()));
        List<String> projectProblems = parseStringList(report.getProjectProblems());
        List<String> recommendedQuestions = parseStringList(report.getRecommendedQuestions());
        vo.setMainProblems(mainProblems);
        vo.setProjectProblems(projectProblems);
        vo.setReviewSuggestions(parseStringListOrSingle(firstText(report.getReviewSuggestions(), report.getSuggestions())));
        vo.setRecommendedQuestions(recommendedQuestions);
        vo.setNextActions(buildNextActions(report, recommendedQuestions, mainProblems, projectProblems));
        vo.setQuestionReviews(parseObjectList(report.getQaReview()));
        vo.setReportContent(report.getReportContent());
        vo.setGeneratedAt(report.getGeneratedAt());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setFailureReason(report.getFailureReason());
        vo.setSourceType(REPORT_SOURCE_TYPE);
        vo.setSourceId(report.getId());
        vo.setTrustStatus(reportTrustStatus(report));
        vo.setEvidenceSummary(reportEvidenceSummary(report));
        vo.setFallback(reportFallback(report));
        return vo;
    }

    private static List<InterviewReportNextActionVO> buildNextActions(
            InterviewReport report,
            List<String> recommendedQuestions,
            List<String> mainProblems,
            List<String> projectProblems) {
        if (!shouldExposeNextActions(report)) {
            return Collections.emptyList();
        }
        List<InterviewReportNextActionVO> actions = new ArrayList<>();
        if (!recommendedQuestions.isEmpty()) {
            String actionUrl = "/questions/practice?mode=recommended&source=interviewReport";
            if (report.getId() != null) {
                actionUrl += "&reportId=" + report.getId();
            }
            actions.add(nextAction(
                    "QUESTION_PRACTICE",
                    "练推荐题",
                    "优先完成报告推荐题，把暴露出的薄弱点转成下一轮练习。",
                    actionUrl,
                    BIZ_INTERVIEW_REPORT,
                    report.getId(),
                    firstEvidence(recommendedQuestions)));
        }
        if (report.getId() != null) {
            actions.add(nextAction(
                    "STUDY_PLAN",
                    "生成学习计划",
                    "把本次复盘建议转成可跟踪的学习任务，避免报告停留在阅读层面。",
                    "/study-plans?source=interviewReport&reportId=" + report.getId(),
                    BIZ_INTERVIEW_REPORT,
                    report.getId(),
                    firstText(report.getSummary(), report.getReportContent())));
        }
        if (report.getSessionId() != null) {
            String actionUrl = "/interviews/create";
            if (report.getId() != null) {
                actionUrl += "?source=interviewReport&reportId=" + report.getId() + "&interviewId=" + report.getSessionId();
            }
            actions.add(nextAction(
                    "INTERVIEW",
                    "开启下一轮模拟面试",
                    "用同一次复盘结论开启下一轮面试，验证表达和知识点是否真正补齐。",
                    actionUrl,
                    BIZ_INTERVIEW_SESSION,
                    report.getSessionId(),
                    reportEvidenceSummary(report)));
        }
        if (!mainProblems.isEmpty() || !projectProblems.isEmpty()) {
            actions.add(nextAction(
                    "RESUME_OPTIMIZE",
                    "优化简历与项目表达",
                    "把主要问题和项目问题补回简历、项目经历和面试话术。",
                    report.getId() == null ? "/resumes" : "/resumes?source=interviewReport&reportId=" + report.getId(),
                    BIZ_INTERVIEW_REPORT,
                    report.getId(),
                    firstEvidence(!mainProblems.isEmpty() ? mainProblems : projectProblems)));
        }
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setPriority(i + 1);
        }
        return actions;
    }

    private static InterviewReportNextActionVO nextAction(
            String actionType,
            String title,
            String description,
            String actionUrl,
            String relatedBizType,
            Long relatedBizId,
            String evidence) {
        InterviewReportNextActionVO action = new InterviewReportNextActionVO();
        action.setActionType(actionType);
        action.setTitle(title);
        action.setDescription(description);
        action.setActionUrl(actionUrl);
        action.setRelatedBizType(relatedBizType);
        action.setRelatedBizId(relatedBizId);
        action.setEvidence(evidence);
        return action;
    }

    private static boolean shouldExposeNextActions(InterviewReport report) {
        if (report == null || isFallbackReport(report)) {
            return false;
        }
        String status = report.getStatus();
        return "GENERATED".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status);
    }

    private static String reportTrustStatus(InterviewReport report) {
        if (report == null) {
            return TRUST_PARTIAL;
        }
        String status = report.getStatus();
        if ("FAILED".equalsIgnoreCase(status) || "UNSCORABLE".equalsIgnoreCase(status)) {
            return TRUST_FALLBACK;
        }
        if (isFallbackReport(report)) {
            return TRUST_FALLBACK;
        }
        boolean generated = "GENERATED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
        boolean hasScore = report.getTotalScore() != null && report.getTotalScore() > 0;
        boolean hasContent = StringUtils.hasText(report.getReportContent())
                || StringUtils.hasText(report.getSummary())
                || StringUtils.hasText(report.getQaReview());
        return generated && hasScore && hasContent ? TRUST_VERIFIED : TRUST_PARTIAL;
    }

    private static Boolean reportFallback(InterviewReport report) {
        if (report == null || report.getSessionId() == null) {
            return true;
        }
        String status = report.getStatus();
        return "FAILED".equalsIgnoreCase(status) || "UNSCORABLE".equalsIgnoreCase(status) || isFallbackReport(report);
    }

    private static String reportEvidenceSummary(InterviewReport report) {
        if (report == null) {
            return "面试报告缺少上下文证据。";
        }
        String reportId = report.getId() == null ? "未落库" : "#" + report.getId();
        String sessionId = report.getSessionId() == null ? "未绑定面试" : "#" + report.getSessionId();
        String status = firstText(report.getStatus(), "状态待确认");
        if ("FAILED".equalsIgnoreCase(report.getStatus()) || "UNSCORABLE".equalsIgnoreCase(report.getStatus()) || isFallbackReport(report)) {
            return "来自面试 " + sessionId + " · 报告 " + reportId + " · " + status + "："
                    + firstText(report.getFailureReason(), "原因待排查");
        }
        String score = report.getTotalScore() == null ? "评分待确认" : "综合得分 " + report.getTotalScore() + " 分";
        String generatedAt = report.getGeneratedAt() == null ? "生成时间待确认" : "生成于 " + report.getGeneratedAt();
        return "来自面试 " + sessionId + " · 报告 " + reportId + " · " + score + " · " + generatedAt;
    }

    private static Map<String, Object> parseMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private static List<String> parseStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        try {
            List<String> list = OBJECT_MAPPER.readValue(value, new TypeReference<>() {
            });
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private static List<String> parseStringListOrSingle(String value) {
        List<String> parsed = parseStringList(value);
        if (!parsed.isEmpty() || !StringUtils.hasText(value)) {
            return parsed;
        }
        return List.of(value);
    }

    private static List<Map<String, Object>> parseObjectList(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> list = OBJECT_MAPPER.readValue(value, new TypeReference<>() {
            });
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private static boolean isFallbackReport(InterviewReport report) {
        return report != null
                && StringUtils.hasText(report.getFailureReason())
                && report.getFailureReason().contains(FALLBACK_REPORT_REASON_KEYWORD);
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String firstEvidence(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }
}
