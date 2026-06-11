package com.codecoachai.interview.convert;

import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewStage;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.InterviewMessageVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.InterviewStageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        vo.setMainProblems(parseStringListOrSingle(firstText(report.getMainProblems(), report.getWeaknesses())));
        vo.setProjectProblems(parseStringList(report.getProjectProblems()));
        vo.setReviewSuggestions(parseStringListOrSingle(firstText(report.getReviewSuggestions(), report.getSuggestions())));
        vo.setRecommendedQuestions(parseStringList(report.getRecommendedQuestions()));
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

    private static String reportTrustStatus(InterviewReport report) {
        if (report == null) {
            return TRUST_PARTIAL;
        }
        String status = report.getStatus();
        if ("FAILED".equalsIgnoreCase(status) || "UNSCORABLE".equalsIgnoreCase(status)) {
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
        return "FAILED".equalsIgnoreCase(status) || "UNSCORABLE".equalsIgnoreCase(status);
    }

    private static String reportEvidenceSummary(InterviewReport report) {
        if (report == null) {
            return "面试报告缺少上下文证据。";
        }
        String reportId = report.getId() == null ? "未落库" : "#" + report.getId();
        String sessionId = report.getSessionId() == null ? "未绑定面试" : "#" + report.getSessionId();
        String status = firstText(report.getStatus(), "状态待确认");
        if ("FAILED".equalsIgnoreCase(report.getStatus()) || "UNSCORABLE".equalsIgnoreCase(report.getStatus())) {
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

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
