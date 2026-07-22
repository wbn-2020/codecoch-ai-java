package com.codecoachai.ai.agent.campaigncockpit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.Application;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.OperatingProfile;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampaignCockpitRuleEngineTest {

    private final CampaignCockpitRuleEngine ruleEngine = new CampaignCockpitRuleEngine();

    @Test
    void lowSampleActionsAreDeterministicAndCoverReviewAndCapacity() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        EvidenceEnvelope evidence = new EvidenceEnvelope();
        evidence.setCampaignId(12L);
        evidence.setDataCutoffAt(now);
        OperatingProfile profile = new OperatingProfile();
        profile.setWeeklyTimeBudgetMinutes(30);
        profile.setStaleAfterDays(7);
        evidence.setOperatingProfile(profile);

        Application first = new Application();
        first.setId(101L);
        first.setStatus("INTERVIEWING");
        first.setNextFollowUpAt(now.minusHours(1));
        first.setInterviewAt(now.minusDays(1));
        first.setInterviewReviewReady(false);
        first.setMaterialCoveragePercent(30);
        first.setContactFollowUpAt(now.plusHours(1));
        first.setUpdatedAt(now.minusDays(10));

        Application second = new Application();
        second.setId(102L);
        second.setStatus("OFFER");
        second.setOfferDeadlineAt(now.plusHours(12));
        second.setResearchCoveragePercent(20);
        second.setUpdatedAt(now);
        evidence.setApplications(List.of(second, first));

        var firstResult = ruleEngine.aggregate(evidence, now);
        var secondResult = ruleEngine.aggregate(evidence, now);

        assertEquals("LOW", firstResult.getConfidenceLevel());
        assertTrue(firstResult.getActionQueue().stream()
                .anyMatch(item -> "INTERVIEW_REVIEW_MISSING".equals(item.getActionType())));
        assertTrue(firstResult.getActionQueue().stream()
                .anyMatch(item -> "PLAN_CAPACITY_OVERLOAD".equals(item.getActionType())));
        assertTrue(firstResult.getActionQueue().stream()
                .anyMatch(item -> "OFFER_DEADLINE".equals(item.getActionType())
                        && "HIGH".equals(item.getPriority())));
        assertFalse(firstResult.getActionQueue().stream()
                .map(item -> item.getSourceHash())
                .anyMatch(value -> value == null || value.isBlank()));
        assertEquals(
                firstResult.getActionQueue().stream().map(item -> item.getSemanticKey()).toList(),
                secondResult.getActionQueue().stream().map(item -> item.getSemanticKey()).toList());
        assertEquals(2, firstResult.getCapacitySummary().getActiveOpportunityCount());
        assertEquals("/applications/101", firstResult.getApplications().get(0).getActionUrl());
    }

    @Test
    void resumeEvidenceFieldsControlActionsAndKeepSourceHashStable() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        EvidenceEnvelope firstEvidence = evidenceWithResumeFields(now);
        EvidenceEnvelope refreshedEvidence = evidenceWithResumeFields(now.plusHours(1));

        var firstResult = ruleEngine.aggregate(firstEvidence, now);
        var refreshedResult = ruleEngine.aggregate(refreshedEvidence, now.plusHours(1));

        assertFalse(firstResult.getActionQueue().stream()
                .anyMatch(item -> "INTERVIEW_PREP_MISSING".equals(item.getActionType())));
        assertTrue(firstResult.getActionQueue().stream()
                .anyMatch(item -> "MATERIAL_COVERAGE_LOW".equals(item.getActionType())));
        assertTrue(firstResult.getActionQueue().stream()
                .allMatch(item -> "resume-source-hash".equals(item.getSourceHash())));
        assertEquals(
                firstResult.getActionQueue().stream().map(item -> item.getSourceHash()).toList(),
                refreshedResult.getActionQueue().stream().map(item -> item.getSourceHash()).toList());
    }

    @Test
    void terminalApplicationsRemainVisibleButDoNotGenerateActionsOrRaiseConfidence() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        EvidenceEnvelope evidence = new EvidenceEnvelope();
        evidence.setCampaignId(12L);
        evidence.setDataCutoffAt(now);
        evidence.setOperatingProfile(new OperatingProfile());
        evidence.setApplications(List.of(
                terminalApplication(101L, "REJECTED", now),
                terminalApplication(102L, "WITHDRAWN", now),
                terminalApplication(103L, "ACCEPTED", now)));

        var result = ruleEngine.aggregate(evidence, now);

        assertEquals(3, result.getApplications().size());
        assertEquals(0, result.getCapacitySummary().getActiveOpportunityCount());
        assertTrue(result.getActionQueue().isEmpty());
        assertEquals("LOW", result.getConfidenceLevel());
    }

    private EvidenceEnvelope evidenceWithResumeFields(LocalDateTime cutoff) {
        EvidenceEnvelope evidence = new EvidenceEnvelope();
        evidence.setCampaignId(12L);
        evidence.setDataCutoffAt(cutoff);
        OperatingProfile profile = new OperatingProfile();
        profile.setWeeklyTimeBudgetMinutes(300);
        profile.setStaleAfterDays(7);
        evidence.setOperatingProfile(profile);

        Application application = new Application();
        application.setId(101L);
        application.setStatus("INTERVIEWING");
        application.setInterviewAt(LocalDateTime.of(2026, 7, 24, 10, 0));
        application.setInterviewPrepMissing(false);
        application.setMaterialCoverageLow(true);
        application.setResearchCoverageLow(false);
        application.setSourceHash("resume-source-hash");
        application.setUpdatedAt(LocalDateTime.of(2026, 7, 22, 9, 0));
        evidence.setApplications(List.of(application));
        return evidence;
    }

    private Application terminalApplication(Long id, String status, LocalDateTime now) {
        Application application = new Application();
        application.setId(id);
        application.setStatus(status);
        application.setNextFollowUpAt(now.minusDays(1));
        application.setUpdatedAt(now.minusDays(30));
        return application;
    }
}
