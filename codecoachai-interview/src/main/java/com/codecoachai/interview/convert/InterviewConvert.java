package com.codecoachai.interview.convert;

import com.codecoachai.interview.domain.entity.InterviewMessage;
import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.entity.InterviewSession;
import com.codecoachai.interview.domain.entity.InterviewStage;
import com.codecoachai.interview.domain.vo.InterviewListVO;
import com.codecoachai.interview.domain.vo.InterviewMessageVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import com.codecoachai.interview.domain.vo.InterviewStageVO;

public final class InterviewConvert {

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
        vo.setStatus(stage.getStatus());
        return vo;
    }

    public static InterviewListVO toListVO(InterviewSession session) {
        InterviewListVO vo = new InterviewListVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setMode(session.getMode());
        vo.setStatus(session.getStatus());
        vo.setReportStatus(session.getReportStatus());
        vo.setAnsweredQuestionCount(session.getAnsweredQuestionCount());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    public static InterviewMessageVO toMessageVO(InterviewMessage message) {
        InterviewMessageVO vo = new InterviewMessageVO();
        vo.setId(message.getId());
        vo.setQuestionId(message.getQuestionId());
        vo.setRole(message.getRole());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
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
        vo.setStatus(report.getStatus());
        vo.setTotalScore(report.getTotalScore());
        vo.setSummary(report.getSummary());
        vo.setStrengths(report.getStrengths());
        vo.setWeaknesses(report.getWeaknesses());
        vo.setSuggestions(report.getSuggestions());
        vo.setFailureReason(report.getFailureReason());
        return vo;
    }
}
