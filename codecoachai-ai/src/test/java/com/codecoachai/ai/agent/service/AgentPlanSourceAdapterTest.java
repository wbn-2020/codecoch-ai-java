package com.codecoachai.ai.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanIntentDTO;
import com.codecoachai.ai.agent.domain.enums.AgentPlanSourceType;
import java.time.LocalDate;
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
}
