package com.codecoachai.interview.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.vo.InterviewReportNextActionVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewConvertTest {

    @Test
    void generatedReportBuildsDeterministicNextActionsFromReportData() {
        InterviewReport report = generatedReport();
        report.setRecommendedQuestions("[\"Redis 缓存穿透怎么处理？\"]");
        report.setMainProblems("[\"回答缺少量化指标\"]");

        InterviewReportVO vo = InterviewConvert.toReportVO(report);

        assertNotNull(vo.getNextActions());
        assertEquals(List.of("QUESTION_PRACTICE", "STUDY_PLAN", "INTERVIEW", "RESUME_OPTIMIZE"),
                vo.getNextActions().stream().map(InterviewReportNextActionVO::getActionType).toList());
        assertEquals(List.of(1, 2, 3, 4),
                vo.getNextActions().stream().map(InterviewReportNextActionVO::getPriority).toList());
        assertEquals(List.of("INTERVIEW_REPORT", "INTERVIEW_REPORT", "INTERVIEW_SESSION", "INTERVIEW_REPORT"),
                vo.getNextActions().stream().map(InterviewReportNextActionVO::getRelatedBizType).toList());
        assertEquals(88L, vo.getNextActions().get(0).getRelatedBizId());
        assertEquals("/questions/practice?mode=recommended&source=interviewReport&reportId=88",
                vo.getNextActions().get(0).getActionUrl());
        assertEquals("/study-plans?source=interviewReport&reportId=88", vo.getNextActions().get(1).getActionUrl());
        assertEquals("/interviews/create?source=interviewReport&reportId=88&interviewId=66",
                vo.getNextActions().get(2).getActionUrl());
        assertEquals("/resumes?source=interviewReport&reportId=88", vo.getNextActions().get(3).getActionUrl());
        assertTrue(vo.getNextActions().get(0).getEvidence().contains("Redis 缓存穿透"));
        assertTrue(vo.getNextActions().get(3).getEvidence().contains("回答缺少量化指标"));
    }

    @Test
    void generatedReportWithoutQuestionsStillIncludesStudyPlanAndInterviewActions() {
        InterviewReport report = generatedReport();

        InterviewReportVO vo = InterviewConvert.toReportVO(report);

        assertEquals(List.of("STUDY_PLAN", "INTERVIEW"),
                vo.getNextActions().stream().map(InterviewReportNextActionVO::getActionType).toList());
    }

    @Test
    void failedOrUnscorableReportsDoNotExposeNextActions() {
        InterviewReport failed = generatedReport();
        failed.setStatus("FAILED");
        InterviewReport unscorable = generatedReport();
        unscorable.setStatus("UNSCORABLE");

        assertTrue(InterviewConvert.toReportVO(failed).getNextActions().isEmpty());
        assertTrue(InterviewConvert.toReportVO(unscorable).getNextActions().isEmpty());
    }

    private static InterviewReport generatedReport() {
        InterviewReport report = new InterviewReport();
        report.setId(88L);
        report.setSessionId(66L);
        report.setUserId(5L);
        report.setStatus("GENERATED");
        report.setTotalScore(76);
        report.setSummary("整体表现稳定，下一轮需要补强项目表达。");
        report.setReportContent("结构化复盘内容");
        report.setGeneratedAt(LocalDateTime.of(2026, 6, 17, 12, 30));
        return report;
    }
}
