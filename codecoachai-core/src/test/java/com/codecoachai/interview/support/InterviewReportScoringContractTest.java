package com.codecoachai.interview.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InterviewReportScoringContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void currentRubricAcceptsStableRequiredDimensionsAndConsistentTotal() {
        InterviewReportScoringContract.Validation validation =
                InterviewReportScoringContract.validate(
                        objectMapper,
                        82,
                        InterviewRubricVersion.CURRENT,
                        formalRubricScores(4.1));

        assertTrue(validation.valid());
    }

    @Test
    void currentRubricRejectsSingleDimensionRecoveryAsIncomplete() {
        InterviewReportScoringContract.Validation validation =
                InterviewReportScoringContract.validate(
                        objectMapper,
                        84,
                        InterviewRubricVersion.CURRENT,
                        """
                        [{"dimension":"ANSWER_QUALITY","score":4.2}]
                        """);

        assertEquals("RUBRIC_DIMENSION_MISMATCH", validation.reasonCode());
    }

    @Test
    void currentRubricRejectsTotalThatDoesNotMatchDimensionAverage() {
        InterviewReportScoringContract.Validation validation =
                InterviewReportScoringContract.validate(
                        objectMapper,
                        70,
                        InterviewRubricVersion.CURRENT,
                        formalRubricScores(4.1));

        assertEquals("TOTAL_SCORE_MISMATCH", validation.reasonCode());
    }

    @Test
    void scenarioRubricRejectsMissingAndExtraDimensions() {
        String dimensions = """
                [{"code":"TECHNICAL_DEPTH","weight":60},
                 {"code":"RISK_AWARENESS","weight":40}]
                """;

        InterviewReportScoringContract.Validation missing =
                InterviewReportScoringContract.validate(
                        objectMapper, 80, "scenario:1:rubric:9",
                        "[{\"dimension\":\"TECHNICAL_DEPTH\",\"score\":4}]",
                        dimensions);
        InterviewReportScoringContract.Validation extra =
                InterviewReportScoringContract.validate(
                        objectMapper, 70, "scenario:1:rubric:9",
                        """
                        [{"dimension":"TECHNICAL_DEPTH","score":4},
                         {"dimension":"RISK_AWARENESS","score":4},
                         {"dimension":"EXTRA","score":4}]
                        """,
                        dimensions);

        assertEquals("RUBRIC_DIMENSION_MISMATCH", missing.reasonCode());
        assertEquals("RUBRIC_DIMENSION_MISMATCH", extra.reasonCode());
    }

    @Test
    void scenarioRubricRejectsTotalThatDoesNotMatchWeightedDimensions() {
        int inconsistentTotalScore = 70;
        InterviewReportScoringContract.Validation validation =
                InterviewReportScoringContract.validate(
                        objectMapper, inconsistentTotalScore, "scenario:1:rubric:9",
                        """
                        [{"dimension":"TECHNICAL_DEPTH","score":5},
                         {"dimension":"RISK_AWARENESS","score":1}]
                        """,
                        """
                        [{"code":"TECHNICAL_DEPTH","weight":75},
                         {"code":"RISK_AWARENESS","weight":25}]
                        """);

        assertEquals("TOTAL_SCORE_MISMATCH", validation.reasonCode());
    }

    private String formalRubricScores(double score) {
        return """
                [{"dimension":"EXPRESSION_STRUCTURE","score":%s},
                 {"dimension":"TECHNICAL_DEPTH","score":%s},
                 {"dimension":"BUSINESS_UNDERSTANDING","score":%s},
                 {"dimension":"RISK_AWARENESS","score":%s},
                 {"dimension":"IMPLEMENTABILITY","score":%s}]
                """.formatted(score, score, score, score, score);
    }
}
