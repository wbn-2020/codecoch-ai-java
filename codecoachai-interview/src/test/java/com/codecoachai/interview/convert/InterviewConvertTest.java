package com.codecoachai.interview.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.codecoachai.interview.domain.vo.InterviewReportNextActionVO;
import com.codecoachai.interview.domain.vo.InterviewReportVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Test
    void generatedReportExposesRubricFollowUpTreeAndAdviceEvidence() {
        InterviewReport report = generatedReport();
        report.setRubricScores("""
                [
                  {"dimension":"EXPRESSION_STRUCTURE","score":4,"comment":"结构清晰","evidenceSummary":"按背景-行动-结果表达","improvementSuggestion":"补充量化结果"},
                  {"dimension":"TECHNICAL_DEPTH","score":3,"comment":"深度一般","evidenceSummary":"Redis 一致性追问解释不足","improvementSuggestion":"补充失败补偿"}
                ]
                """);
        report.setFollowUpTree("""
                [
                  {"questionMessageId":10,"answerMessageId":11,"followUpMessageId":12,"followUpIntent":"VERIFY_RISK","followUpReason":"缺少缓存一致性策略","exposedRisk":"风险意识不足"}
                ]
                """);
        report.setAdviceEvidence("""
                [
                  {"title":"补齐 Redis 缓存一致性","adviceType":"PRACTICE_SKILL","confidence":"HIGH","sampleInsufficient":false,"evidenceSources":[{"sourceType":"INTERVIEW_ANSWER","sourceId":11,"sourceSummary":"一致性回答不完整"}],"feedbackStatus":"NONE"}
                ]
                """);
        report.setAbilityProfileUpdates("""
                [
                  {"skillCode":"REDIS_CACHE","candidateStatus":"BASIC","confidence":"MEDIUM","evidenceCount":1,"sampleInsufficient":true}
                ]
                """);

        InterviewReportVO vo = InterviewConvert.toReportVO(report);

        assertEquals(2, vo.getRubricScores().size());
        assertEquals("TECHNICAL_DEPTH", vo.getRubricScores().get(1).get("dimension"));
        assertEquals(1, vo.getFollowUpTree().size());
        assertEquals("VERIFY_RISK", vo.getFollowUpTree().get(0).get("followUpIntent"));
        assertEquals(1, vo.getAdviceEvidence().size());
        assertEquals("HIGH", vo.getAdviceEvidence().get(0).get("confidence"));
        assertEquals(1, vo.getAbilityProfileUpdates().size());
        assertEquals(true, vo.getAbilityProfileUpdates().get(0).get("sampleInsufficient"));
    }

    @Test
    void generatedReportBuildsEvidenceBackedNextActionsFromAdviceEvidence() {
        InterviewReport report = generatedReport();
        report.setAdviceEvidence("""
                [
                  {"title":"补充项目量化结果","content":"把查询耗时和 QPS 写回项目素材","adviceType":"PROJECT_EVIDENCE","confidence":"MEDIUM","sampleInsufficient":true,"sampleWarning":"样本不足，仅基于 1 次训练","actionUrl":"/project-evidence","evidenceSources":[{"sourceType":"INTERVIEW_REPORT","sourceId":88,"sourceSummary":"可落地性 2/5"}]}
                ]
                """);

        List<InterviewReportNextActionVO> actions = InterviewConvert.toReportVO(report).getNextActions();

        assertTrue(actions.stream().anyMatch(action -> "AI_ADVICE".equals(action.getActionType())
                && "补充项目量化结果".equals(action.getTitle())
                && "INTERVIEW_REPORT".equals(action.getRelatedBizType())
                && action.getEvidence().contains("可落地性 2/5")));
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
