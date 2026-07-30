package com.codecoachai.ai.agent.campaigncockpit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.codecoachai.ai.agent.campaigncockpit.domain.entity.CampaignActionDecision;
import com.codecoachai.ai.agent.campaigncockpit.mapper.CampaignActionDecisionMapper;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.common.core.domain.Result;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampaignCockpitServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CAMPAIGN_ID = 12L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 12, 0);

    @Test
    void hidesSnoozedActionUntilItsDeadline() {
        CampaignActionDecision decision = decision(NOW.plusHours(1));

        CockpitView result = service(decision).get(USER_ID, CAMPAIGN_ID);

        assertTrue(result.getActionQueue().isEmpty());
    }

    @Test
    void reopensSnoozedActionAfterItsDeadline() {
        CampaignActionDecision decision = decision(NOW.minusMinutes(1));

        CockpitView result = service(decision).get(USER_ID, CAMPAIGN_ID);

        assertEquals(1, result.getActionQueue().size());
        assertEquals("OPEN", result.getActionQueue().get(0).getDecisionStatus());
    }

    private CampaignCockpitServiceImpl service(CampaignActionDecision decision) {
        CampaignCockpitEvidenceClient evidenceClient = mock(CampaignCockpitEvidenceClient.class);
        CampaignCockpitRuleEngine ruleEngine = mock(CampaignCockpitRuleEngine.class);
        CampaignActionDecisionMapper decisionMapper = mock(CampaignActionDecisionMapper.class);
        AgentBusinessTimeProvider timeProvider = mock(AgentBusinessTimeProvider.class);

        EvidenceEnvelope evidence = new EvidenceEnvelope();
        evidence.setUserId(USER_ID);
        evidence.setCampaignId(CAMPAIGN_ID);
        CockpitView cockpit = new CockpitView();
        cockpit.setActionQueue(List.of(action()));

        when(timeProvider.now()).thenReturn(NOW);
        when(evidenceClient.get(eq(USER_ID), eq(CAMPAIGN_ID), eq(NOW), anyInt(), anyInt()))
                .thenReturn(Result.success(evidence));
        when(ruleEngine.aggregate(evidence, NOW)).thenReturn(cockpit);
        when(decisionMapper.selectBySemanticSource(
                USER_ID, CAMPAIGN_ID, "FOLLOW_UP_OVERDUE:12:31:31", "source-hash"))
                .thenReturn(decision);

        return new CampaignCockpitServiceImpl(
                evidenceClient, ruleEngine, decisionMapper, timeProvider);
    }

    private ActionItem action() {
        ActionItem action = new ActionItem();
        action.setSemanticKey("FOLLOW_UP_OVERDUE:12:31:31");
        action.setSourceHash("source-hash");
        action.setActionType("FOLLOW_UP_OVERDUE");
        return action;
    }

    private CampaignActionDecision decision(LocalDateTime snoozedUntil) {
        CampaignActionDecision decision = new CampaignActionDecision();
        decision.setDecisionStatus("SNOOZED");
        decision.setSnoozedUntil(snoozedUntil);
        return decision;
    }
}
