package com.codecoachai.ai.agent.evidencelearning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.feign.ResumeEvidenceUsageFactsVO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceLearningRuleEngineTest {

    private final EvidenceLearningRuleEngine engine = new EvidenceLearningRuleEngine();

    @Test
    void fifteenComparableApplicationsWithTwoInterviewsUseMediumTrendBoundary() {
        ResumeEvidenceUsageFactsVO facts = facts(15);
        facts.getConfirmedResults().add(result(1L, 1L, "INTERVIEW_COMPLETED"));
        facts.getConfirmedResults().add(result(2L, 2L, "INTERVIEW_COMPLETED"));

        EvidenceLearningRuleEngine.Quality quality = engine.quality(facts);

        assertEquals("MEDIUM", quality.confidenceLevel());
        assertTrue(quality.limits().stream().anyMatch(value -> value.contains("过程趋势")));
    }

    @Test
    void staleAndSupersededFactsDoNotCountTowardQualityGate() {
        ResumeEvidenceUsageFactsVO facts = facts(5);
        facts.getUsageSnapshots().get(0).setStale(true);
        facts.getUsageSnapshots().get(1).setStatus("SUPERSEDED");

        assertEquals(3, engine.comparableApplicationCount(facts));
        assertEquals("LOW", engine.quality(facts).confidenceLevel());
    }

    @Test
    void sourceRefsKeepServerTypeIdVersionFormat() {
        ResumeEvidenceUsageFactsVO facts = facts(1);
        facts.getUsageSnapshots().get(0).setSourceRefs(
                List.of("PROJECT_EVIDENCE:123:2", "PACKAGE_SNAPSHOT:88:4"));

        var refs = engine.sourceRefs(facts);

        assertTrue(refs.stream().anyMatch(ref -> "PROJECT_EVIDENCE:123:2".equals(ref.getSourceId())));
        assertTrue(refs.stream().anyMatch(ref -> "PACKAGE_SNAPSHOT:88:4".equals(ref.getSourceId())));
    }

    @Test
    void fewerThanFiveComparableApplicationsReturnFactOnlyFallbacks() {
        ResumeEvidenceUsageFactsVO facts = facts(4);

        var candidate = engine.candidateFallback(facts, "fallback");
        var reuse = engine.reuseFallback(facts, "fallback");
        var quality = engine.quality(facts);

        assertEquals(4, quality.usageCount());
        assertEquals(0, quality.sampleCount());
        assertTrue(candidate.getCandidateDecision().isEmpty());
        assertTrue(candidate.getWeakObservations().isEmpty());
        assertNull(reuse.getReuseDraft());
        assertTrue(reuse.getWeakObservations().isEmpty());
    }

    @Test
    void qualityOwnsComparableUsageAndConfirmedSampleCounts() {
        ResumeEvidenceUsageFactsVO facts = facts(5);
        facts.getConfirmedResults().add(result(1L, 1L, "INTERVIEW_COMPLETED"));
        facts.getConfirmedResults().add(result(2L, 2L, "REJECTED"));

        var quality = engine.quality(facts);
        var candidate = engine.candidateFallback(facts, "fallback");

        assertEquals(5, quality.usageCount());
        assertEquals(2, quality.sampleCount());
        assertTrue(quality.candidateAllowed());
        assertEquals(5, candidate.getCandidateDecision().get(0).getUsageCount());
        assertEquals(2, candidate.getCandidateDecision().get(0).getSampleCount());
    }

    private ResumeEvidenceUsageFactsVO facts(int count) {
        ResumeEvidenceUsageFactsVO facts = new ResumeEvidenceUsageFactsVO();
        facts.setUserId(10L);
        facts.setSourceSetHash("server-source-set-hash");
        List<ResumeEvidenceUsageFactsVO.UsageFact> usages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ResumeEvidenceUsageFactsVO.UsageFact usage = new ResumeEvidenceUsageFactsVO.UsageFact();
            usage.setUsageId((long) i);
            usage.setApplicationId((long) i);
            usage.setAssetVersion("v" + ((i % 2) + 1));
            usage.setStatus("CAPTURED");
            usage.setSourceHash("usage-hash-" + i);
            usage.setSourceRefs(List.of("PROJECT_EVIDENCE:" + i + ":1"));
            usages.add(usage);
        }
        facts.setUsageSnapshots(usages);
        return facts;
    }

    private ResumeEvidenceUsageFactsVO.ResultFact result(
            Long id, Long usageId, String eventType) {
        ResumeEvidenceUsageFactsVO.ResultFact result = new ResumeEvidenceUsageFactsVO.ResultFact();
        result.setResultId(id);
        result.setUsageId(usageId);
        result.setEventType(eventType);
        return result;
    }
}
