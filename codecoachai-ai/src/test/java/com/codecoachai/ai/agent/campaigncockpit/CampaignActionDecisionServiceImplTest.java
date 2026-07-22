package com.codecoachai.ai.agent.campaigncockpit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaigncockpit.CampaignActionDecisionModels.Request;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.CockpitView;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.EvidenceEnvelope;
import com.codecoachai.ai.agent.campaigncockpit.domain.entity.CampaignActionDecision;
import com.codecoachai.ai.agent.campaigncockpit.mapper.CampaignActionDecisionMapper;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampaignActionDecisionServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CAMPAIGN_ID = 12L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 12, 0);
    private static final String SEMANTIC_KEY = "FOLLOW_UP_OVERDUE:12:31:31";
    private static final String SOURCE_HASH = "source-hash";
    private static final String IDEMPOTENCY_KEY = "decision-0001";

    @Test
    void rejectsEvidenceOwnedByAnotherUser() {
        Fixture fixture = fixture();
        fixture.evidence().setUserId(99L);

        assertThrows(BusinessException.class,
                () -> fixture.service().decide(USER_ID, CAMPAIGN_ID, request()));

        verify(fixture.mapper(), never()).insert(any());
    }

    @Test
    void replaysSameIdempotencyKeyAndPayload() {
        Fixture fixture = fixture();
        CampaignActionDecision replay = new CampaignActionDecision();
        replay.setId(88L);
        replay.setCampaignId(CAMPAIGN_ID);
        replay.setSemanticKey(SEMANTIC_KEY);
        replay.setSourceHash(SOURCE_HASH);
        replay.setActionType("FOLLOW_UP_OVERDUE");
        replay.setDecisionStatus("DISMISSED");
        replay.setIdempotencyKeyHash(AgentAdaptivePlanHashUtils.sha256(IDEMPOTENCY_KEY));
        replay.setPayloadHash(payloadHash("DISMISSED"));
        replay.setDecidedAt(NOW);
        when(fixture.mapper().selectByIdempotency(
                USER_ID, AgentAdaptivePlanHashUtils.sha256(IDEMPOTENCY_KEY)))
                .thenReturn(replay);

        var result = fixture.service().decide(USER_ID, CAMPAIGN_ID, request());

        assertEquals(88L, result.getId());
        assertEquals("DISMISSED", result.getDecisionStatus());
        verify(fixture.mapper(), never()).deactivateCurrent(
                any(), any(), any(), any());
        verify(fixture.mapper(), never()).insert(any());
        verify(fixture.evidenceClient(), never()).get(
                any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDecisionWhenSourceHashHasChanged() {
        Fixture fixture = fixture();
        Request request = request();
        request.setSourceHash("stale-source-hash");

        assertThrows(BusinessException.class,
                () -> fixture.service().decide(USER_ID, CAMPAIGN_ID, request));

        verify(fixture.mapper(), never()).insert(any());
    }

    private Fixture fixture() {
        CampaignCockpitEvidenceClient evidenceClient = mock(CampaignCockpitEvidenceClient.class);
        CampaignCockpitRuleEngine ruleEngine = mock(CampaignCockpitRuleEngine.class);
        CampaignActionDecisionMapper mapper = mock(CampaignActionDecisionMapper.class);
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

        return new Fixture(
                new CampaignActionDecisionServiceImpl(
                        evidenceClient, ruleEngine, mapper, timeProvider),
                mapper,
                evidence,
                evidenceClient);
    }

    private Request request() {
        Request request = new Request();
        request.setSemanticKey(SEMANTIC_KEY);
        request.setSourceHash(SOURCE_HASH);
        request.setDecisionStatus("DISMISSED");
        request.setIdempotencyKey(IDEMPOTENCY_KEY);
        return request;
    }

    private ActionItem action() {
        ActionItem action = new ActionItem();
        action.setSemanticKey(SEMANTIC_KEY);
        action.setSourceHash(SOURCE_HASH);
        action.setActionType("FOLLOW_UP_OVERDUE");
        return action;
    }

    private String payloadHash(String status) {
        return AgentAdaptivePlanHashUtils.sha256(
                SEMANTIC_KEY + "|" + SOURCE_HASH + "|" + status + "||");
    }

    private record Fixture(
            CampaignActionDecisionServiceImpl service,
            CampaignActionDecisionMapper mapper,
            EvidenceEnvelope evidence,
            CampaignCockpitEvidenceClient evidenceClient) {
    }
}
