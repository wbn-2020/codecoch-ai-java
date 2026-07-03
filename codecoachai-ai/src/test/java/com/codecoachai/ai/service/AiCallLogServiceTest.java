package com.codecoachai.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.router.AiModelRouter;
import com.codecoachai.ai.router.AiModelRouter.AiCallContext;
import com.codecoachai.ai.router.AiModelRouter.RouteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCallLogServiceTest {

    @Mock
    private AiModelRouter aiModelRouter;
    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Captor
    private ArgumentCaptor<AiCallLog> logCaptor;

    @Test
    void callAndLogStoresOnlySafeMetadataWithoutRawPromptInputOrResponse() {
        RouteResult routeResult = new RouteResult();
        routeResult.setModel("deepseek-chat");
        routeResult.setContent("raw response with phone 13800000000");
        routeResult.setPromptTokens(12);
        routeResult.setCompletionTokens(8);
        routeResult.setTotalTokens(20);
        routeResult.setRouteTrace("deepseek");
        routeResult.setEstimatedCost(0.01D);
        when(aiModelRouter.chat(any())).thenReturn(routeResult);
        when(aiCallLogMapper.insert(logCaptor.capture())).thenReturn(1);

        AiCallContext context = new AiCallContext();
        context.setScene("interview.report");
        context.setUserId(10L);
        context.setBusinessId("report-1");
        context.setRequestId("req-1");
        context.setPromptTemplateId(2L);
        context.setPromptTemplateVersionId(3L);
        context.setPromptVersion("v1");
        context.setInputVariablesJson("{\"resume\":\"full resume text\",\"phone\":\"13800000000\"}");
        context.setModelParamsJson("{\"temperature\":0.2}");
        context.setPromptHash("sha256-prompt");
        context.setResponseFormat("JSON");
        context.setPrompt("full prompt containing resume and JD");
        context.setRequestBody("{\"prompt\":\"full prompt containing resume and JD\"}");

        RouteResult result = new AiCallLogService(aiModelRouter, aiCallLogMapper).callAndLog(context);

        AiCallLog saved = logCaptor.getValue();
        assertEquals(routeResult, result);
        assertEquals("sha256-prompt", saved.getPromptHash());
        assertEquals("deepseek-chat", saved.getModel());
        assertEquals(20, saved.getTotalTokens());
        assertEquals("deepseek", saved.getRouteTrace());
        assertNull(saved.getInputVariablesJson());
        assertNull(saved.getRequestPrompt());
        assertNull(saved.getRequestBody());
        assertNull(saved.getResponseContent());
        assertNull(saved.getResponseBody());
    }
}
