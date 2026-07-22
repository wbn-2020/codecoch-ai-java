package com.codecoachai.ai.agent.campaignpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.Application;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.Campaign;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.OperatingProfile;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampaignPulseRuleEngineTest {

    private final CampaignPulseRuleEngine ruleEngine = new CampaignPulseRuleEngine();

    @Test
    void firstLowSampleSnapshotKeepsLimitsAndExplicitDriftOnly() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 22, 12, 0);
        CockpitView cockpit = new CockpitView();
        Campaign campaign = new Campaign();
        campaign.setId(9L);
        campaign.setStatus("ACTIVE");
        cockpit.setCampaign(campaign);
        cockpit.setDataCutoffAt(cutoff);
        OperatingProfile profile = new OperatingProfile();
        profile.setMaxActiveOpportunities(1);
        profile.setWeeklyTimeBudgetMinutes(30);
        cockpit.setOperatingProfile(profile);
        Application first = new Application();
        first.setId(1L);
        Application second = new Application();
        second.setId(2L);
        cockpit.setApplications(List.of(first, second));
        cockpit.setConfidenceLevel("LOW");
        cockpit.getCapacitySummary().setActiveOpportunityCount(2);
        cockpit.getCapacitySummary().setWeeklyBudgetMinutes(30);
        cockpit.getCapacitySummary().setOpenActionMinutes(60);
        cockpit.getCapacitySummary().setOverloaded(true);
        ActionItem action = new ActionItem();
        action.setSemanticKey("FOLLOW_UP_OVERDUE:9:1:1");
        action.setSourceHash("source");
        action.setPriority("HIGH");
        action.setEstimatedMinutes(60);
        action.setDueAt(cutoff.minusHours(1));
        cockpit.setActionQueue(List.of(action));

        var result = ruleEngine.compute(cockpit, null);

        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getChanges().stream()
                .anyMatch(value -> value.contains("首个脉搏快照")));
        assertTrue(result.getDriftSignals().stream()
                .anyMatch(value -> value.contains("同时推进上限")));
        assertTrue(result.getDriftSignals().stream()
                .anyMatch(value -> value.contains("每周时间预算")));
        assertTrue(result.getLimits().stream()
                .anyMatch(value -> value.contains("低样本弱信号")));
        assertTrue(result.getLimits().stream()
                .anyMatch(value -> value.contains("缺少上一快照")));
    }
}
