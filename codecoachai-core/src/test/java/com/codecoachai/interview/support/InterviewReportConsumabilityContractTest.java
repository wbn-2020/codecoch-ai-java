package com.codecoachai.interview.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.interview.domain.entity.InterviewReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InterviewReportConsumabilityContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCompleteReportWithAtLeastSixReviewsAndOneReviewPerAnswer() {
        InterviewReportConsumabilityContract.Validation result =
                InterviewReportConsumabilityContract.validate(
                        objectMapper, completeReport(), 6, null);

        assertTrue(result.valid());
    }

    @Test
    void rejectsCompleteLookingReportWhenFewerThanSixAnswersExist() {
        InterviewReportConsumabilityContract.Validation result =
                InterviewReportConsumabilityContract.validate(
                        objectMapper, completeReport(), 5, null);

        assertEquals("ANSWER_EVIDENCE_INSUFFICIENT", result.reasonCode());
    }

    @Test
    void rejectsReportWithPlaceholderProblemsEvenWhenScoreIsValid() {
        InterviewReport report = completeReport();
        report.setMainProblems("[]");

        InterviewReportConsumabilityContract.Validation result =
                InterviewReportConsumabilityContract.validate(objectMapper, report, 6, null);

        assertEquals("MAIN_PROBLEMS_MISSING", result.reasonCode());
    }

    @Test
    void rejectsQaReviewThatDoesNotPreserveEveryAnswer() {
        InterviewReport report = completeReport();
        report.setQaReview("[{\"question\":\"Q1\",\"answer\":\"A1\",\"score\":80,\"comment\":\"C1\"}]");

        InterviewReportConsumabilityContract.Validation result =
                InterviewReportConsumabilityContract.validate(objectMapper, report, 6, null);

        assertEquals("QA_REVIEW_COUNT_MISMATCH", result.reasonCode());
    }

    private InterviewReport completeReport() {
        InterviewReport report = new InterviewReport();
        report.setTotalScore(82);
        report.setRubricVersion(InterviewRubricVersion.CURRENT);
        report.setRubricScores("""
                [{"dimension":"EXPRESSION_STRUCTURE","score":4.1},
                 {"dimension":"TECHNICAL_DEPTH","score":4.1},
                 {"dimension":"BUSINESS_UNDERSTANDING","score":4.1},
                 {"dimension":"RISK_AWARENESS","score":4.1},
                 {"dimension":"IMPLEMENTABILITY","score":4.1}]
                """.replaceAll("\\s+", ""));
        report.setStrengths("[\"表达清晰\"]");
        report.setWeaknesses("缓存一致性边界说明不够完整");
        report.setMainProblems("[\"缺少失败场景分析\"]");
        report.setReviewSuggestions("[\"补充缓存一致性案例\"]");
        report.setReportContent("报告正文");
        report.setQaReview("""
                [{"question":"Q1","answer":"A1","score":80,"comment":"C1"},
                 {"questionContent":"Q2","userAnswer":"A2","aiScore":84,"aiComment":"C2"},
                 {"question":"Q3","answer":"A3","score":81,"comment":"C3"},
                 {"questionContent":"Q4","userAnswer":"A4","aiScore":83,"aiComment":"C4"},
                 {"question":"Q5","answer":"A5","score":82,"comment":"C5"},
                 {"questionContent":"Q6","userAnswer":"A6","aiScore":85,"aiComment":"C6"}]
                """.replaceAll("\\s+", ""));
        report.setGeneratedAt(LocalDateTime.now());
        return report;
    }
}
