package com.codecoachai.ai.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanIntentDTO;
import com.codecoachai.ai.agent.domain.enums.AgentPlanSourceType;
import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PulseView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPlanSourceAdapterTest {

    private final AgentPlanSourceAdapter adapter = new AgentPlanSourceAdapter();

    @Test
    void supportsAllV7SourcesAndPreservesStableItemKey() {
        for (String source : List.of("DAILY_REVIEW", "WEEKLY_REPORT", "INTERVIEW_PREPARATION")) {
            AgentExternalPlanChangePreviewDTO request = new AgentExternalPlanChangePreviewDTO();
            request.setSourceType(source);
            request.setSourceId(7L);
            request.setSourceVersion(2);
            request.setSourceContextHash("hash");
            request.setTargetJobId(42L);
            AgentExternalPlanIntentDTO intent = new AgentExternalPlanIntentDTO();
            intent.setSourceItemKey("action-1");
            intent.setTitle("Practice");
            intent.setPlanDate(LocalDate.of(2026, 7, 21));
            intent.setRelatedSkillCode("JAVA");
            request.setIntents(List.of(intent));
            assertEquals(AgentPlanSourceType.valueOf(source).name(),
                    adapter.toSuggestions(9L, request).get(0).getSourceType());
            assertEquals("action-1", adapter.toSuggestions(9L, request).get(0).getSourceItemKey());
            assertTrue(adapter.contextHash(request).length() == 64);
        }
    }

    @Test
    void campaignPulseUsesStableCampaignSourceAndMovesOverdueActionToTargetDate() {
        PulseView pulse = new PulseView();
        pulse.setCampaignId(12L);
        pulse.setSnapshotId(55L);
        pulse.setSnapshotVersion(3);
        pulse.setInputHash("pulse-input-hash");
        ActionItem action = new ActionItem();
        action.setSemanticKey("FOLLOW_UP_OVERDUE:12:31:31");
        action.setTitle("处理逾期跟进");
        action.setDueAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        action.setEstimatedMinutes(30);
        action.setPriority("HIGH");
        action.setConfidenceLevel("MEDIUM");
        pulse.setActionSeeds(List.of(action));
        LocalDate targetDate = LocalDate.of(2026, 7, 22);

        AgentExternalPlanChangePreviewDTO request = adapter.fromCampaignPulse(
                pulse, List.of(action.getSemanticKey()), "pulse-plan-0001", 120, targetDate);

        assertEquals(12L, request.getSourceId());
        assertEquals(3, request.getSourceVersion());
        assertEquals("pulse-input-hash", request.getSourceContextHash());
        assertEquals(targetDate, request.getIntents().get(0).getPlanDate());
    }
}
