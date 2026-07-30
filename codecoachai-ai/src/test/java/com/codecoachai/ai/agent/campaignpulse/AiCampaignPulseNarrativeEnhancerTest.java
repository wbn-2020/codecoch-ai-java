package com.codecoachai.ai.agent.campaignpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.agent.campaigncockpit.CampaignCockpitModels.ActionItem;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.Computation;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.PromptRenderResult;
import com.codecoachai.ai.service.PromptRenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiCampaignPulseNarrativeEnhancerTest {

    @Test
    void validChineseWhitelistedNarrativeUsesAiResult() {
        PromptRenderService promptService = mock(PromptRenderService.class);
        AiCallLogService logService = mock(AiCallLogService.class);
        when(promptService.render(anyString(), anyString(), anyMap()))
                .thenReturn(PromptRenderResult.builder()
                        .renderedPrompt("prompt")
                        .promptHash("hash")
                        .build());
        RouteResult route = new RouteResult();
        route.setAiCallLogId(88L);
        route.setContent("""
                {
                  "summary":"当前周期有一项明确跟进行动。",
                  "facts":["AI 编造事实。"],
                  "changes":[],
                  "driftReasons":["跟进已超过明确时间。"],
                  "focusAreas":["先处理逾期跟进。"],
                  "actionSelections":["FOLLOW_UP_OVERDUE:1:2:2"],
                  "limits":["只基于当前周期事实。"],
                  "confidenceLevel":"MEDIUM"
                }
                """);
        when(logService.callAndLog(any())).thenReturn(route);
        var enhancer = new AiCampaignPulseNarrativeEnhancer(
                promptService, logService, new ObjectMapper(),
                new RuleOnlyCampaignPulseNarrativeEnhancer());

        var result = enhancer.enhance(7L, 1L, computation());

        assertFalse(Boolean.TRUE.equals(result.getFallback()));
        assertEquals(88L, result.getAiCallLogId());
        assertEquals(List.of("FOLLOW_UP_OVERDUE:1:2:2"), result.getActionSelections());
        assertTrue(result.getFacts().stream()
                .noneMatch(item -> item.contains("AI 编造")));
        assertTrue(result.getLimits().contains("低样本仅视为弱信号。"));
    }

    @Test
    void aiFailureFallsBackAndCapsConfidenceToLow() {
        PromptRenderService promptService = mock(PromptRenderService.class);
        AiCallLogService logService = mock(AiCallLogService.class);
        when(promptService.render(anyString(), anyString(), anyMap()))
                .thenReturn(PromptRenderResult.builder().renderedPrompt("prompt").build());
        when(logService.callAndLog(any())).thenThrow(new IllegalStateException("timeout"));
        var enhancer = new AiCampaignPulseNarrativeEnhancer(
                promptService, logService, new ObjectMapper(),
                new RuleOnlyCampaignPulseNarrativeEnhancer());

        var result = enhancer.enhance(7L, 1L, computation());

        assertTrue(Boolean.TRUE.equals(result.getFallback()));
        assertEquals("LOW", result.getConfidenceLevel());
        assertTrue(result.getLimits().contains("低样本仅视为弱信号。"));
    }

    private Computation computation() {
        Computation result = new Computation();
        result.setFacts(Map.of("applicationCount", 1));
        result.setMetrics(Map.of("openActionCount", 1, "weeklyBudgetMinutes", 60));
        result.setChanges(List.of());
        result.setDriftSignals(List.of("跟进已超过明确时间。"));
        result.setLimits(List.of("低样本仅视为弱信号。"));
        result.setConfidenceLevel("MEDIUM");
        ActionItem action = new ActionItem();
        action.setSemanticKey("FOLLOW_UP_OVERDUE:1:2:2");
        action.setTitle("处理逾期跟进");
        action.setPriority("HIGH");
        action.setEstimatedMinutes(30);
        action.setConfidenceLevel("MEDIUM");
        result.setActionSeeds(List.of(action));
        return result;
    }
}
