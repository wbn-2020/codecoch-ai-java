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
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setTargetPosition(session.getTargetPosition());
        vo.setExperienceLevel(session.getExperienceLevel());
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
        return vo;
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
