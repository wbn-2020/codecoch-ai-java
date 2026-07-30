package com.codecoachai.resume.service.support;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.domain.entity.ResumeJobMatchReport;
import com.codecoachai.resume.domain.enums.ResumeJobMatchStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ResumeJobMatchTrustPolicyTest {

    private final ResumeJobMatchTrustPolicy policy =
            new ResumeJobMatchTrustPolicy(new ObjectMapper());

    @Test
    void schemaWarningRequiresReviewWithoutBecomingFallback() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setRawResultJson("""
                {
                  "trustStatus": "VERIFIED",
                  "fallback": false,
                  "schemaWarnings": [
                    {
                      "field": "evidenceBoundary",
                      "message": "unsupported evidence removed"
                    }
                  ]
                }
                """);

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("PARTIAL", assessment.trustStatus()),
                () -> assertTrue(assessment.requiresReview()),
                () -> assertFalse(assessment.fallback()),
                () -> assertFalse(assessment.trustedSuccess()),
                () -> assertEquals(1, assessment.schemaWarningCount()));
    }

    @Test
    void rawPartialWithoutWarningsStillRequiresReview() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setRawResultJson("""
                {"trustStatus":"PARTIAL","fallback":false,"schemaWarnings":[]}
                """);

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("PARTIAL", assessment.trustStatus()),
                () -> assertTrue(assessment.requiresReview()),
                () -> assertFalse(assessment.fallback()),
                () -> assertFalse(assessment.trustedSuccess()));
    }

    @Test
    void rawFallbackFlagIsFallback() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setRawResultJson("""
                {"trustStatus":"VERIFIED","fallback":true,"schemaWarnings":[]}
                """);

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("FALLBACK", assessment.trustStatus()),
                () -> assertTrue(assessment.fallback()),
                () -> assertTrue(assessment.requiresReview()),
                () -> assertFalse(assessment.trustedSuccess()));
    }

    @Test
    void rawFallbackTrustStatusIsFallback() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setRawResultJson("""
                {"trustStatus":"FALLBACK","fallback":false,"schemaWarnings":[]}
                """);

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("FALLBACK", assessment.trustStatus()),
                () -> assertTrue(assessment.fallback()),
                () -> assertTrue(assessment.requiresReview()),
                () -> assertFalse(assessment.trustedSuccess()));
    }

    @Test
    void generationFailureIsFallback() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setStatus(ResumeJobMatchStatus.FAILED.getCode());
        report.setRawResultJson(null);

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("FALLBACK", assessment.trustStatus()),
                () -> assertTrue(assessment.fallback()),
                () -> assertTrue(assessment.requiresReview()),
                () -> assertFalse(assessment.trustedSuccess()));
    }

    @Test
    void cleanSuccessfulReportIsVerifiedAndTrusted() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setRawResultJson("""
                {"trustStatus":"VERIFIED","fallback":false,"schemaWarnings":[]}
                """);

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("VERIFIED", assessment.trustStatus()),
                () -> assertFalse(assessment.fallback()),
                () -> assertFalse(assessment.requiresReview()),
                () -> assertTrue(assessment.trustedSuccess()));
    }

    @Test
    void zeroOverallScoreIsTrustedButMissingOrOutOfRangeScoresArePartial() {
        ResumeJobMatchReport zero = trustedSuccessReport();
        zero.setOverallScore(0);
        ResumeJobMatchReport negative = trustedSuccessReport();
        negative.setOverallScore(-1);
        ResumeJobMatchReport aboveMaximum = trustedSuccessReport();
        aboveMaximum.setOverallScore(101);
        ResumeJobMatchReport missing = trustedSuccessReport();
        missing.setOverallScore(null);

        ResumeJobMatchTrustPolicy.Assessment zeroAssessment = policy.assess(zero);
        ResumeJobMatchTrustPolicy.Assessment negativeAssessment = policy.assess(negative);
        ResumeJobMatchTrustPolicy.Assessment aboveMaximumAssessment = policy.assess(aboveMaximum);
        ResumeJobMatchTrustPolicy.Assessment missingAssessment = policy.assess(missing);

        assertAll(
                () -> assertEquals("VERIFIED", zeroAssessment.trustStatus()),
                () -> assertTrue(zeroAssessment.trustedSuccess()),
                () -> assertEquals("PARTIAL", negativeAssessment.trustStatus()),
                () -> assertFalse(negativeAssessment.trustedSuccess()),
                () -> assertEquals("PARTIAL", aboveMaximumAssessment.trustStatus()),
                () -> assertFalse(aboveMaximumAssessment.trustedSuccess()),
                () -> assertEquals("PARTIAL", missingAssessment.trustStatus()),
                () -> assertFalse(missingAssessment.trustedSuccess()));
    }

    @Test
    void persistedArrayWarningsMergeWithRawWarningsAndDowngradeAssessment() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setRawResultJson("""
                {
                  "trustStatus":"VERIFIED",
                  "fallback":false,
                  "schemaWarnings":[{"field":"rawResult","message":"source warning"}]
                }
                """);
        report.setStrengthsJson("{not-json");
        report.setGapsJson("{}");
        report.setResumeRisksJson("null");
        report.setOptimizationSuggestionsJson("\"not-an-array\"");
        report.setRecommendedLearningTopicsJson("42");
        report.setRecommendedInterviewTopicsJson("true");

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("PARTIAL", assessment.trustStatus()),
                () -> assertFalse(assessment.fallback()),
                () -> assertTrue(assessment.requiresReview()),
                () -> assertFalse(assessment.trustedSuccess()),
                () -> assertEquals(7, assessment.schemaWarningCount()),
                () -> assertEquals("rawResult",
                        assessment.schemaWarnings().path(0).path("field").asText()),
                () -> assertEquals("strengths",
                        assessment.schemaWarnings().path(1).path("field").asText()),
                () -> assertEquals("recommendedInterviewTopics",
                        assessment.schemaWarnings().path(6).path("field").asText()));
    }

    @Test
    void allNonEmptyPersistedArrayFieldsAcceptValidJsonArrays() {
        ResumeJobMatchReport report = trustedSuccessReport();
        report.setStrengthsJson("[]");
        report.setGapsJson("[{}]");
        report.setResumeRisksJson("[\"risk\"]");
        report.setOptimizationSuggestionsJson("[\"suggestion\"]");
        report.setRecommendedLearningTopicsJson("[\"topic\"]");
        report.setRecommendedInterviewTopicsJson("[\"interview\"]");

        ResumeJobMatchTrustPolicy.Assessment assessment = policy.assess(report);

        assertAll(
                () -> assertEquals("VERIFIED", assessment.trustStatus()),
                () -> assertTrue(assessment.trustedSuccess()),
                () -> assertEquals(0, assessment.schemaWarningCount()));
    }

    @Test
    void processingMalformedOrIncompleteResultIsPartialButNotFallback() {
        ResumeJobMatchReport processing = trustedSuccessReport();
        processing.setStatus(ResumeJobMatchStatus.PROCESSING.getCode());
        ResumeJobMatchReport malformed = trustedSuccessReport();
        malformed.setRawResultJson("{not-json");
        ResumeJobMatchReport incomplete = trustedSuccessReport();
        incomplete.setResumeId(null);

        ResumeJobMatchTrustPolicy.Assessment processingAssessment = policy.assess(processing);
        ResumeJobMatchTrustPolicy.Assessment malformedAssessment = policy.assess(malformed);
        ResumeJobMatchTrustPolicy.Assessment incompleteAssessment = policy.assess(incomplete);
        ResumeJobMatchTrustPolicy.Assessment missingAssessment = policy.assess(null);

        assertAll(
                () -> assertEquals("PARTIAL", processingAssessment.trustStatus()),
                () -> assertFalse(processingAssessment.fallback()),
                () -> assertTrue(processingAssessment.requiresReview()),
                () -> assertEquals("PARTIAL", malformedAssessment.trustStatus()),
                () -> assertFalse(malformedAssessment.fallback()),
                () -> assertTrue(malformedAssessment.requiresReview()),
                () -> assertEquals("PARTIAL", incompleteAssessment.trustStatus()),
                () -> assertFalse(incompleteAssessment.fallback()),
                () -> assertTrue(incompleteAssessment.requiresReview()),
                () -> assertEquals("PARTIAL", missingAssessment.trustStatus()),
                () -> assertFalse(missingAssessment.fallback()),
                () -> assertTrue(missingAssessment.requiresReview()));
    }

    private ResumeJobMatchReport trustedSuccessReport() {
        ResumeJobMatchReport report = new ResumeJobMatchReport();
        report.setStatus(ResumeJobMatchStatus.SUCCESS.getCode());
        report.setResumeId(11L);
        report.setTargetJobId(12L);
        report.setOverallScore(82);
        report.setAiCallLogId(13L);
        report.setSummary("Grounded match result");
        return report;
    }
}
